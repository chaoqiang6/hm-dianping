package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.RedisClient;
import org.redisson.RedissonLock;
import org.redisson.RedissonLockEntry;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisResponseTimeoutException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisStrictCommand;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service

public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Autowired
    private VoucherOrderServiceImpl voucherOrderService;
    @Autowired
    private RedisIdWorker idWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient1;
    @Resource
    private RedissonClient redissonClient2;

    private static DefaultRedisScript<Long> secKillScript;

    private BlockingQueue<VoucherOrder> voucherOrders = new LinkedBlockingQueue<>();

    private ExecutorService es = Executors.newSingleThreadExecutor(r -> new Thread(r,"创建订单线程"));
    @PostConstruct
    private void openGenOrderTask (){
        es.submit(() ->{
            try {
                while (true){
                    final VoucherOrder voucherOrder = voucherOrders.take();
                    createOrder(voucherOrder);
                }
            } catch (InterruptedException e) {
                log.error("阻塞队列获取元素被打断",e);
            }
        });
    }

    static {
        secKillScript = new DefaultRedisScript<>();
        secKillScript.setLocation(new ClassPathResource("seckill.lua"));
        secKillScript.setResultType(Long.class);
    }


    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        final SeckillVoucher seckillVoucher = getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("数据库中不存在该优惠券");
        }
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime()) || LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("当前不在该优惠券的秒杀时间");
        }
        //判断库存数量
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("该优惠券已售罄");
        }

        //保证一人一单
        final Long userId = UserHolder.getUser().getId();
//        final RLock rLock = redissonClient.getLock("order:" + userId);
//        final RLock rLock1 = redissonClient1.getLock("order:" + userId);
//        final RLock rLock2 = redissonClient2.getLock("order:" + userId);
//
//        final RLock mLock = redissonClient.getMultiLock(rLock, rLock1, rLock2);
        final RLock rLock = redissonClient.getLock("order:" + userId);
//        final boolean lockSuc = mLock.tryLock(3, TimeUnit.SECONDS);
//        final SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
//        final RLock mLock = redissonClient.getLock("order:" + userId);
        final boolean lockSuc = rLock.tryLock(10L,TimeUnit.SECONDS);
        if (!lockSuc) {
            return Result.fail("怀疑你开挂");
        }
        try {
            ISeckillVoucherService proxy = SpringUtil.getBean(ISeckillVoucherService.class);
            return proxy.createOrder(voucherId);
        }finally {
//            lock.unLock("order:"+userId);
            rLock.unlock();
//            mLock.unlock();
        }

    }

    @Override
    public Result seckillVoucherInCache(Long voucherId) throws InterruptedException {
        final Long userId = UserHolder.getUser().getId();
        if(userId == null){
            return Result.fail("非法token");
        }
        final Integer result = redisTemplate.execute(secKillScript, Collections.EMPTY_LIST, voucherId.toString(), userId.toString()).intValue();
        if(result == -1){
            return Result.fail("该优惠券已售罄");
        }
        if(result == 2){
            return Result.fail("不允许重复下单");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(idWorker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrders.add(voucherOrder);


        return Result.ok(voucherOrder.getId());
    }

    /**
     * 下单逻辑，必须保证一人一单
     * 在方法上加锁锁的是this对象，粒度太大，所以针对用户id进行加锁，Long.toString会返回一个新对象，所以使用intern()返回常量池地址，保证了同一用户id返回的是同一个地址
     *
     * @param voucherId
     * @return
     */

    @Transactional
    public Result createOrder(Long voucherId) {
        //保证一人一单
        final Long userId = UserHolder.getUser().getId();
        Integer count = voucherOrderService.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("一人只能买一个订单");
        }
        final boolean updated = update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!updated) {
            return Result.fail("该优惠券已售罄");
        }
        final VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        voucherOrderService.save(order);
        return Result.ok(order.getId());
    }

    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        //保证一人一单
        final Long userId = voucherOrder.getUserId();
        Integer count = voucherOrderService.query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("数据库中已存在该订单");
            return ;
        }
        final boolean updated = update().setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!updated) {
            log.error("该优惠券已售罄");
            return ;
        }
//        voucherOrder.setId(idWorker.nextId("order"));
        voucherOrderService.save(voucherOrder);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Result createOrderWithRedisLock(Long voucherId) {

        //保证一人一单
        final Long userId = UserHolder.getUser().getId();
        final SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
        final boolean lockSuc = lock.tryLock(Duration.ofSeconds(2));
        if (!lockSuc) {
            return Result.fail("一人只能买一个订单");
        }
        Integer count = voucherOrderService.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("一人只能买一个订单");
        }
        System.out.println(System.currentTimeMillis() + "时间" + count);
        final boolean updated = update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!updated) {
            return Result.fail("该优惠券已售罄");
        }
        final VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        voucherOrderService.save(order);
        lock.unLock("order:" + userId);
        return Result.ok(order.getId());


    }

}

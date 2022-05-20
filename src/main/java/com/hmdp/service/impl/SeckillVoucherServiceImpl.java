package com.hmdp.service.impl;

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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

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


    @Override
    public Result seckillVoucher(Long voucherId) {
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
        final SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
        final boolean lockSuc = lock.tryLock(Duration.ofSeconds(2));
        if (!lockSuc) {
            return Result.fail("怀疑你开挂");
        }
        try {
            ISeckillVoucherService proxy = SpringUtil.getBean(ISeckillVoucherService.class);
            return proxy.createOrder(voucherId);
        }finally {
            lock.unLock("order:"+userId);
        }

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
        return Result.ok(order.getId());

    }
}

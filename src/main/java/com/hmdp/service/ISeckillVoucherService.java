package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {

    /**
     * 秒杀
     * @param voucherId 优惠券id
     * @return 执行结果
     */
    Result seckillVoucher(Long voucherId) throws InterruptedException;

    /**
     * 秒杀，通过缓存操作，如果缓存执行成功，再将用户id和优惠券id放入消息队列，其他线程订阅该队列对数据库进行写入操作
     * @param voucherId
     * @return
     * @throws InterruptedException
     */
    Result seckillVoucherInCache(Long voucherId) throws InterruptedException;

    Result seckillVoucherInStream(Long voucherId) throws InterruptedException;
    Result createOrder(Long voucherId);
}

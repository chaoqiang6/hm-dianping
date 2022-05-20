package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(50);

    @Test
    public void testSaveHotData() throws InterruptedException {
        final Shop byId = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1,byId, Duration.ofSeconds(10));
    }

    @Test
    public void testIdGen() throws InterruptedException {

        final int coreCount = Runtime.getRuntime().availableProcessors();
        CountDownLatch countDownLatch = new CountDownLatch(coreCount);
        //定义线程执行方法对象
        Runnable runnable = ()->{
            for (int i = 0; i < 1000; i++) {
                redisIdWorker.nextId("test0517id");
            }
            countDownLatch.countDown();
        };
        final long begin = System.currentTimeMillis();
        for (int i = 0; i < coreCount; i++) {
            es.submit(runnable);
        }
        countDownLatch.await();
        final long expend = System.currentTimeMillis() - begin;
        System.out.println(expend);
    }






}

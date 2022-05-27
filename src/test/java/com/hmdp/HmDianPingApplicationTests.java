package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private ExecutorService es = Executors.newFixedThreadPool(8);


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
    @Test
    public void importShopLocation(){
        final Map<Long, List<Shop>> groupingByTypeId = shopService.list().stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : groupingByTypeId.entrySet()) {
            stringRedisTemplate.opsForGeo().add(RedisConstants.SHOP_GEO_KEY+entry.getKey(),
                    entry.getValue().stream()
                            .map(shop -> new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())))
                            .collect(Collectors.toList()));
        }

    }






}

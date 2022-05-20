package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    @Transactional
    public void update(Shop shop) {
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }

    @Override
    public Result queryShopById(Long id) {
        final Shop shop = queryWithPassThrough2(id);
        if (shop != null) {
            return Result.ok(shop);
        }
        return Result.fail("未找到该店铺");
    }


    /**
     * 缓存击穿+缓存穿透
     * 互斥锁方式
     *
     * @param id
     * @return
     * @throws InterruptedException
     */
    private Shop queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopCache)) {
            //命中
            return JSONUtil.toBean(shopCache, Shop.class);
        } else if (shopCache != null) {
            return null;
        }
        //未命中
        try {
            final boolean lockSuc = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (!lockSuc) {
                //获取锁失败，休眠一段时间再去查询
                Thread.sleep(Duration.ofSeconds(1).toMillis());
                return queryWithMutex(id);
            }

            final Shop shopInDb = super.getById(id);
            Thread.sleep(Duration.ofMillis(200).toMillis());
            if (Objects.nonNull(shopInDb)) {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopInDb));
                stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, Duration.ofMinutes(RedisConstants.CACHE_SHOP_TTL));
                return shopInDb;
            } else {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", Duration.ofMinutes(RedisConstants.CACHE_NULL_TTL));
            }

        } catch (InterruptedException e) {
            //睡眠被打断
        } finally {
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }

        return null;
    }

    /**
     * 逻辑过期解决热点key问题，存入redis时设置有效期，系统中已提前对热点key进行预热
     * 获取到数据后查看是否过期，如果过期，尝试获取锁开启线程执行redis刷新操作，获取不到锁说明其他已在执行更新操作，返回旧数据
     * @param id
     * @return
     */
    private Shop queryWithLogicalExpire(Long id){
        final String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if(shopJson!= null){
            final RedisData shopData = JSONUtil.toBean(shopJson, RedisData.class);
            if(shopData.getExpireTime().isBefore(LocalDateTime.now())){
                //过期了，获取锁，开线程刷新缓存，释放锁
                final boolean lockSuc = tryLock(RedisConstants.LOCK_SHOP_KEY+id);
                if(lockSuc){
                    //开线程更新缓存释放锁
                    ThreadUtil.newThread(()->{
                        try{
                            saveShopInfo2Cache(id,10L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            unLock(RedisConstants.LOCK_SHOP_KEY+id);
                        }
                    },"更新热点key"+id+"线程").start();
                }
            }
            final JSONObject shop = (JSONObject) shopData.getData();
            return JSONUtil.toBean(shop,Shop.class);
        }
        return null;
    }

    private Shop queryWithLogicalExpire2(Long id){
        return cacheClient.getWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,RedisConstants.LOCK_SHOP_KEY,id,Shop.class, l -> baseMapper.selectById(l),Duration.ofSeconds(RedisConstants.CACHE_SHOP_TTL));
    }

    /**
     * 解决缓存穿透查询数据
     * (数据库中没有该条数据，还一个劲查)
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopCache)) {
            return JSONUtil.toBean(shopCache, Shop.class);
        } else if (shopCache != null) {
            return null;
        }
        //
        final Shop shopInDb = super.getById(id);
        if (Objects.nonNull(shopInDb)) {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopInDb));
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, Duration.ofMinutes(RedisConstants.CACHE_SHOP_TTL));
            return shopInDb;
        } else {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", Duration.ofMinutes(RedisConstants.CACHE_NULL_TTL));
        }
        return null;

    }
    private Shop queryWithPassThrough2(Long id) {
        return cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, (l) -> baseMapper.selectById(l), Duration.ofSeconds(RedisConstants.CACHE_SHOP_TTL));

    }


    /**
     * 尝试获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        final Boolean updating = stringRedisTemplate.opsForValue().setIfAbsent(key, "updating", Duration.ofSeconds(RedisConstants.LOCK_SHOP_TTL));
        return BooleanUtil.isTrue(updating);
    }

    /**
     * 尝试删除锁
     *
     * @param key
     * @return
     */
    private boolean unLock(String key) {
        final Boolean delete = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(delete);
    }

    /**
     * 缓存预热，将数据存入缓存，设置逻辑过期时间
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShopInfo2Cache(Long id, Long expireSeconds) throws InterruptedException {
        log.debug("开始执行缓存重建");
        final Shop shop = getById(id);
        final RedisData hotData = new RedisData();
        hotData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        hotData.setData(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(hotData));
        Thread.sleep(Duration.ofMillis(200).toMillis());
    }
}

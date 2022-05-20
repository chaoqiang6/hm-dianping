package com.hmdp.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(2, r -> new Thread(r,"更新热点数据"));

    //针对缓存穿透
    public void set(String key, Object val, Duration duration){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(val),duration);
    }

    /**
     * 过期时间方式获取数据，针对数据库中的空数据缓存2s
     * 查询缓存，如果缓存中不存在，调用getDataFromDb从数据库中查找并存入缓存，如果数据库中不存在，缓存空数据1s
     * @param prefix 缓存前缀
     * @param id 缓存id
     * @param clazz value类型
     * @param getDataFromDb 数据库获取数据函数
     * @param duration 缓存时常
     * @param <R> 返回值类型
     * @param <ID> id值类型
     * @return
     */
    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> clazz, Function<ID,R> getDataFromDb,Duration duration){
        String key = prefix+id;
        String cache = stringRedisTemplate.opsForValue().get(key);
        if(cache==null){
            //从数据库中查询数据
            final Object dataFromDb = getDataFromDb.apply(id);
            if(dataFromDb == null){
                //避免缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",Duration.ofSeconds(RedisConstants.CACHE_NULL_TTL));
            }else {
                cache = JSONUtil.toJsonStr(dataFromDb);
                stringRedisTemplate.opsForValue().set(key,cache,duration);
            }

        }
        if(cache!=null && !"".equals(cache)){
            return JSONUtil.toBean(cache,clazz);
        }
        return null;
    }


    //针对缓存击穿,热点key
    public void setWithLogicalExpire(String key,Object value,Duration duration){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plus(duration));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 逻辑过期时间获取缓存数据，如果到了过期时间，开启线程刷新缓存数据，返回旧数据
     * @param prefix
     * @param id
     * @param clazz
     * @param getDataFromDb
     * @param duration
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R getWithLogicalExpire(String prefix,String lockPrefix, ID id, Class<R> clazz, Function<ID,R> getDataFromDb,Duration duration){
        String key = prefix+id;
        final String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(redisDataJson)){
            //查询数据库
            return null;
        }
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        if(redisData.getExpireTime().isBefore(LocalDateTime.now())){
            //获取锁，开启线程，更新，释放锁
            final boolean lockSuc = tryLock(lockPrefix+id);
            if(lockSuc){
                //开线程更新缓存释放锁
                threadPoolExecutor.execute(()->{
                    try{
                        final R dataFromDb = getDataFromDb.apply(id);
                        if(dataFromDb == null){
                            stringRedisTemplate.delete(key);
                        }else {
                            RedisData redisDataInCache = new RedisData();
                            redisDataInCache.setData(dataFromDb);
                            redisDataInCache.setExpireTime(LocalDateTime.now().plus(duration));
                            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisDataInCache));
                        }
                    }finally {
                        unLock(lockPrefix+id);
                    }
                });
            }
        }
        return JSONUtil.toBean((JSONObject) redisData.getData(),clazz);

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

}


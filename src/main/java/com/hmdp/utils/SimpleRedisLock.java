package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

public class SimpleRedisLock implements ILock{
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString();

    private String key;
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    public SimpleRedisLock(String key, StringRedisTemplate redisTemplate) {
        this.key = key;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(Duration timeout) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        final Boolean suc = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + key, threadId, timeout);

        return BooleanUtil.isTrue(suc);
    }

    @Override
    public void unLock(String key) {

        String threadId = ID_PREFIX+Thread.currentThread().getId();
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(LOCK_PREFIX + key),threadId);
//        final String cacheThreadId = redisTemplate.opsForValue().get(LOCK_PREFIX + key);
//        if(threadId.equals(cacheThreadId)){
//            redisTemplate.delete(LOCK_PREFIX + key);
//        }

    }

//    @Override
//    public void unLock(String key) {
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        final String cacheThreadId = redisTemplate.opsForValue().get(LOCK_PREFIX + key);
//        if(threadId.equals(cacheThreadId)){
//            redisTemplate.delete(LOCK_PREFIX + key);
//        }
//
//    }
}

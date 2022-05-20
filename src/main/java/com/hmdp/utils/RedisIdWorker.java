package com.hmdp.utils;

import cn.hutool.Hutool;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.swing.text.DateFormatter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 2022年5月17日0时秒数
     */
    private static final long BEGIN_TIMESTAMP = LocalDateTime.of(2022,5,17,0,0,0).toEpochSecond(ZoneOffset.UTC);
    public long nextId(String keyPrefix){
        final long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        //时间戳
        long timeStamp = nowSecond-BEGIN_TIMESTAMP;
        //流水
        final Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd")));
        return timeStamp << 32 | increment;
    }
}

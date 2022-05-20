package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime;
    //组合优于继承
    private Object data;
}

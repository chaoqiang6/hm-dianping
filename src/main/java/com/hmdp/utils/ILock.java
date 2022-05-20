package com.hmdp.utils;

import java.time.Duration;

public interface ILock {
    boolean tryLock(Duration timeout);
    void unLock(String key);
}

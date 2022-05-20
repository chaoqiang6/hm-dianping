--锁的key
local key = KEYS[1]
-- 当前线程标识
local threadId = ARGV[1]
local threadIdInCache = redis.call('get',key)
if(threadIdInCache == threadId) then
    return redis.call('del',key)
end
return 0
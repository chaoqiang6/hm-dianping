-- 秒杀时，直接从redis中读取优惠券数量，如果数量<0,返回-1,再判断当前用户是否已存在，如果当前用户已有秒杀记录，返回0，否则，返回1
local voucherStockKey = "seckill:stock:"..ARGV[1];
local voucherOrderKey = "seckill:order:"..ARGV[1];
local userId = ARGV[2];
local voucherCount = tonumber(redis.call('get',voucherStockKey));
if(voucherCount <= 0) then
    return -1;
end
local isMember = tonumber(redis.call('SMISMEMBER',voucherOrderKey,userId));
if (isMember == 1) then
    return 2;
end
redis.call('decr',voucherStockKey);
redis.call('sadd',voucherOrderKey,userId);
return 1;
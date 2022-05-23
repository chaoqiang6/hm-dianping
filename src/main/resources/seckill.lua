-- 秒杀时，直接从redis中读取优惠券数量，如果数量<0,返回-1,再判断当前用户是否已存在，如果当前用户已有秒杀记录，返回0，否则，返回1
local voucherStockKey = 'seckill:stock:'..ARGV[1];
local voucherOrderKey = 'seckill:order:'..ARGV[1];
local orderId = ARGV[3];
local voucherId = ARGV[1] ;
local userId = ARGV[2];
local voucherCount = tonumber(redis.call('get',voucherStockKey));
if(voucherCount <= 0) then
    return -1;
end
local isMember = tonumber(redis.call('SISMEMBER',voucherOrderKey,userId));
if (isMember == 1) then
    return 2;
end
redis.call('decr',voucherStockKey);
redis.call('sadd',voucherOrderKey,userId);
--改为验证成功后向redis消费队列发送消息，业务代码通过消费者组订阅消息队列未被消费消息，若消费失败，从waitlist中取消息尝试重新消费

--XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold [LIMIT count]] *|ID field value [field value ...]
--summary: Appends a new entry to a stream
--向一个stream中添加一个entry
-- xadd stream1 * k1 v1 k2 v2
--since: 5.0.0
--group: stream


--XGROUP [CREATE key groupname ID|$ [MKSTREAM]] [SETID key groupname ID|$] [DESTROY key groupname] [CREATECONSUMER key groupname consumername] [DELCONSUMER key groupname consumername]
--summary: Create, destroy, and manage consumer groups.
--新建，销毁，管理消费者组,
--xgroup create stream1 group1 $ $表示从最后一条开始订阅，0表示从第1条开始订阅
--xgroup create stream1 group2 0
--since: 5.0.0
--group: stream

--XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds] [NOACK] STREAMS key [key ...] ID [ID ...]
--summary: Return new entries from a stream using a consumer group, or access the history of the pending entries for a given consumer. Can block.
--使用消费者组从stream中返回一个entries，或从pending entries中取出已读取未ACK的记录
--xreadgroup GROUP group1 [Count 本次查询最大数量] [BLOCK 最大阻塞时间] [不需要ACK，取出来这个消息不会放到pendinglist中] STREAMS stream1
--xreadgroup GROUP group1 consumer1 count 1 block 2000 streams stream1 >
--从未被消费消息取
--xreadgroup GROUP group1 consumer1 count 1 block 2000 streams stream1 0
--从pendinglist中取
--xreadgroup group  group2 consumer1 count 3 block 2000 streams stream1 >
--xreadgroup group  group2 consumer1 count 5 block 2000 streams stream1 0

--since: 5.0.0

--XACK key group ID [ID ...]
--summary: Marks a pending message as correctly processed, effectively removing it from the pending entries list of the consumer group. Return value of the command is the number of messages successfully acknowledged, that is, the IDs we were actually able to resolve in the PEL.
--ack确认 xack stream1 group1 1653314812611-0
--since: 5.0.0
--向消息队列中添加下单信息
redis.call('xadd','stream.order','*','id',orderId,'voucherId',voucherId,'userId',userId,"voucherStockKey",voucherStockKey,"voucherOrderKey",voucherOrderKey);
return 1;
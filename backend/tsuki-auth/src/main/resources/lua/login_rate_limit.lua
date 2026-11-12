-- KEYS[1]: 限流键
-- ARGV[1]: 窗口内允许的最大次数
-- ARGV[2]: 窗口大小（毫秒）

-- 绑定键与参数
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])

-- 原子递增
local count = redis.call('INCR', key)

-- 首次计数与窗口内放行
if count == 1 then
    redis.call('PEXPIRE', key, windowMs)
    return 0
elseif count <= limit then
    return 0
end

-- 超限查询与兜底修复
local ttl = redis.call('PTTL', key)
if ttl <= 0 then
    redis.call('PEXPIRE', key, windowMs)
    ttl = windowMs
end

return ttl

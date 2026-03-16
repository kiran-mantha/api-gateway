-- gateway/src/main/resources/scripts/rate_limit.lua

-- KEYS[1] = bucket key e.g. "rate_limit:user:123"
-- ARGV[1] = max tokens (bucket capacity)
-- ARGV[2] = refill rate  (tokens per window)
-- ARGV[3] = window size  (in seconds)
-- ARGV[4] = requested tokens (always 1)
-- Returns: remaining tokens, or -1 if rejected

local key        = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill     = tonumber(ARGV[2])
local window     = tonumber(ARGV[3])
local requested  = tonumber(ARGV[4])
local now        = tonumber(redis.call("TIME")[1])  -- unix seconds

-- Read current bucket state
local bucket     = redis.call("HMGET", key, "tokens", "last_refill")
local tokens     = tonumber(bucket[1])
local last_refill= tonumber(bucket[2])

-- First request — initialise the bucket
if tokens == nil then
    tokens      = max_tokens
    last_refill = now
end

-- Refill tokens based on elapsed time
local elapsed    = now - last_refill
local new_tokens = math.floor(elapsed * refill / window)

if new_tokens > 0 then
    tokens      = math.min(max_tokens, tokens + new_tokens)
    last_refill = now
end

-- Check if request can be served
if tokens < requested then
    -- Save updated bucket state even on rejection
    redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
    redis.call("EXPIRE", key, window * 2)
    return -1   -- rejected
end

-- Consume token and save
tokens = tokens - requested
redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
redis.call("EXPIRE", key, window * 2)

return tokens   -- remaining tokens (>= 0)
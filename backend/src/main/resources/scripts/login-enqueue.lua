-- 로그인 대기열 등록. 용량 검사 → 등록 → 순번 반환을 하나의 원자 연산으로 묶는다.
-- ZCARD 로 확인한 뒤 ZADD 하는 두 번의 왕복은 원자적이지 않아, 동시 요청이 capacity 를 넘겨 들어올 수 있다.
--
-- KEYS[1] login:queue (ZSET, member=ticketId score=enqueue epoch millis)
-- KEYS[2] login:ticket:{ticketId} (HASH)
-- ARGV[1] ticketId  ARGV[2] 현재시각(ms)  ARGV[3] capacity  ARGV[4] ticketTtl(ms)
--
-- 반환: 순번(1부터) / -1 대기열 만석

if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
    return -1
end

redis.call('ZADD', KEYS[1], tonumber(ARGV[2]), ARGV[1])
redis.call('HSET', KEYS[2], 'state', 'WAITING')
redis.call('PEXPIRE', KEYS[2], tonumber(ARGV[4]))

return redis.call('ZRANK', KEYS[1], ARGV[1]) + 1

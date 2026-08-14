-- 해싱을 마친 티켓의 결과를 기록한다. 대기열에서 빼는 것과 상태를 쓰는 것이 갈라지면
-- 순번은 줄었는데 결과가 없는 티켓이 생기므로 한 번에 처리한다.
--
-- KEYS[1] login:queue  KEYS[2] login:ticket:{ticketId}
-- ARGV[1] ticketId  ARGV[2] state(READY|FAILED)  ARGV[3] payload(boormiId 또는 errorCode)
-- ARGV[4] readyTtl(ms)
--
-- 반환: 1 기록 성공 / 0 티켓 없음(사용자 이탈·TTL 만료)

redis.call('ZREM', KEYS[1], ARGV[1])

if redis.call('EXISTS', KEYS[2]) == 0 then
    return 0
end

redis.call('HSET', KEYS[2], 'state', ARGV[2], 'payload', ARGV[3])
redis.call('PEXPIRE', KEYS[2], tonumber(ARGV[4]))

return 1

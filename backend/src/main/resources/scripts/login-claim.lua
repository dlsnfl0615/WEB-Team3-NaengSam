-- 폴링 겸 클레임. 아직 대기 중이면 순번을, 처리가 끝났으면 결과를 돌려주고 티켓을 소비한다.
-- 조회와 삭제를 나누면 동시에 들어온 두 요청이 같은 결과를 받아 세션이 두 번 만들어질 수 있어
-- 여기서 1회용 소비를 원자적으로 처리한다.
--
-- KEYS[1] login:queue  KEYS[2] login:ticket:{ticketId}
-- ARGV[1] ticketId
--
-- 반환: 빈 배열(티켓 없음) 또는 {state, payload, position, totalWaiting}
-- 숫자는 문자열로 넘긴다(Lua 테이블 안의 숫자는 정수로 잘리고, nil 은 뒤 원소를 잘라먹는다).

local state = redis.call('HGET', KEYS[2], 'state')
if not state then
    return {}
end

if state == 'WAITING' then
    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
    -- 스윕으로 ZSET 멤버만 먼저 사라졌으면 rank 가 없다. 0 으로 내려보내고 호출측이 보정한다.
    local position = 0
    if rank then
        position = rank + 1
    end
    return {state, '', tostring(position), tostring(redis.call('ZCARD', KEYS[1]))}
end

local payload = redis.call('HGET', KEYS[2], 'payload') or ''
redis.call('DEL', KEYS[2])
return {state, payload, '0', '0'}

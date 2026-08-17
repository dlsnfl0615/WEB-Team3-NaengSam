package com.naengsam.quick.domain.address.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 응답을 Redis 에 담아 두는 얇은 저장소. {@link CachedCoordinatesService}, {@link CachedDirectionsService} 만 쓴다.
 * <p>
 * <b>Spring Cache({@code @EnableCaching} + {@code @Cacheable})를 쓰지 않는 이유.</b> {@code @Cacheable} 은 빈 프록시에 붙는데, Dev 스텁을
 * 캐시에서 빼려면 어노테이션을 {@code Kakao*Service} 구현 클래스에 붙여야 한다. 그런데 그 클래스들은 테스트가 {@code new} 로 직접 만들어 쓰므로 프록시가 없고, 캐시가 조용히
 * 무효가 된다. "테스트에서만 동작이 다른 어노테이션"보다 별도 데코레이터 클래스가 낫다. 부수적으로 부트 4.1 액추에이터에는 캐시 메트릭 바인더가 없어 Spring Cache 를 써도 히트율을
 * 따로 붙여야 한다.
 * <p>
 * <b>모든 실패를 삼키는 이유(fail-open).</b> 캐시는 카카오 호출을 줄이는 장치일 뿐이라, Redis 가 죽거나 저장된 JSON 이 깨져도 카카오 직접 호출로 그냥 흘러가야 한다.
 * {@code management.health.redis.enabled=false} 로 "Redis 장애가 앱을 내리지 않는다"고 정해 둔 것과 같은 맥락이다.
 * <p>
 * <b>로그인 대기열과 같은 Redis 인스턴스를 쓴다.</b> 그 인스턴스는 {@code maxmemory 64mb} + {@code noeviction} 이고, 정책을
 * {@code allkeys-*}/{@code volatile-*} 로 바꾸면 {@code login:ticket:*} 가 evict 되어 대기자가 조용히 사라진다. 그래서 캐시 총량은 정책이 아니라 TTL
 * 로만 묶는다. 64MB 가 차면 모든 쓰기가 OOM 이 되어 대기열까지 멈추므로, {@code kakao.cache.error{reason="oom"}} 카운터를 그 조기경보로 쓴다
 * (자세한 내용은 {@code redis/README.md}).
 * <p>
 * <b>오류를 {@code kakao.cache} 가 아니라 별도 메트릭으로 세는 이유.</b> 마이크로미터·프로메테우스는 같은 메트릭 이름에 태그 키 집합이 달라지는 것을 허용하지
 * 않는다. 오류에만 {@code reason} 을 달 수 없으므로 히트율(비율 지표)과 오류(사건 지표)를 이름부터 나눈다. 덕분에 히트율 분모에서 오류를 빼는 처리도 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kakao.enabled", havingValue = "true", matchIfMissing = true)
public class KakaoResponseCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * 캐시된 응답을 꺼낸다. 값이 없거나 Redis 조회·역직렬화가 실패하면 {@code null} 을 돌려준다 — 호출부는 둘을 구분하지 않고 카카오를 호출하면 된다.
     *
     * @param api 메트릭 태그로만 쓰는 호출 종류({@code geocode} / {@code route})
     */
    public <T> T get(String key, Class<T> type, String api) {
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("카카오 응답 캐시 조회 실패(Redis) — 카카오를 직접 호출한다. key={}", key, e);
            countError(api, "get", e);
            return null;
        }

        if (json == null) {
            meterRegistry.counter("kakao.cache", "api", api, "result", "miss").increment();
            return null;
        }

        try {
            T value = objectMapper.readValue(json, type);
            meterRegistry.counter("kakao.cache", "api", api, "result", "hit").increment();
            return value;
        } catch (Exception e) {
            // DTO 모양이 바뀌면 예전에 저장된 값이 여기서 깨진다. 미스로 처리하면 새 값으로 덮여 자동 복구된다.
            log.warn("카카오 응답 캐시 역직렬화 실패 — 미스로 처리한다. key={}", key, e);
            countError(api, "get", e);
            return null;
        }
    }

    /**
     * 응답을 캐시에 넣는다. 실패해도 아무 일도 일어나지 않는다(이미 카카오 응답은 손에 있다).
     */
    public void put(String key, Object value, Duration ttl, String api) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("카카오 응답 캐시 저장 실패 — 다음 호출도 카카오를 탄다. key={}", key, e);
            countError(api, "put", e);
        }
    }

    /**
     * 오류를 원인별로 센다. {@code op} 를 같이 다는 이유는 레디스가 완전히 죽으면 요청 1건이 {@code get}·{@code put} 양쪽에서 실패해, 이 값만으로는 영향받은 요청
     * 수를 셀 수 없기 때문이다.
     */
    private void countError(String api, String op, Exception e) {
        meterRegistry.counter("kakao.cache.error", "api", api, "op", op, "reason", reasonOf(e)).increment();
    }

    /**
     * 대응이 갈리는 축으로만 나눈다 — {@code connection} 은 레디스 자체를 봐야 하고(로그인 대기열도 같이 죽어 있다), {@code oom} 은 TTL 축소 →
     * {@code maxmemory} 인상 순으로 대응하며, {@code serialization} 은 DTO 모양이 바뀐 배포 직후에만 잠깐 튀고 새 값으로 덮여 자동 복구되는 정상 현상이다
     * (대응표는 {@code redis/README.md}).
     */
    private static String reasonOf(Exception e) {
        if (e instanceof RedisConnectionFailureException) {
            return "connection";
        }
        if (e instanceof QueryTimeoutException) {
            return "timeout";
        }
        if (e instanceof RedisSystemException && isOutOfMemory(e)) {
            return "oom";
        }
        if (e instanceof JacksonException) {
            return "serialization";
        }
        return "other";
    }

    /**
     * 레디스는 {@code maxmemory} 를 넘긴 쓰기에 {@code "OOM command not allowed..."} 로 시작하는 에러를 응답하고, Lettuce 가 그 메시지를 그대로
     * 감싸 올린다. 예외 타입만으로는 다른 서버 에러와 구분되지 않아 메시지를 본다.
     */
    private static boolean isOutOfMemory(Exception e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.startsWith("OOM");
    }
}

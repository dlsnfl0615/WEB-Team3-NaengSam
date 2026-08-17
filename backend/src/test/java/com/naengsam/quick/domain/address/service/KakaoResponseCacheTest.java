package com.naengsam.quick.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 응답 캐시의 fail-open 동작(레디스 장애·깨진 값이 미스로 처리되는지), 오류 원인 분류, 실제 ObjectMapper 라운드트립을 검증한다.
 */
class KakaoResponseCacheTest {

    private static final Duration TTL = Duration.ofHours(1);

    private ValueOperations<String, String> valueOperations;
    private SimpleMeterRegistry meterRegistry;
    private KakaoResponseCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(valueOperations);

        meterRegistry = new SimpleMeterRegistry();
        cache = new KakaoResponseCache(redis, new ObjectMapper(), meterRegistry);
    }

    @Test
    void 레디스_조회가_실패하면_null_을_돌려준다() {
        given(valueOperations.get("kakao:route:x")).willThrow(new RedisConnectionFailureException("down"));

        KakaoDirectionsResponseDto.Route result =
                cache.get("kakao:route:x", KakaoDirectionsResponseDto.Route.class, "route");

        assertThat(result).isNull();
        assertThat(errorCounter("route", "get", "connection")).isEqualTo(1.0);
    }

    @Test
    void 저장된_값이_없으면_null_을_돌려주고_미스로_센다() {
        given(valueOperations.get("kakao:geo:서울특별시 강남구 테헤란로 427")).willReturn(null);

        CoordinatesResponseDto result =
                cache.get("kakao:geo:서울특별시 강남구 테헤란로 427", CoordinatesResponseDto.class, "geocode");

        assertThat(result).isNull();
        assertThat(counter("geocode", "miss")).isEqualTo(1.0);
    }

    @Test
    void 저장된_값이_깨져있으면_null_을_돌려주고_serialization_원인으로_센다() {
        given(valueOperations.get("kakao:route:x")).willReturn("{ 이건 JSON 이 아니다");

        KakaoDirectionsResponseDto.Route result =
                cache.get("kakao:route:x", KakaoDirectionsResponseDto.Route.class, "route");

        assertThat(result).isNull();
        assertThat(errorCounter("route", "get", "serialization")).isEqualTo(1.0);
    }

    @Test
    void 레디스_저장이_실패해도_예외가_전파되지_않는다() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        Throwable thrown = catchThrowable(() -> cache.put("kakao:route:x", route(), TTL, "route"));

        assertThat(thrown).isNull();
        assertThat(errorCounter("route", "put", "connection")).isEqualTo(1.0);
    }

    @Test
    void 메모리가_차서_저장이_실패하면_oom_원인으로_센다() {
        // 레디스가 maxmemory 를 넘긴 쓰기에 돌려주는 에러를 Lettuce 클래스 없이 최심 원인 메시지로만 재현한다.
        willThrow(new RedisSystemException("Error in execution",
                new RuntimeException("OOM command not allowed when used memory > 'maxmemory'.")))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        cache.put("kakao:route:x", route(), TTL, "route");

        assertThat(errorCounter("route", "put", "oom")).isEqualTo(1.0);
    }

    @Test
    void 서버_에러라도_OOM_이_아니면_other_로_센다() {
        willThrow(new RedisSystemException("Error in execution", new RuntimeException("READONLY You can't write")))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        cache.put("kakao:route:x", route(), TTL, "route");

        assertThat(errorCounter("route", "put", "other")).isEqualTo(1.0);
    }

    @Test
    void 커맨드가_타임아웃되면_timeout_원인으로_센다() {
        given(valueOperations.get("kakao:route:x")).willThrow(new QueryTimeoutException("timed out"));

        KakaoDirectionsResponseDto.Route result =
                cache.get("kakao:route:x", KakaoDirectionsResponseDto.Route.class, "route");

        assertThat(result).isNull();
        assertThat(errorCounter("route", "get", "timeout")).isEqualTo(1.0);
    }

    @Test
    void 분류되지_않는_예외는_other_로_센다() {
        given(valueOperations.get("kakao:geo:테헤란로 427")).willThrow(new IllegalStateException("모르는 실패"));

        CoordinatesResponseDto result =
                cache.get("kakao:geo:테헤란로 427", CoordinatesResponseDto.class, "geocode");

        assertThat(result).isNull();
        assertThat(errorCounter("geocode", "get", "other")).isEqualTo(1.0);
    }

    @Test
    void 저장된_JSON_을_원래_레코드로_되돌린다() {
        // record + double[][] + 배열 조합이 Jackson 3 에서 그대로 왕복하는지 확인한다.
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(route());
        given(valueOperations.get("kakao:route:x")).willReturn(json);

        KakaoDirectionsResponseDto.Route result =
                cache.get("kakao:route:x", KakaoDirectionsResponseDto.Route.class, "route");

        assertThat(result.properties().totalDistance()).isEqualTo(1200);
        assertThat(result.properties().totalTime()).isEqualTo(900);
        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs()[0].steps()[0].path().points()[1]).containsExactly(127.027, 37.4987);
        assertThat(counter("route", "hit")).isEqualTo(1.0);
    }

    @Test
    void 스네이크케이스_지오코딩_응답도_그대로_왕복한다() {
        // CoordinatesResponseDto 는 클래스 레벨 @JsonNaming(SnakeCase) 라 쓰기/읽기가 같은 매퍼여야 대칭이다.
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(coordinates());
        given(valueOperations.get("kakao:geo:테헤란로 427")).willReturn(json);

        CoordinatesResponseDto result =
                cache.get("kakao:geo:테헤란로 427", CoordinatesResponseDto.class, "geocode");

        CoordinatesResponseDto.RoadAddress roadAddress = result.documents().getFirst().roadAddress();
        assertThat(roadAddress.addressName()).isEqualTo("서울 강남구 테헤란로 427");
        assertThat(roadAddress.x()).isEqualTo("127.027");
        assertThat(roadAddress.y()).isEqualTo("37.4987");
        assertThat(counter("geocode", "hit")).isEqualTo(1.0);
    }

    private double counter(String api, String result) {
        return meterRegistry.counter("kakao.cache", "api", api, "result", result).count();
    }

    private double errorCounter(String api, String op, String reason) {
        return meterRegistry.counter("kakao.cache.error", "api", api, "op", op, "reason", reason).count();
    }

    private KakaoDirectionsResponseDto.Route route() {
        double[][] points = {{127.0270, 37.4986}, {127.0270, 37.4987}};
        KakaoDirectionsResponseDto.Step step = new KakaoDirectionsResponseDto.Step(
                new KakaoDirectionsResponseDto.StepProperties(120, "직진", 90, 127.0270, 37.4986),
                new KakaoDirectionsResponseDto.Path(points));
        KakaoDirectionsResponseDto.Leg leg = new KakaoDirectionsResponseDto.Leg(
                new KakaoDirectionsResponseDto.LegProperties(1200, 900),
                new KakaoDirectionsResponseDto.Step[]{step});
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(1200, 900),
                new KakaoDirectionsResponseDto.Leg[]{leg});
    }

    private CoordinatesResponseDto coordinates() {
        CoordinatesResponseDto.RoadAddress roadAddress = new CoordinatesResponseDto.RoadAddress(
                "서울 강남구 테헤란로 427", "서울", "강남구", "역삼동", "테헤란로",
                "427", null, "위워크타워", "06159", "127.027", "37.4987");
        return new CoordinatesResponseDto(
                List.of(new CoordinatesResponseDto.Document(roadAddress)));
    }
}

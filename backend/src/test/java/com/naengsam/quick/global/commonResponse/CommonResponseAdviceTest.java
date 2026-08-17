package com.naengsam.quick.global.commonResponse;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.global.debug.InMemoryStateController;
import com.naengsam.quick.global.notification.PushSubscriptionController;
import com.naengsam.quick.global.sse.SseController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 공통 응답 봉투의 적용 범위를 지킨다.
 *
 * <p>{@link CommonResponseAdvice}는 {@code basePackages}로 대상을 한정하므로, {@code global} 아래에 새 JSON API 컨트롤러를 만들면서 패키지를 추가하지 않으면
 * 응답이 봉투 없이 나간다. 이때 서버는 200을 주고 프론트만 조용히 {@code .result}에서 undefined를 읽어 빈 화면이 되므로, 런타임에 알아채기 어렵다. 실제로 인메모리 현황 API가 이
 * 실수로 빈 화면을 냈다.
 *
 * <p>MockMvc standalone 테스트로는 잡을 수 없다 — {@code setControllerAdvice}가 {@code basePackages}를 무시하고 어드바이스를 무조건 등록하기 때문이다.
 * 그래서 애노테이션 선언 자체를 검증한다.
 */
class CommonResponseAdviceTest {

    private static boolean wraps(Class<?> controller) {
        String[] basePackages = CommonResponseAdvice.class
                .getAnnotation(RestControllerAdvice.class)
                .basePackages();
        return Arrays.stream(basePackages).anyMatch(basePackage ->
                controller.getPackageName().equals(basePackage)
                        || controller.getPackageName().startsWith(basePackage + "."));
    }

    @Test
    void 인메모리_현황_API는_공통_응답_봉투로_감싸진다() {
        assertThat(wraps(InMemoryStateController.class)).isTrue();
    }

    @Test
    void 웹푸시_구독_API는_공통_응답_봉투로_감싸진다() {
        assertThat(wraps(PushSubscriptionController.class)).isTrue();
    }

    @Test
    void SSE_스트림은_공통_응답_봉투로_감싸지_않는다() {
        assertThat(wraps(SseController.class)).isFalse();
    }
}

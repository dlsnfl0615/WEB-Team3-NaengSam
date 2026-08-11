package com.naengsam.quick.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독 엔드포인트가 연결 수립은 200으로, 한도 초과 거부는 204로 응답하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock
    private SseService sseService;

    @InjectMocks
    private SseController sseController;

    @Test
    void 구독에_성공하면_emitter를_200으로_반환한다() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        given(sseService.subscribe(userId)).willReturn(emitter);

        ResponseEntity<SseEmitter> response = sseController.subscribe(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void 연결_한도를_초과하면_바디_없이_204를_반환한다() {
        UUID userId = UUID.randomUUID();
        given(sseService.subscribe(userId)).willReturn(null);

        ResponseEntity<SseEmitter> response = sseController.subscribe(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}

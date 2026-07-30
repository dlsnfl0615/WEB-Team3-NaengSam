package com.naengsam.quick.global.sse;

import com.naengsam.quick.global.session.LoginRequired;
import com.naengsam.quick.global.session.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 도메인 공통 SSE 구독 엔드포인트. 로그인 사용자당 하나의 연결을 맺고, 모든 도메인이 이 연결 위로 이벤트를 흘려보낸다. 클라이언트는 이벤트 이름으로 종류를 구분한다.
 */
@Tag(name = "SSE", description = "실시간 이벤트 SSE 구독")
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @Operation(summary = "실시간 이벤트 SSE 구독",
            description = "로그인 사용자로 SSE 연결을 맺는다. 최초 connected 이벤트 후, 각 도메인의 이벤트가 이름별로 전달된다.")
    @LoginRequired
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@LoginUser UUID userId) {
        return sseService.subscribe(userId);
    }
}

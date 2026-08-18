package com.naengsam.quick.global.sse;

import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.LoginSession;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 도메인 공통 SSE 구독 엔드포인트. 계정당 하나의 연결을 맺고, 모든 도메인이 이 연결 위로 이벤트를 흘려보낸다. 클라이언트는 이벤트 이름으로 종류를 구분한다.
 */
@Tag(name = "SSE", description = "실시간 이벤트 SSE 구독")
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @Operation(summary = "실시간 이벤트 SSE 구독",
            description = "로그인 계정으로 SSE 연결을 맺는다. 최초 connected 이벤트(connectionId 포함) 후, 각 도메인의 이벤트가 "
                    + "이름별로 전달된다. 이미 연결이 있어도 거부하지 않고 즉시 새 연결로 교체한다.")
    @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 수립")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@LoginUser UUID userId, HttpServletRequest httpRequest) {
        return sseService.subscribe(userId, currentSessionId(httpRequest));
    }

    @Operation(summary = "SSE 연결 명시적 해제",
            description = "탭이 닫힐 때 sendBeacon 등으로 호출한다. connectionId가 현재 연결과 일치할 때만 제거하며, "
                    + "이미 새 연결로 교체됐거나 연결이 없으면 idempotent하게 아무 일도 하지 않는다.")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    @PostMapping("/disconnect")
    public void disconnect(@LoginUser UUID userId, @RequestParam String connectionId, HttpServletRequest httpRequest) {
        sseService.disconnect(userId, currentSessionId(httpRequest), connectionId);
    }

    private String currentSessionId(HttpServletRequest httpRequest) {
        return LoginSession.current(httpRequest)
                .map(LoginSession::getSessionId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));
    }
}

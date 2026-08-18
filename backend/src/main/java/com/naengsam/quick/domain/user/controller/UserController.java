package com.naengsam.quick.domain.user.controller;

import com.naengsam.quick.domain.user.dto.ActiveRole;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.LoginResultDto;
import com.naengsam.quick.domain.user.dto.SendVerificationCodeRequest;
import com.naengsam.quick.domain.user.dto.SignUpRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.dto.VerifyCodeRequest;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.domain.user.service.LoginQueue;
import com.naengsam.quick.domain.user.service.SmsVerificationService;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.session.ActiveSession;
import com.naengsam.quick.global.session.ActiveSessionRegistry;
import com.naengsam.quick.global.session.LoginSession;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.session.PublicApi;
import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseConnection;
import com.naengsam.quick.global.sse.SseConnectionManager;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController()
@RequestMapping("/api/v1/user")
@Tag(name = "유저컨트롤러", description = "유저컨트롤러입니다")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final LoginQueue loginQueue;
    private final SmsVerificationService smsVerificationService;
    private final ActiveSessionRegistry activeSessionRegistry;
    private final SseConnectionManager sseConnectionManager;

    @PublicApi
    @Operation(summary = "인증문자 발송", description = "휴대폰 번호로 인증번호를 발송한다.")
    @PostMapping("/verification-code")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"VERIFICATION_CODE_REQUEST_EXCEEDED"})
    public void sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        smsVerificationService.send(request.phoneNumber());
    }

    @PublicApi
    @Operation(summary = "인증문자 검증", description = "발송된 인증번호를 검증하고 해당 번호를 인증 완료 처리한다.")
    @PostMapping("/verification-code/verify")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"INVALID_VERIFICATION_CODE", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_CODE_ATTEMPTS_EXCEEDED"})
    public void verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        smsVerificationService.verify(request.phoneNumber(), request.code());
    }

    @PublicApi
    @Operation(summary = "회원가입", description = "휴대폰 인증을 마친 사용자를 가입시킨다.")
    @PostMapping("/signup")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"ALREADY_REGISTERED", "PHONE_ALREADY_REGISTERED", "PHONE_NOT_VERIFIED"})
    @ApiErrorCodes(enumClass = UserErrorCode.class, codes = {"DUPLICATE_NICKNAME"})
    public UserDto signup(@Valid @RequestBody SignUpRequest request) {
        return userService.signup(request);
    }

    @PublicApi
    @Operation(summary = "로그인",
            description = "이메일/비밀번호로 로그인한다. 동시 로그인이 몰리면 세션 대신 대기 티켓(QUEUED)을 발급한다.")
    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"LOGIN_FAILED", "SUSPENDED_ACCOUNT", "WITHDRAWN_ACCOUNT", "LOGIN_QUEUE_FULL",
                    "LOGIN_QUEUE_UNAVAILABLE"})
    public LoginResultDto login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return establishSessionIfReady(loginQueue.submit(request), httpRequest);
    }

    @PublicApi
    @Operation(summary = "로그인 대기열 상태 조회",
            description = "대기 티켓의 순번을 조회하고, 차례가 되면 그 자리에서 세션을 생성한다. "
                    + "응답의 pollAfterMs 만큼 기다렸다가 다시 호출한다.")
    @PostMapping("/login/queue/{ticketId}")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"LOGIN_FAILED", "SUSPENDED_ACCOUNT", "WITHDRAWN_ACCOUNT", "LOGIN_TICKET_EXPIRED",
                    "LOGIN_QUEUE_UNAVAILABLE"})
    public LoginResultDto pollLoginQueue(@PathVariable String ticketId, HttpServletRequest httpRequest) {
        return establishSessionIfReady(loginQueue.poll(ticketId), httpRequest);
    }

    /**
     * 대기열을 통과했으면 세션을 만들고, 아직 대기 중이면 대기 응답을 그대로 돌려준다.
     * 즉시 로그인 경로와 대기열 클레임 경로가 같은 세션 생성 로직을 쓰도록 한 곳에 모아둔다.
     */
    private LoginResultDto establishSessionIfReady(LoginQueue.Progress progress, HttpServletRequest httpRequest) {
        UUID boormiId = progress.boormiId();
        if (boormiId == null) {
            return progress.response();
        }

        LoginSession newSession = LoginSession.create(httpRequest);
        newSession.login(boormiId);

        // 새 세션 등록 후 이전 활성 세션이 있으면, 응답 반환 전에 이전 SSE를 끊고 이전 세션을 무효화한다. 새
        // ActiveSession의 SSE 슬롯은 비어 있으므로, 새 로그인 브라우저가 SSE 화면에 진입할 때 새 연결을 만든다.
        ActiveSession previous = activeSessionRegistry.replace(boormiId, newSession);
        if (previous != null) {
            SseConnection previousConnection = previous.sseConnection();
            if (previousConnection != null) {
                sseConnectionManager.close(previousConnection, SseCloseReason.REPLACED_BY_LOGIN);
            }
            previous.session().invalidate();
        }
        return progress.response();
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 무효화한다.")
    @PostMapping("/logout")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"UNAUTHORIZED"})
    public void logout(HttpServletRequest httpRequest) {
        LoginSession.current(httpRequest).ifPresent(session -> {
            activeSessionRegistry.removeIfCurrent(session.getSessionId())
                    .ifPresent(removed -> {
                        SseConnection connection = removed.activeSession().sseConnection();
                        if (connection != null) {
                            sseConnectionManager.close(connection, SseCloseReason.LOGOUT);
                        }
                    });
            session.invalidate();
        });
    }

    @Operation(summary = "내 정보", description = "로그인한 사용자 정보를 반환한다.")
    @GetMapping("/me")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"INVALID_SESSION"})
    public UserDto me(@LoginUser UUID boormiId) {
        return userService.getUserInfo(boormiId);
    }

    @GetMapping("/role")
    @Operation(summary = "부르미/드리미 전환", description = """
            로그인 한 사용자가 target 역할로 전환 가능한지 확인한다.
            매칭·배달이 진행 중이면 어느 방향으로도 전환할 수 없다.""")
    @ApiErrorCodes(enumClass = UserErrorCode.class,
            codes = {"DREAMI_NOT_REGISTERED", "DREAMI_NOT_APPROVED",
                    "CANNOT_CHANGE_ROLE_WITH_ACTIVE_ORDER", "CANNOT_CHANGE_ROLE_WHILE_MATCHING"})
    public void changeRole(@LoginUser UUID boormiId, @RequestParam ActiveRole target) {
        userService.changeRole(boormiId, target);
    }
}

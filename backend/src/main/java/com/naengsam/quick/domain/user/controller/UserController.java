package com.naengsam.quick.domain.user.controller;

import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.SendVerificationCodeRequest;
import com.naengsam.quick.domain.user.dto.SignUpRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.dto.VerifyCodeRequest;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.domain.user.service.SmsVerificationService;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.session.ActiveSession;
import com.naengsam.quick.global.session.ActiveSessionRegistry;
import com.naengsam.quick.global.session.LoginSession;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.session.PublicApi;
import com.naengsam.quick.global.sse.SseCloseReason;
import com.naengsam.quick.global.sse.SseEmitterRegistry;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController()
@RequestMapping("/api/v1/user")
@Tag(name = "유저컨트롤러", description = "유저컨트롤러입니다")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final SmsVerificationService smsVerificationService;
    private final ActiveSessionRegistry activeSessionRegistry;
    private final SseEmitterRegistry sseEmitterRegistry;

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
    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 세션을 생성한다.")
    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"LOGIN_FAILED", "SUSPENDED_ACCOUNT", "WITHDRAWN_ACCOUNT"})
    public void login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UUID boormiId = userService.login(request);

        LoginSession newSession = LoginSession.create(httpRequest);
        newSession.login(boormiId);

        // 새 세션 등록 후 이전 활성 세션이 있으면, 응답 반환 전에 이전 SSE를 모두 끊고 이전 세션을 무효화한다.
        ActiveSession previous = activeSessionRegistry.replace(boormiId, newSession);
        if (previous != null) {
            sseEmitterRegistry.disconnectAll(boormiId, SseCloseReason.REPLACED_BY_LOGIN);
            previous.session().invalidate();
        }
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 무효화한다.")
    @PostMapping("/logout")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class,
            codes = {"UNAUTHORIZED"})
    public void logout(HttpServletRequest httpRequest) {
        LoginSession.current(httpRequest).ifPresent(session -> {
            activeSessionRegistry.removeIfCurrent(session.getSessionId())
                    .ifPresent(boormiId -> sseEmitterRegistry.disconnectAll(boormiId, SseCloseReason.LOGOUT));
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
    @Operation(summary = "부르미/드리미 전환", description = "로그인 한 사용자가 부르미/드리미 전환 가능한지 확인한다.")
    public void changeRole(@LoginUser UUID boormiId) {
        userService.changeRole(boormiId);
    }
}

package com.naengsam.quick.domain.user.controller;

import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.session.LoginRequired;
import com.naengsam.quick.global.session.LoginSession;
import com.naengsam.quick.global.session.LoginUser;
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

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 세션을 생성한다.")
    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = UserErrorCode.class,
            codes = {"LOGIN_FAILED", "SUSPENDED_ACCOUNT", "WITHDRAWN_ACCOUNT"})
    public void login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UUID boormiId = userService.login(request);
        LoginSession.create(httpRequest).login(boormiId);
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 무효화한다.")
    @PostMapping("/logout")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @LoginRequired
    @ApiErrorCodes(enumClass = UserErrorCode.class,
            codes = {"UNAUTHORIZED"})
    public void logout(HttpServletRequest httpRequest) {
        LoginSession.current(httpRequest).ifPresent(LoginSession::invalidate);
    }

    @Operation(summary = "내 정보", description = "로그인한 사용자 정보를 반환한다.")
    @GetMapping("/me")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = UserErrorCode.class,
            codes = {"INVALID_SESSION"})
    public UserDto me(@LoginUser UUID boormiId) {
        return userService.getUserInfo(boormiId);
    }

    @GetMapping("/signin")
    public void singup() {

    }
}

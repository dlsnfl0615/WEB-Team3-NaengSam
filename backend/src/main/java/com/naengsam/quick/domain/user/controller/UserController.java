package com.naengsam.quick.domain.user.controller;

import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/v1/user")
@Tag(name = "유저컨트롤러", description = "유저컨트롤러입니다")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/")
    @Tag(name = "유저컨트롤러")
    @Operation(summary = "유저 컨트롤러 테스트", description = "user를 반환합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful Operation"),
    })
    @ApiErrorCodes(enumClass = UserErrorCode.class, codes = {
            "USER_NOT_FOUND",
            "INVALID_PASSWORD",
            "INCORRECT_PASSWORD"})
    UserDto helloUser() {
        return userService.hello();
    }

    @GetMapping("/hello")
    @Tag(name = "유저컨트롤러")
    @Operation(summary = "유저 컨트롤러 테스트", description = "hello를 반환합니다")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "207", description = "Successful Operation"),
    })
    String stringHello() {
        return "hello";
    }

    @GetMapping("/error")
    @ApiErrorCodes(enumClass = UserErrorCode.class, codes = {"USER_NOT_FOUND"})
    void errorTest() {
        userService.Error();
    }
}

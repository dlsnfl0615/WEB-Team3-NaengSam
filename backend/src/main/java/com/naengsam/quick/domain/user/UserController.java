package com.naengsam.quick.domain.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    ResponseEntity<UserDto> helloUser() {
        return ResponseEntity.status(200).body(userService.hello());
    }
}

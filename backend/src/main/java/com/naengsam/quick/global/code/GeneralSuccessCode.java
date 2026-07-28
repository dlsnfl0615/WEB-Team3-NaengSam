package com.naengsam.quick.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum GeneralSuccessCode implements BaseCode {

    OK(HttpStatus.OK, "COM200", "요청에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "COM201", "생성에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

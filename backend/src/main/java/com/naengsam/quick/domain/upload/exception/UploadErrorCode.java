package com.naengsam.quick.domain.upload.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UploadErrorCode implements BaseErrorCode {

    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "UPLOAD_001", "지원하지 않는 파일 형식입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

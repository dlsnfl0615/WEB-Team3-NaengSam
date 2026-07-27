package com.naengsam.quick.global.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.commonResponse.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e,
                                                                        HttpServletRequest request) {
        BaseErrorCode errorCode = e.getErrorCode();

        if (errorCode.getStatus().is5xxServerError()) {
            // 서버 결함이므로 전체 스택이 필요하다
            log.error("BusinessException: {} {} {} {}", errorCode.getCode(), errorCode.getMessage(),
                    request.getMethod(), request.getRequestURI(), e);
        } else {
            // 4xx 는 의도된 흐름 — 어디서 던졌는지만 한 줄로 남긴다
            log.warn("BusinessException: {} {} {} {} (at {})", errorCode.getCode(), errorCode.getMessage(),
                    request.getMethod(), request.getRequestURI(), origin(e));
        }

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    /**
     * 스택트레이스 대신 던진 지점 한 줄만.
     */
    private String origin(Throwable e) {
        StackTraceElement[] stack = e.getStackTrace();
        return stack.length > 0 ? stack[0].toString() : "unknown";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    /**
     * 404, 405 등 Spring MVC 가 처리하는 표준 예외. 본래 상태코드는 그대로 두고 body 만 공통 포맷으로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        log.warn("Spring MVC exception: {} {}", statusCode, e.getClass().getSimpleName());
        BaseErrorCode errorCode = switch (statusCode.value()) {
            case 400 -> GeneralErrorCode.BAD_REQUEST;
            case 404 -> GeneralErrorCode.NOT_FOUND;
            case 405 -> GeneralErrorCode.METHOD_NOT_ALLOWED;
            default -> GeneralErrorCode.INTERNAL_SERVER_ERROR;
        };

        return super.handleExceptionInternal(e, CommonResponse.onFail(errorCode, null), headers, statusCode, request);
    }
}

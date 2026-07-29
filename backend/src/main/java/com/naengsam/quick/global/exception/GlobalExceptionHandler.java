package com.naengsam.quick.global.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.commonResponse.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
            log.debug("", e);
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

    /**
     * 동시 수정 충돌(낙관적 락 실패) → COMMON_008.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<CommonResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e,
                                                                     HttpServletRequest request) {
        BaseErrorCode errorCode = GeneralErrorCode.CONFLICT;
        log.warn("OptimisticLockingFailure: {} {} (at {})", request.getMethod(), request.getRequestURI(), origin(e));
        log.debug("", e);

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    /**
     * UNIQUE 제약 위반 등 데이터 무결성 충돌(예: 이메일/휴대폰 동시 가입 경합) → COMMON_008.
     * 서비스단 사전 검사를 통과한 뒤 발생하는 드문 경합을 500 이 아닌 409 로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                                             HttpServletRequest request) {
        BaseErrorCode errorCode = GeneralErrorCode.CONFLICT;
        log.warn("DataIntegrityViolation: {} {} (at {})", request.getMethod(), request.getRequestURI(), origin(e));
        log.debug("", e);

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    /**
     * Spring MVC 가 처리하는 표준 예외. 본래 상태코드는 그대로 두고 body 만 공통 포맷으로 바꾼다. 예외 타입별로 세분화된 COMMON 코드를 매핑한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        BaseErrorCode errorCode = resolve(e, statusCode);
        log.warn("Spring MVC exception: {} {} -> {}", statusCode, e.getClass().getSimpleName(), errorCode.getCode());
        log.debug("", e);
        return super.handleExceptionInternal(e, CommonResponse.onFail(errorCode, null), headers, statusCode, request);
    }

    private BaseErrorCode resolve(Exception e, HttpStatusCode statusCode) {
        return switch (e) {
            case MethodArgumentNotValidException ignored -> GeneralErrorCode.INVALID_PARAMETER;          // COMMON_001
            case TypeMismatchException ignored ->
                    GeneralErrorCode.TYPE_MISMATCH;                        // COMMON_002 (MethodArgumentTypeMismatch 포함)
            case MissingServletRequestParameterException ignored ->
                    GeneralErrorCode.MISSING_REQUEST_VALUE; // COMMON_003
            case ServletRequestBindingException ignored ->
                    GeneralErrorCode.MISSING_REQUEST_VALUE;       // COMMON_003 (헤더 누락 등 상위타입)
            case HttpMessageNotReadableException ignored -> GeneralErrorCode.MALFORMED_REQUEST_BODY;     // COMMON_004
            case HttpMediaTypeNotSupportedException ignored -> GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE;  // COMMON_007
            case HttpRequestMethodNotSupportedException ignored -> GeneralErrorCode.METHOD_NOT_ALLOWED;  // COMMON_006
            case NoHandlerFoundException ignored -> GeneralErrorCode.NOT_FOUND;                          // COMMON_005
            case NoResourceFoundException ignored -> GeneralErrorCode.NOT_FOUND;                         // COMMON_005
            default -> switch (statusCode.value()) {
                case 400 -> GeneralErrorCode.INVALID_PARAMETER;
                case 404 -> GeneralErrorCode.NOT_FOUND;
                case 405 -> GeneralErrorCode.METHOD_NOT_ALLOWED;
                case 415 -> GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE;
                default -> GeneralErrorCode.INTERNAL_SERVER_ERROR;
            };
        };
    }
}

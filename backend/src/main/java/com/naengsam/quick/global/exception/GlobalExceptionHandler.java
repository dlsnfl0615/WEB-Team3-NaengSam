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

// @RestControllerAdvice가 앱 전체(모든 @RestController)에서 던져진 예외를 이 클래스로 모아준다.
// 컨트롤러마다 try-catch를 두는 대신, 이 클래스 하나가 "전역 catch 블록" 역할을 한다.
// ResponseEntityExceptionHandler를 상속하는 이유는 Spring MVC가 내부에서 던지는 표준 예외
// (파라미터 바인딩 실패 등)를 처리하는 handleExceptionInternal(...)을 오버라이드하기 위해서다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 도메인 서비스가 throw new BusinessException(XxxErrorCode.YYY) 로 던진 예외는
    // 중간에 별도 catch 없이 그대로 위로 전파되다가, 타입 매칭으로 반드시 이 메서드에서 잡힌다.
    // 즉 BusinessException의 처리 경로는 오직 여기 하나뿐이다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e,
                                                                        HttpServletRequest request) {
        // 예외를 생성할 때 실어 보낸 도메인별 에러코드(enum 상수)를 꺼낸다.
        // 이 안에 이미 HTTP 상태 · 응답 코드 문자열 · 사용자에게 보여줄 메시지가 다 들어 있다.
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

        // 에러코드가 정해둔 HTTP 상태 그대로, 바디는 공통 응답 포맷(CommonResponse)으로 감싸 내려보낸다.
        // 이 리턴값이 클라이언트가 실제로 받는 최종 HTTP 응답이며, 도메인 서비스는 이 조립 과정을 전혀 모른다.
        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    /**
     * 스택트레이스 대신 던진 지점 한 줄만.
     */
    // 예외의 스택트레이스 배열 맨 위(= 예외가 최초로 만들어진 지점)만 뽑아 로그를 가볍게 유지한다.
    private String origin(Throwable e) {
        StackTraceElement[] stack = e.getStackTrace();
        return stack.length > 0 ? stack[0].toString() : "unknown";
    }

    /**
     * 동시 수정 충돌(낙관적 락 실패) → COMMON_008.
     */
    // 서비스 코드가 직접 던지는 게 아니라, JPA가 버전 충돌(동시 수정)을 감지했을 때 프레임워크가 던지는 예외를 잡는다.
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
    // 서비스 로직의 사전 검사(existsByEmail 등)를 통과한 뒤에도 발생하는 드문 동시성 경합이라,
    // 버그로 보고 500을 내리는 대신 "다시 시도해볼 수 있는 충돌"인 409로 다룬다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                                             HttpServletRequest request) {
        BaseErrorCode errorCode = GeneralErrorCode.CONFLICT;
        log.warn("DataIntegrityViolation: {} {} (at {})", request.getMethod(), request.getRequestURI(), origin(e));
        log.debug("", e);

        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.onFail(errorCode, null));
    }

    // 위의 어떤 @ExceptionHandler에도 걸리지 않은 나머지 모든 예외를 받는 최종 캐치올이다.
    // 여기까지 흘러온다는 것 자체가 예상하지 못한 상황(버그, 외부 장애 등)이라는 뜻이라 무조건 500으로 처리한다.
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
    // 컨트롤러 메서드 본문에 진입하기도 전에(파라미터 바인딩·검증 단계 등) Spring MVC 자체가 던지는 예외들이 모이는 지점.
    // 부모 클래스(ResponseEntityExceptionHandler)가 그런 예외들을 이미 이 메서드로 라우팅해주므로,
    // 우리는 Spring이 정한 상태코드는 그대로 두고 응답 바디만 우리 포맷으로 바꿔치기한다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        BaseErrorCode errorCode = resolve(e, statusCode);
        log.warn("Spring MVC exception: {} {} -> {}", statusCode, e.getClass().getSimpleName(), errorCode.getCode());
        log.debug("", e);
        // 상태 코드는 Spring이 원래 정한 값을 그대로 쓰고(super 호출), 바디만 CommonResponse로 교체해 넘긴다.
        return super.handleExceptionInternal(e, CommonResponse.onFail(errorCode, null), headers, statusCode, request);
    }

    // Spring MVC 표준 예외의 구체 타입 → 우리 쪽 COMMON 에러코드 매핑표.
    // Java 21 패턴 매칭 switch로 타입별 분기를 순서대로 나열한다.
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
            // 위 목록에 없는 타입은 Spring이 이미 정해준 HTTP 상태코드 값으로 한 번 더 폴백 매핑한다.
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

package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.code.BaseErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 가 던질 수 있는 에러 코드를 Swagger 에 노출한다. enum 상수에서 상태코드/코드/메시지를 직접 읽어오므로 문서와 실제 응답이 어긋나지 않는다.
 *
 * <pre>
 * &#64;ApiErrorCodes(enumClass = UserErrorCode.class, codes = {"USER_NOT_FOUNT"})
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    Class<? extends BaseErrorCode> enumClass();

    /**
     * 노출할 enum 상수 이름. 비워두면 enumClass 의 모든 상수를 노출한다.
     */
    String[] codes() default {};
}

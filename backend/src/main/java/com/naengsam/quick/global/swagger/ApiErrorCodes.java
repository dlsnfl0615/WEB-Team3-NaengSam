package com.naengsam.quick.global.swagger;

import com.naengsam.quick.global.code.BaseErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 가 던질 수 있는 에러 코드를 Swagger 에 노출한다. enum 상수에서 상태코드/코드/메시지를 직접 읽어오므로 문서와 실제 응답이 어긋나지 않는다.
 *
 * <p>여러 ErrorCode enum 을 노출하려면 애노테이션을 여러 번 붙인다.
 *
 * <pre>
 * &#64;ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"ALREADY_REGISTERED", "PHONE_NOT_VERIFIED"})
 * &#64;ApiErrorCodes(enumClass = UserErrorCode.class, codes = {"USER_NOT_FOUNT"})
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodes.ApiErrorCodesContainer.class)
public @interface ApiErrorCodes {

    Class<? extends BaseErrorCode> enumClass();

    /**
     * 노출할 enum 상수 이름. 비워두면 enumClass 의 모든 상수를 노출한다.
     */
    String[] codes() default {};

    /**
     * {@link ApiErrorCodes} 를 한 메서드에 여러 번 붙이기 위한 컨테이너. 직접 사용하지 않는다.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ApiErrorCodesContainer {

        ApiErrorCodes[] value();
    }
}

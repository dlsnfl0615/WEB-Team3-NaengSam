package com.naengsam.quick.global.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 또는 컨트롤러 클래스에 붙이면 해당 핸들러는 로그인(세션)이 있어야만 접근할 수 있다.
 * {@link LoginCheckInterceptor} 가 검사하며, 세션이 없으면
 * {@link com.naengsam.quick.global.code.GeneralErrorCode#UNAUTHORIZED} 로 거절한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
}

package com.naengsam.quick.global.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 또는 컨트롤러 클래스에 붙이면 해당 핸들러는 로그인(세션) 없이 접근할 수 있다.
 * {@code /api/**} 는 기본적으로 로그인이 필요하며, {@link LoginCheckInterceptor} 가 이 어노테이션이 붙은
 * 핸들러에 대해서만 세션 검사를 건너뛴다. (로그인·회원가입·인증문자처럼 로그인 전에 호출되는 공개 API 에 사용한다.)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}

package com.naengsam.quick.global.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 세션에 저장된 현재 로그인 사용자의 식별자(UUID)를 주입한다.
 * 세션이 없으면 {@link com.naengsam.quick.global.code.GeneralErrorCode#UNAUTHORIZED} 로 거절된다.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public MeResponse me(@LoginUser UUID boormiId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}

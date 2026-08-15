package com.naengsam.quick.global.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 세션에 저장된 현재 로그인 사용자가 관리자(Boormi.isAdmin)인지 확인한 뒤 그 식별자(UUID)를 주입한다.
 * 세션이 없으면 {@link com.naengsam.quick.domain.user.exception.AuthErrorCode#UNAUTHORIZED}, 로그인은 했지만 관리자가
 * 아니면 {@link com.naengsam.quick.domain.user.exception.AuthErrorCode#FORBIDDEN_ROLE} 로 거절된다.
 *
 * <pre>{@code
 * @PostMapping("/{dreamiId}/approve")
 * public void approve(@PathVariable UUID dreamiId, @AdminUser UUID adminId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminUser {
}

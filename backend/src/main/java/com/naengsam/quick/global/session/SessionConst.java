package com.naengsam.quick.global.session;

/**
 * 세션 속성 키 상수. 외부 라이브러리 없이 서블릿 {@link jakarta.servlet.http.HttpSession} 을 사용한다.
 */
public final class SessionConst {

    /**
     * 로그인한 사용자의 식별자(BOORMI 의 {@code boormi_id}, UUID)를 담는 세션 속성 키.
     */
    public static final String LOGIN_USER = "LOGIN_USER";
    
    private SessionConst() {
    }
}

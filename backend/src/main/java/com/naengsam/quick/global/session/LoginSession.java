package com.naengsam.quick.global.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 서블릿 {@link HttpSession} 을 감싸 로그인 세션을 감싸는 래퍼. 세션 생성/만료/쿠키 발급은 여전히 서블릿 컨테이너(톰캣)가 관리하고, 이 클래스는 문자열
 * 키({@link SessionConst})와 캐스팅을 한곳에 모아 타입 안전하게 접근하도록 한다.
 */
public class LoginSession {

    private final HttpSession session;

    private LoginSession(HttpSession session) {
        this.session = session;
    }

    /**
     * 로그인 시 사용. 세션 고정(Session Fixation) 공격을 막기 위해 기존 세션이 있으면 무효화하고 항상 새 세션을 발급한다. 로그인 이전에 발급됐던 세션
     * ID 는 인증된 세션으로 이어지지 않는다.
     */
    public static LoginSession create(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        return new LoginSession(request.getSession(true));
    }

    /**
     * 이미 존재하는 세션만 감싼다. 없으면 empty. 조회/검사 시 사용.
     */
    public static Optional<LoginSession> current(HttpServletRequest request) {
        return Optional.ofNullable(request.getSession(false)).map(LoginSession::new);
    }

    /**
     * 로그인 사용자 식별자를 세션에 저장한다.
     */
    public void login(UUID boormiId) {
        session.setAttribute(SessionConst.LOGIN_USER, boormiId);
    }

    /**
     * 세션에 저장된 로그인 사용자 식별자. 없으면 empty.
     */
    public Optional<UUID> boormiId() {
        Object value = session.getAttribute(SessionConst.LOGIN_USER);
        return value instanceof UUID boormiId ? Optional.of(boormiId) : Optional.empty();
    }

    public boolean isLoggedIn() {
        return session.getAttribute(SessionConst.LOGIN_USER) != null;
    }

    /**
     * 세션을 무효화한다(로그아웃).
     */
    public void invalidate() {
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalidated) {
        }
    }

    /**
     * 세션 생성 시각 (톰캣이 관리하는 메타데이터).
     */
    public Instant getCreatedAt() {
        return Instant.ofEpochMilli(session.getCreationTime());
    }

    /**
     * 마지막 접근 시각 (톰캣이 관리하는 메타데이터).
     */
    public Instant getLastAccessedAt() {
        return Instant.ofEpochMilli(session.getLastAccessedTime());
    }
}

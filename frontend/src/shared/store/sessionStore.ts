import { create } from "zustand";
import { api, SESSION_PROBE_HEADER } from "@/shared/api";
import type { UserDto } from "@/shared/api";
import { verifyIdentity } from "@/shared/mock/authService";
import type { AuthUser, LoginRequest, SignupRequest } from "@/shared/mock/types";

interface SessionState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** 앱 시작 시 세션 확인(bootstrap)이 끝났는지. 라우트 가드가 이 값을 기다린다. */
  hydrated: boolean;
  /** 쿠키 세션으로 /me를 조회해 로그인 상태를 복원한다(앱 시작 1회). */
  bootstrap: () => Promise<void>;
  login: (dto: LoginRequest) => Promise<AuthUser>;
  signup: (dto: SignupRequest) => Promise<AuthUser>;
  /** 로그인 유저의 본인인증(드리미 등록). 미로그인 시 null. */
  verify: () => Promise<AuthUser | null>;
  logout: () => Promise<void>;
}

/** 백엔드 UserDto → 화면용 AuthUser 매핑. */
function toAuthUser(dto: UserDto): AuthUser {
  return {
    id: dto.boormiId ?? "",
    name: dto.name ?? "",
    email: dto.email ?? "",
    // UserDto엔 역할 목록 대신 isDreami 플래그만 온다. 부르미는 항상 보유, 드리미는 등록 시 추가.
    roles: dto.isDreami ? ["부르미", "드리미"] : ["부르미"],
    // UserDto엔 평점이 없다. 평점 API 확정 전까지 0으로 둔다(마이페이지 표시용).
    rating: 0,
  };
}

/** 회원가입 폼의 생년월일("YYYY.M.D") → 백엔드 ISO 날짜("YYYY-MM-DD"). */
function toIsoDate(birth: string): string {
  const [year, month, day] = birth.split(".");
  return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
}

/**
 * 로그인 세션(유저·역할)을 화면 간 공유하는 전역 스토어(#37 인증).
 * 실제 백엔드 API(`@/shared/api`)를 호출한다. 인증은 세션 쿠키(JSESSIONID) 기반.
 */
export const useSessionStore = create<SessionState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  hydrated: false,
  bootstrap: async () => {
    // probe 헤더를 실어 401이어도 전역 로그인 리다이렉트를 유발하지 않게 한다(공개 페이지 보호).
    try {
      const { result } = await api.me({
        headers: { [SESSION_PROBE_HEADER]: "1" },
      });
      // 부트스트랩 도중 사용자가 먼저 로그인했다면(hydrated) 그 결과를 덮어쓰지 않는다.
      if (!get().hydrated) {
        set({ user: toAuthUser(result ?? {}), isAuthenticated: true });
      }
    } catch {
      if (!get().hydrated) {
        set({ user: null, isAuthenticated: false });
      }
    } finally {
      set({ hydrated: true });
    }
  },
  login: async (dto) => {
    // 로그인은 세션 쿠키만 발급(result 없음)하므로, 곧바로 /me로 사용자 정보를 조회한다.
    await api.login({ email: dto.email, password: dto.password });
    const { result } = await api.me();
    const user = toAuthUser(result ?? {});
    set({ user, isAuthenticated: true, hydrated: true });
    return user;
  },
  signup: async (dto) => {
    // 가입은 세션을 만들지 않는다(@PublicApi). 성공 후 곧바로 로그인해 세션을 생성한다.
    await api.signup({
      email: dto.email,
      password: dto.password,
      name: dto.name,
      phoneNumber: dto.phone,
      birthdate: toIsoDate(dto.birth),
    });
    return get().login({ email: dto.email, password: dto.password });
  },
  verify: async () => {
    // 본인인증(드리미 등록)은 dreami 도메인 소관 — 아직 목 유지(후속 연동).
    const { user } = get();
    if (!user) return null;
    const updated = await verifyIdentity(user);
    set({ user: updated });
    return updated;
  },
  logout: async () => {
    try {
      await api.logout();
    } finally {
      set({ user: null, isAuthenticated: false });
    }
  },
}));

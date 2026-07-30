import { create } from "zustand";
import {
  login as loginApi,
  signup as signupApi,
  verifyIdentity,
} from "@/shared/mock/authService";
import type { AuthUser, LoginRequest, SignupRequest } from "@/shared/mock/types";

interface SessionState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (dto: LoginRequest) => Promise<AuthUser>;
  signup: (dto: SignupRequest) => Promise<AuthUser>;
  /** 로그인 유저의 본인인증(드리미 등록). 미로그인 시 null. */
  verify: () => Promise<AuthUser | null>;
  logout: () => void;
}

/**
 * 로그인 세션(유저·역할)을 화면 간 공유하는 전역 스토어(#37 인증).
 * authService 목을 호출하며, 추후 Orval 클라이언트로 교체한다.
 */
export const useSessionStore = create<SessionState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  login: async (dto) => {
    const user = await loginApi(dto);
    set({ user, isAuthenticated: true });
    return user;
  },
  signup: async (dto) => {
    const user = await signupApi(dto);
    set({ user, isAuthenticated: true });
    return user;
  },
  verify: async () => {
    const { user } = get();
    if (!user) return null;
    const updated = await verifyIdentity(user);
    set({ user: updated });
    return updated;
  },
  logout: () => set({ user: null, isAuthenticated: false }),
}));

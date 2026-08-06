import { createContext } from "react";

/** 앱 사용자 역할 모드(부르미=발송인, 드리미=배송인). */
export type Role = "부르미" | "드리미";

/** 역할 전환 요청 결과. 실패 시 화면이 안내 방식을 결정한다. */
export type RoleChangeResult =
  | { ok: true }
  | {
      ok: false;
      /** 드리미 미등록·미승인 → 등록/본인인증 화면으로 유도해야 하는 실패. */
      needsRegister: boolean;
      message: string;
    };

export interface RoleContextValue {
  role: Role;
  setRole: (role: Role) => void;
  /** 서버 검증을 거쳐 역할을 바꾼다. 성공 시에만 `role`이 갱신된다. */
  requestRole: (role: Role) => Promise<RoleChangeResult>;
}

export const RoleContext = createContext<RoleContextValue | null>(null);

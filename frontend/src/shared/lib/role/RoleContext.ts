import { createContext } from "react";

/** 앱 사용자 역할 모드(부르미=발송인, 드리미=배송인). */
export type Role = "부르미" | "드리미";

export interface RoleContextValue {
  role: Role;
  setRole: (role: Role) => void;
}

export const RoleContext = createContext<RoleContextValue | null>(null);

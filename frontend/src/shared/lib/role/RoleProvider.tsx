import { useMemo, useState, type ReactNode } from "react";
import { RoleContext, type Role } from "./RoleContext";

export interface RoleProviderProps {
  children: ReactNode;
}

/** 역할 모드를 화면 간에 공유하는 프로바이더. 조립 루트(main.tsx)에서 감쌉니다. */
export function RoleProvider({ children }: RoleProviderProps) {
  const [role, setRole] = useState<Role>("부르미");
  const value = useMemo(() => ({ role, setRole }), [role]);

  return <RoleContext value={value}>{children}</RoleContext>;
}

import { useCallback, useMemo, useState, type ReactNode } from "react";
import { api, isApiError } from "@/shared/api";
import { useSessionStore } from "@/shared/store/sessionStore";
import { RoleContext, type Role, type RoleChangeResult } from "./RoleContext";

export interface RoleProviderProps {
  children: ReactNode;
}

/** 드리미 등록/승인 전이라 본인인증 화면으로 유도해야 하는 오류 코드. */
const NEEDS_REGISTER_CODES = ["USER_003", "USER_004"];

/**
 * 역할 보관 키. 새로고침·직접 URL 진입에도 역할이 유지돼야 한다
 * (드리미로 `/matching`에 다시 들어왔을 때 온라인 전환이 실행되지 않는 문제).
 * 탭을 닫으면 사라지도록 sessionStorage를 쓴다.
 */
const ROLE_STORAGE_KEY = "naengsam.role";

function readStoredRole(): Role {
  return sessionStorage.getItem(ROLE_STORAGE_KEY) === "드리미"
    ? "드리미"
    : "부르미";
}

/** 역할 모드를 화면 간에 공유하는 프로바이더. 조립 루트(main.tsx)에서 감쌉니다. */
export function RoleProvider({ children }: RoleProviderProps) {
  const [storedRole, setRoleState] = useState<Role>(readStoredRole);

  const setRole = useCallback((next: Role) => {
    sessionStorage.setItem(ROLE_STORAGE_KEY, next);
    setRoleState(next);
  }, []);

  // 서버가 수행 중인 역할을 알려주면 그것이 진실이다 — 새로고침·직접 URL 진입으로 sessionStorage가
  // 실제 상태와 어긋나 있어도 화면은 수행 중인 역할을 따른다. 수행 중인 것이 없으면(activeRole 없음)
  // 사용자가 마지막으로 고른 역할을 유지한다.
  const activeRole = useSessionStore((s) => s.user?.activeRole);
  const role: Role = activeRole
    ? activeRole === "DREAMI"
      ? "드리미"
      : "부르미"
    : storedRole;

  // 양방향 모두 서버가 검증한다(GET /api/v1/user/role?target=…). 매칭·배달이 진행 중이면
  // 드리미→부르미 복귀도 막아야 하므로, 가려는 역할을 target으로 실어 보낸다.
  const requestRole = useCallback(
    async (next: Role): Promise<RoleChangeResult> => {
      try {
        await api.changeRole({ target: next === "드리미" ? "DREAMI" : "BOORMI" });
        setRole(next);
        return { ok: true };
      } catch (e) {
        const code = isApiError(e) ? e.code : undefined;
        return {
          ok: false,
          needsRegister: NEEDS_REGISTER_CODES.includes(code ?? ""),
          message: isApiError(e) ? e.message : "역할 전환에 실패했어요.",
        };
      }
    },
    [setRole],
  );

  const value = useMemo(
    () => ({ role, setRole, requestRole }),
    [role, setRole, requestRole],
  );

  return <RoleContext value={value}>{children}</RoleContext>;
}

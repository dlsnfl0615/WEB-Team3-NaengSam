import { use } from "react";
import { RoleContext } from "./RoleContext";

/** 현재 역할 모드와 변경 함수를 반환합니다. RoleProvider 안에서만 사용하세요. */
export function useRole() {
  const value = use(RoleContext);
  if (!value)
    throw new Error("useRole은 RoleProvider 안에서만 사용할 수 있습니다.");
  return value;
}

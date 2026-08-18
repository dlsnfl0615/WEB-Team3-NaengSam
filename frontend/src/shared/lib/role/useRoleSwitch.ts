import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useRole } from "./useRole";
import type { Role } from "./RoleContext";

/**
 * 역할 토글(SegmentedToggle)용 공통 핸들러.
 *
 * 드리미 전환은 서버 검증을 거치므로 실패할 수 있다.
 * - 이미 신청해서 심사 대기 중(REQUESTED) → 접수 안내 화면으로 유도(재제출 버튼은 그 화면이 제공)
 * - 그 외 미등록·미승인 → 본인인증 화면으로 유도
 * - 그 외 실패(수행 중인 주문 등) → `error` 메시지를 화면에 노출
 *
 * 검증이 끝날 때까지 `pending`으로 토글을 잠가 중복 요청을 막는다.
 */
export function useRoleSwitch() {
  const navigate = useNavigate();
  const { requestRole } = useRole();
  const dreamiStatus = useSessionStore((s) => s.user?.dreamiStatus);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onRoleChange = (value: string) => {
    if (pending) return;
    setPending(true);
    setError(null);
    void requestRole(value as Role)
      .then((result) => {
        if (result.ok) return;
        if (result.needsRegister) {
          navigate(
            dreamiStatus === "REQUESTED" ? ROUTES.dreamiPending : ROUTES.verify,
          );
          return;
        }
        setError(result.message);
      })
      .finally(() => setPending(false));
  };

  return { onRoleChange, pending, error };
}

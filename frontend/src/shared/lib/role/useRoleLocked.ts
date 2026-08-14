import { useSessionStore } from "@/shared/store/sessionStore";

/**
 * 진행 중인 매칭·배달이 있으면 역할 토글을 잠그고 사유를 함께 돌려준다.
 *
 * 판정 근거는 서버가 `/me`로 내려주는 activeRole이다(주문 테이블 + 매칭엔진 인메모리 상태를 합친 값).
 * 토글이 한 박자 늦게 잠기더라도 전환 요청 자체는 서버가 다시 검사하므로 잘못된 전환은 통과하지 못한다.
 */
export function useRoleLocked(): { locked: boolean; reason: string | null } {
  const activeRole = useSessionStore((s) => s.user?.activeRole);
  const activeOrderCd = useSessionStore((s) => s.user?.activeOrderCd);

  if (!activeRole) return { locked: false, reason: null };

  return { locked: true, reason: lockReason(activeRole, activeOrderCd) };
}

function lockReason(
  activeRole: "BOORMI" | "DREAMI",
  activeOrderCd: string | undefined,
): string {
  // 주문이 없는데 활성인 경우는 드리미가 오퍼를 기다리는 중뿐이다. 오프라인으로 풀 수 있으므로 안내를 구분한다.
  if (!activeOrderCd) {
    return "매칭 대기 중에는 전환할 수 없어요. 먼저 오프라인으로 전환해주세요.";
  }
  if (activeOrderCd === "IN_PROGRESS") {
    return "배달이 진행 중이라 전환할 수 없어요.";
  }
  if (activeOrderCd === "PENDING_BOORMI_CONFIRMATION") {
    return activeRole === "DREAMI"
      ? "부르미 확인을 기다리는 중이라 전환할 수 없어요."
      : "드리미 확인이 남아 있어 전환할 수 없어요.";
  }
  return "매칭 중인 주문이 있어 전환할 수 없어요.";
}

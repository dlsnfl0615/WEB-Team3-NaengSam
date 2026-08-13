import { useEffect, useState } from "react";
import { api, type DeliveryCompletionDto } from "@/shared/api";

/**
 * GET /api/v1/delivery/orders/{orderId}/completion 을 조회한다. 완료 화면(DeliveryCompleteScreen)과
 * 달리 시점 제한 없이 orderId만으로 조회되므로, 과거 활동 내역 상세(활동 탭)에서도 그대로 재사용한다.
 * 조회 실패(예: 픽업 전 취소돼 Delivery 레코드가 없는 주문)해도 참고용 부가 정보라 조용히 null로 둔다.
 */
export function useDeliveryCompletion(
  orderId: string | null,
): DeliveryCompletionDto | null {
  // orderId를 상태에 같이 들고 있다가, 조회 중인 orderId와 다르면(즉 아직 안 갱신됐으면)
  // null을 반환한다 — effect 본문에서 동기적으로 setState해 리셋하지 않고도 이전 주문의
  // 데이터가 새 orderId에 잠깐 노출되는 걸 막는다.
  const [state, setState] = useState<{
    orderId: string | null;
    detail: DeliveryCompletionDto | null;
  }>({ orderId: null, detail: null });

  useEffect(() => {
    if (!orderId) return;
    let cancelled = false;
    void api
      .getDeliveryCompletion(orderId)
      .then(({ result }) => {
        if (!cancelled && result) setState({ orderId, detail: result });
      })
      .catch(() => {
        // 참고용 부가 정보라 실패해도 화면은 기본 정보로 그대로 둔다.
      });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  return state.orderId === orderId ? state.detail : null;
}

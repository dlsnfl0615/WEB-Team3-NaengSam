import { useEffect, useState } from "react";
import { api, type ReviewDto } from "@/shared/api";

export interface ReceivedReviewState {
  review: ReviewDto | null;
  /** 조회가 아직 끝나지 않았다("리뷰 없음"과 구분용). */
  loading: boolean;
}

/**
 * GET /api/v1/orders/{orderId}/review/received 로 상대방이 나에게 남긴 리뷰를 조회한다.
 * 아직 리뷰가 없어도 정상 상태(result: null)이고, 조회 자체가 실패해도 참고용 정보라
 * "리뷰 없음"과 동일하게 취급한다(화면을 막지 않는다).
 */
export function useReceivedReview(orderId: string | null): ReceivedReviewState {
  const [state, setState] = useState<{
    orderId: string | null;
    review: ReviewDto | null;
  }>({ orderId: null, review: null });

  useEffect(() => {
    if (!orderId) return;
    let cancelled = false;
    void api
      .getReceivedReview(orderId)
      .then(({ result }) => {
        if (!cancelled) setState({ orderId, review: result ?? null });
      })
      .catch(() => {
        if (!cancelled) setState({ orderId, review: null });
      });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  const loading = state.orderId !== orderId;
  return { review: loading ? null : state.review, loading };
}

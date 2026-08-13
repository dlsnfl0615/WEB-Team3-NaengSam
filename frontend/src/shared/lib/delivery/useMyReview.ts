import { useEffect, useState } from "react";
import { api, type ReviewDto } from "@/shared/api";

export interface MyReviewState {
  review: ReviewDto | null;
  /** 조회가 아직 끝나지 않았다("리뷰 없음"과 구분용). */
  loading: boolean;
}

/**
 * GET /api/v1/orders/{orderId}/review 로 내가 이 주문에 쓴 리뷰를 조회한다. 아직 별점을 안
 * 남겼으면 백엔드가 REVIEW_NOT_FOUND 예외를 던지는데(getReceivedReview와 달리 null이 아님),
 * "리뷰 없음"과 동일하게 취급해 화면을 막지 않는다.
 */
export function useMyReview(orderId: string | null): MyReviewState {
  const [state, setState] = useState<{
    orderId: string | null;
    review: ReviewDto | null;
  }>({ orderId: null, review: null });

  useEffect(() => {
    if (!orderId) return;
    let cancelled = false;
    void api
      .getMyReview(orderId)
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

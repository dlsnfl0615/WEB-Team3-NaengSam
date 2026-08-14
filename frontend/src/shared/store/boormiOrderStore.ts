import { useEffect, useState } from "react";
import { create } from "zustand";
import { api, isApiError, type OrderRequest } from "@/shared/api";
import { toBoormiOrder, type BoormiOrder } from "./boormiOrderAdapter";

interface BoormiOrderState {
  orders: BoormiOrder[];
  loading: boolean;
  error: string | null;
  /** 전체 조회(orders 교체). */
  load: () => Promise<void>;
  /** 부름 등록 → 생성된 orderId 반환. */
  createOrder: (req: OrderRequest) => Promise<string>;
  /** 부름 취소 → 로컬 목록에서 제거. */
  cancelOrder: (orderId: string) => Promise<void>;
}

/**
 * 부르미 주문(콜) 전역 스토어. 홈("진행 중인 부름")·활동 화면이 함께 구독한다.
 *
 * 활동 탭 필터(전체/진행중/완료/취소)가 클라이언트에서 orderCd를 그룹핑하는 방식이라, 백엔드도
 * 페이지네이션 없이 전체를 한 번에 내려준다(지금 규모에서는 이게 페이지네이션+서버 필터링보다
 * 단순하고 충분하다).
 */
export const useBoormiOrderStore = create<BoormiOrderState>((set) => ({
  orders: [],
  loading: false,
  error: null,

  load: async () => {
    set({ loading: true, error: null });
    try {
      const { result } = await api.getBoormiOrders();
      set({ orders: (result?.orders ?? []).map(toBoormiOrder), loading: false });
    } catch (e) {
      set({
        loading: false,
        error: isApiError(e) ? e.message : "콜 목록을 불러오지 못했어요.",
      });
    }
  },

  createOrder: async (req) => {
    const { result } = await api.subscribeOrder(req);
    return result ?? "";
  },

  cancelOrder: async (orderId) => {
    await api.unsubscribeOrder(orderId);
    set((s) => ({ orders: s.orders.filter((o) => o.id !== orderId) }));
  },
}));

export interface BoormiOrderByIdResult {
  order: BoormiOrder | null;
  loading: boolean;
}

/**
 * 주문 하나를 id로 직접 조회한다(전용 API: getBoormiOrder). 활동 내역 상세 화면이 목록 로딩과
 * 무관하게 딥링크/새로고침으로 바로 들어와도 그 주문 하나를 정확히 찾을 수 있게, 스토어를 아예
 * 안 보고 항상 이 API로 조회한다.
 */
export function useBoormiOrderById(id: string | null): BoormiOrderByIdResult {
  // id를 상태에 같이 들고 있다가, 조회 중인 id와 다르면(아직 응답 전이면) loading으로 취급한다 —
  // effect 본문에서 동기적으로 setState해 로딩 플래그를 켜지 않고도 이전 주문 데이터가
  // 새 id에 잠깐 노출되는 걸 막는다(useDeliveryCompletion과 동일한 패턴).
  const [state, setState] = useState<{ id: string | null; order: BoormiOrder | null }>(
    { id: null, order: null },
  );

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api
      .getBoormiOrder(id)
      .then(({ result }) => {
        if (!cancelled) setState({ id, order: result ? toBoormiOrder(result) : null });
      })
      .catch(() => {
        if (!cancelled) setState({ id, order: null });
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (!id) return { order: null, loading: false };
  const loading = state.id !== id;
  return { order: loading ? null : state.order, loading };
}

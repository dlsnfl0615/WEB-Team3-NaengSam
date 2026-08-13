import { useEffect, useState } from "react";
import { create } from "zustand";
import { api, isApiError, type OrderRequest } from "@/shared/api";
import { toBoormiOrder, type BoormiOrder } from "./boormiOrderAdapter";

/** 한 번에 불러올 주문 수. */
const PAGE_SIZE = 20;

interface BoormiOrderState {
  orders: BoormiOrder[];
  nextCursor?: string;
  hasNext: boolean;
  loading: boolean;
  /** 다음 페이지 로딩 중(더 보기 버튼 상태). */
  loadingMore: boolean;
  error: string | null;
  /** 첫 페이지 조회(orders 교체). */
  load: () => Promise<void>;
  /** nextCursor로 다음 페이지 append. */
  loadMore: () => Promise<void>;
  /** 부름 등록 → 생성된 orderId 반환. */
  createOrder: (req: OrderRequest) => Promise<string>;
  /** 부름 취소 → 로컬 목록에서 제거. */
  cancelOrder: (orderId: string) => Promise<void>;
}

/**
 * 부르미 주문(콜) 전역 스토어. getBoormiOrders 커서 페이지네이션 결과를 담고
 * 홈("진행 중인 부름")·활동 화면이 함께 구독한다.
 *
 * 필터링은 클라이언트에서 orderCd를 그룹핑(전체/진행중/완료/취소)하므로
 * 목록은 status 미지정(=전체)으로 조회한다. 백엔드의 단일 status 필터는
 * 추후 단일 상태 최적화가 필요할 때 활용한다.
 */
export const useBoormiOrderStore = create<BoormiOrderState>((set, get) => ({
  orders: [],
  nextCursor: undefined,
  hasNext: false,
  loading: false,
  loadingMore: false,
  error: null,

  load: async () => {
    set({ loading: true, error: null });
    try {
      const { result } = await api.getBoormiOrders({ size: PAGE_SIZE });
      set({
        orders: (result?.orders ?? []).map(toBoormiOrder),
        nextCursor: result?.nextCursor,
        hasNext: result?.hasNext ?? false,
        loading: false,
      });
    } catch (e) {
      set({
        loading: false,
        error: isApiError(e) ? e.message : "콜 목록을 불러오지 못했어요.",
      });
    }
  },

  loadMore: async () => {
    const { hasNext, nextCursor, loadingMore } = get();
    if (!hasNext || !nextCursor || loadingMore) return;
    set({ loadingMore: true, error: null });
    try {
      const { result } = await api.getBoormiOrders({
        cursor: nextCursor,
        size: PAGE_SIZE,
      });
      set((s) => ({
        orders: [...s.orders, ...(result?.orders ?? []).map(toBoormiOrder)],
        nextCursor: result?.nextCursor,
        hasNext: result?.hasNext ?? false,
        loadingMore: false,
      }));
    } catch (e) {
      set({
        loadingMore: false,
        error: isApiError(e) ? e.message : "콜을 더 불러오지 못했어요.",
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
 * 주문 하나를 id로 직접 조회한다(전용 API: getBoormiOrder). 목록 스토어의 orders 배열은 커서
 * 페이지네이션으로 최근 것부터 일부만 들고 있어서, 활동 내역 상세 화면이 목록을 거치지 않고
 * 새로고침/딥링크로 바로 들어오면 그 시점에 스토어가 비어있거나(또는 최근 20건 안에 이 주문이
 * 없어서) 못 찾을 수 있었다 — 그래서 스토어를 아예 안 보고 항상 이 API로 그 주문 하나만 조회한다.
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

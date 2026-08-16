import { useEffect, useState } from "react";
import { create } from "zustand";
import { api, isApiError, type OrderRequest } from "@/shared/api";
import {
  FILTER_ORDER_CDS,
  toBoormiOrder,
  toFilterCounts,
  type ActivityFilter,
  type BoormiOrder,
  type FilterCounts,
} from "./boormiOrderAdapter";

/** 홈·매칭 화면이 보는 "진행 중인 부름"은 몇 건이든 한 페이지 안에 다 들어오도록 넉넉히 받는다
 * (부르미 동시 진행 주문은 MAX_ACTIVE_ORDERS로 이미 작게 캡되어 있다 — 서버 페이지 크기 상한과 동일). */
const ONGOING_FETCH_SIZE = 50;

interface BoormiOrderState {
  /** 진행 중인 주문만(홈 "진행 중인 부름"용). 활동 내역 목록과는 별개 상태다. */
  orders: BoormiOrder[];
  loading: boolean;
  error: string | null;
  /** 진행 중인 주문 다시 조회(orders 교체). */
  load: () => Promise<void>;
  /** 부름 등록 → 생성된 orderId 반환. */
  createOrder: (req: OrderRequest) => Promise<string>;
  /** 부름 취소 → 로컬 목록에서 제거. */
  cancelOrder: (orderId: string) => Promise<void>;

  // ---- 활동 내역 화면 전용(무한 스크롤 + 필터) ----
  activityOrders: BoormiOrder[];
  activityFilter: ActivityFilter;
  activityCursor: string | null;
  activityHasNext: boolean;
  /** 첫 페이지(필터 전환 포함) 로딩. */
  activityLoading: boolean;
  /** 스크롤로 다음 페이지를 이어 받는 중. */
  activityLoadingMore: boolean;
  activityError: string | null;
  /** 탭별(전체/진행중/완료/취소) 개수. 화면 진입 시 한 번만 받아온다. */
  activityCounts: FilterCounts | null;
  /** 필터 탭을 정하고 그 필터의 첫 페이지를 새로 받는다(기존 목록은 버린다). */
  loadActivityFirstPage: (filter: ActivityFilter) => Promise<void>;
  /** 현재 필터의 다음 페이지를 이어 받는다. 이미 없거나(hasNext=false) 로딩 중이면 아무것도 안 한다. */
  loadActivityMore: () => Promise<void>;
  /** 탭별 개수 갱신. */
  loadActivityCounts: () => Promise<void>;
}

/**
 * 부르미 주문(콜) 전역 스토어.
 *
 * `orders`/`load`는 홈("진행 중인 부름")·매칭 화면이 구독하는 "지금 진행 중인 주문"만을 위한 것이고,
 * `activity*` 상태는 활동 내역 화면의 커서 기반 무한 스크롤 목록을 위한 것이다 — 서로 다른 화면의
 * 서로 다른 필요(진행 중인 것 몇 건 vs 전 기간 이력 페이지네이션)라 상태를 분리해 뒀다.
 */
export const useBoormiOrderStore = create<BoormiOrderState>((set, get) => ({
  orders: [],
  loading: false,
  error: null,

  load: async () => {
    set({ loading: true, error: null });
    try {
      const { result } = await api.getBoormiOrders({
        status: FILTER_ORDER_CDS.진행중,
        size: ONGOING_FETCH_SIZE,
      });
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

  activityOrders: [],
  activityFilter: "전체",
  activityCursor: null,
  activityHasNext: false,
  activityLoading: false,
  activityLoadingMore: false,
  activityError: null,
  activityCounts: null,

  loadActivityFirstPage: async (filter) => {
    set({ activityLoading: true, activityError: null, activityFilter: filter });
    try {
      const status = filter === "전체" ? undefined : FILTER_ORDER_CDS[filter];
      const { result } = await api.getBoormiOrders({ status });
      set({
        activityOrders: (result?.orders ?? []).map(toBoormiOrder),
        activityCursor: result?.nextCursor ?? null,
        activityHasNext: result?.hasNext ?? false,
        activityLoading: false,
      });
    } catch (e) {
      set({
        activityLoading: false,
        activityError: isApiError(e) ? e.message : "활동 내역을 불러오지 못했어요.",
      });
    }
  },

  loadActivityMore: async () => {
    const { activityHasNext, activityLoadingMore, activityLoading, activityCursor, activityFilter } = get();
    if (!activityHasNext || activityLoadingMore || activityLoading) return;
    set({ activityLoadingMore: true });
    try {
      const status = activityFilter === "전체" ? undefined : FILTER_ORDER_CDS[activityFilter];
      const { result } = await api.getBoormiOrders({ status, cursor: activityCursor ?? undefined });
      set((s) => ({
        activityOrders: [...s.activityOrders, ...(result?.orders ?? []).map(toBoormiOrder)],
        activityCursor: result?.nextCursor ?? null,
        activityHasNext: result?.hasNext ?? false,
        activityLoadingMore: false,
      }));
    } catch (e) {
      set({
        activityLoadingMore: false,
        activityError: isApiError(e) ? e.message : "활동 내역을 불러오지 못했어요.",
      });
    }
  },

  loadActivityCounts: async () => {
    try {
      const { result } = await api.getBoormiOrderStatusCounts();
      set({ activityCounts: toFilterCounts(result ?? []) });
    } catch {
      // 탭 개수는 보조 지표라 실패해도 목록 자체는 계속 보여준다.
    }
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

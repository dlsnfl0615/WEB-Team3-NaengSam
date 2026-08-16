import { useEffect, useState } from "react";
import { create } from "zustand";
import { api, isApiError } from "@/shared/api";
import {
  FILTER_ORDER_CDS,
  toBoormiOrder,
  toFilterCounts,
  type ActivityFilter,
  type BoormiOrder,
  type FilterCounts,
} from "./boormiOrderAdapter";

interface DreamiOrderState {
  deliveries: BoormiOrder[];
  filter: ActivityFilter;
  cursor: string | null;
  hasNext: boolean;
  /** 첫 페이지(필터 전환 포함) 로딩. */
  loading: boolean;
  /** 스크롤로 다음 페이지를 이어 받는 중. */
  loadingMore: boolean;
  error: string | null;
  /** 탭별(전체/진행중/완료/취소) 개수. 화면 진입 시 한 번만 받아온다. */
  counts: FilterCounts | null;
  /** 필터 탭을 정하고 그 필터의 첫 페이지를 새로 받는다(기존 목록은 버린다). */
  load: (filter: ActivityFilter) => Promise<void>;
  /** 현재 필터의 다음 페이지를 이어 받는다. 이미 없거나(hasNext=false) 로딩 중이면 아무것도 안 한다. */
  loadMore: () => Promise<void>;
  /** 탭별 개수 갱신. */
  loadCounts: () => Promise<void>;
}

/**
 * 드리미 활동 내역 전역 스토어. 커서 기반 무한 스크롤 목록 + 필터 탭 상태를 담는다.
 * 필터 탭 하나(예: "진행중")가 여러 orderCd를 묶은 경우 {@link FILTER_ORDER_CDS}로 구체적인
 * 상태 목록을 만들어 서버에 넘긴다.
 */
export const useDreamiOrderStore = create<DreamiOrderState>((set, get) => ({
  deliveries: [],
  filter: "전체",
  cursor: null,
  hasNext: false,
  loading: false,
  loadingMore: false,
  error: null,
  counts: null,

  load: async (filter) => {
    set({ loading: true, error: null, filter });
    try {
      const status = filter === "전체" ? undefined : FILTER_ORDER_CDS[filter];
      const { result } = await api.getDreamiOrders({ status });
      set({
        deliveries: (result?.orders ?? []).map(toBoormiOrder),
        cursor: result?.nextCursor ?? null,
        hasNext: result?.hasNext ?? false,
        loading: false,
      });
    } catch (e) {
      set({
        loading: false,
        error: isApiError(e) ? e.message : "활동 내역을 불러오지 못했어요.",
      });
    }
  },

  loadMore: async () => {
    const { hasNext, loadingMore, loading, cursor, filter } = get();
    if (!hasNext || loadingMore || loading) return;
    set({ loadingMore: true });
    try {
      const status = filter === "전체" ? undefined : FILTER_ORDER_CDS[filter];
      const { result } = await api.getDreamiOrders({ status, cursor: cursor ?? undefined });
      set((s) => ({
        deliveries: [...s.deliveries, ...(result?.orders ?? []).map(toBoormiOrder)],
        cursor: result?.nextCursor ?? null,
        hasNext: result?.hasNext ?? false,
        loadingMore: false,
      }));
    } catch (e) {
      set({
        loadingMore: false,
        error: isApiError(e) ? e.message : "활동 내역을 불러오지 못했어요.",
      });
    }
  },

  loadCounts: async () => {
    try {
      const { result } = await api.getDreamiOrderStatusCounts();
      set({ counts: toFilterCounts(result ?? []) });
    } catch {
      // 탭 개수는 보조 지표라 실패해도 목록 자체는 계속 보여준다.
    }
  },
}));

export interface DreamiOrderByIdResult {
  order: BoormiOrder | null;
  loading: boolean;
}

/**
 * 배달 하나를 주문 id로 직접 조회한다(전용 API: getDreamiOrder). 활동 내역 상세 화면이 목록
 * 로딩과 무관하게 딥링크/새로고침으로 바로 들어와도 그 배달 하나를 정확히 찾을 수 있게, 스토어를
 * 아예 안 보고 항상 이 API로 조회한다.
 */
export function useDreamiOrderById(id: string | null): DreamiOrderByIdResult {
  // id를 상태에 같이 들고 있다가, 조회 중인 id와 다르면(아직 응답 전이면) loading으로 취급한다 —
  // effect 본문에서 동기적으로 setState해 로딩 플래그를 켜지 않고도 이전 배달 데이터가
  // 새 id에 잠깐 노출되는 걸 막는다(useDeliveryCompletion과 동일한 패턴).
  const [state, setState] = useState<{ id: string | null; order: BoormiOrder | null }>(
    { id: null, order: null },
  );

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api
      .getDreamiOrder(id)
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

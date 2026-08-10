import { create } from "zustand";
import { api, isApiError } from "@/shared/api";
import { toBoormiOrder, type BoormiOrder } from "./boormiOrderAdapter";

/** 한 번에 불러올 배달 수. */
const PAGE_SIZE = 20;

interface DreamiOrderState {
  deliveries: BoormiOrder[];
  nextCursor?: string;
  hasNext: boolean;
  loading: boolean;
  /** 다음 페이지 로딩 중(더 보기 버튼 상태). */
  loadingMore: boolean;
  error: string | null;
  /** 첫 페이지 조회(deliveries 교체). */
  load: () => Promise<void>;
  /** nextCursor로 다음 페이지 append. */
  loadMore: () => Promise<void>;
}

/**
 * 드리미 활동 내역 전역 스토어. getDreamiOrders 커서 페이지네이션 결과를 담는다.
 * 필터링은 클라이언트에서 orderCd를 그룹핑(전체/진행중/완료/취소)하므로 항상 전체를 조회한다.
 */
export const useDreamiOrderStore = create<DreamiOrderState>((set, get) => ({
  deliveries: [],
  nextCursor: undefined,
  hasNext: false,
  loading: false,
  loadingMore: false,
  error: null,

  load: async () => {
    set({ loading: true, error: null });
    try {
      const { result } = await api.getDreamiOrders({ size: PAGE_SIZE });
      set({
        deliveries: (result?.orders ?? []).map(toBoormiOrder),
        nextCursor: result?.nextCursor,
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
    const { hasNext, nextCursor, loadingMore } = get();
    if (!hasNext || !nextCursor || loadingMore) return;
    set({ loadingMore: true, error: null });
    try {
      const { result } = await api.getDreamiOrders({
        cursor: nextCursor,
        size: PAGE_SIZE,
      });
      set((s) => ({
        deliveries: [
          ...s.deliveries,
          ...(result?.orders ?? []).map(toBoormiOrder),
        ],
        nextCursor: result?.nextCursor,
        hasNext: result?.hasNext ?? false,
        loadingMore: false,
      }));
    } catch (e) {
      set({
        loadingMore: false,
        error: isApiError(e) ? e.message : "활동 내역을 더 불러오지 못했어요.",
      });
    }
  },
}));

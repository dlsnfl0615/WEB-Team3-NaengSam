import { create } from "zustand";

/** 배달 진행 상태(드리미·부르미 공용). */
export type DeliveryStatus = "픽업중" | "배송중" | "지연";

interface DeliveryState {
  status: DeliveryStatus;
  setStatus: (status: DeliveryStatus) => void;
  /** 픽업중 → 배송중 */
  advance: () => void;
  /** 픽업중으로 초기화(콜 수락 시) */
  reset: () => void;
}

/**
 * 배달 진행 상태를 화면 간 공유하는 전역 스토어.
 * 드리미(배달 진행)와 부르미(드림 상세)가 같은 상태를 구독한다. URL에 노출하지 않는다.
 */
export const useDeliveryStore = create<DeliveryState>((set) => ({
  status: "픽업중",
  setStatus: (status) => set({ status }),
  advance: () =>
    set((s) => ({ status: s.status === "픽업중" ? "배송중" : s.status })),
  reset: () => set({ status: "픽업중" }),
}));

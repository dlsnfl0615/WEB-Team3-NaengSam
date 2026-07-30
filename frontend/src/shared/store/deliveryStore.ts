import { create } from "zustand";
import { createDelivery } from "@/shared/mock/requestService";
import { acceptCall as acceptCallApi } from "@/shared/mock/matchingService";
import { SEED_DELIVERIES } from "@/shared/mock/seed";
import type {
  Call,
  CreateDeliveryRequest,
  Delivery,
} from "@/shared/mock/types";

/** 배달 진행 상태(레거시, 진행 화면 임시 호환). C5에서 활성 배달 구독으로 대체. */
export type DeliveryStatus = "픽업중" | "배송중" | "지연";

interface DeliveryState {
  // ── 레거시(진행 화면 임시 호환) ──
  status: DeliveryStatus;
  setStatus: (status: DeliveryStatus) => void;
  /** 픽업중 → 배송중 */
  advance: () => void;
  /** 픽업중으로 초기화(콜 수락 시) */
  reset: () => void;

  // ── 배달 목록/생성 ──
  deliveries: Delivery[];
  /** 현재 진행 중인 배달 id(진행 화면 구독 대상). */
  activeId: string | null;
  /** 부름 등록 → "매칭중" 배달 생성 후 활성 배달로 지정. */
  createRequest: (dto: CreateDeliveryRequest) => Promise<Delivery>;
  /** 드리미 콜 수락 → "픽업중" 배달 생성 후 활성 배달로 지정. */
  acceptCall: (call: Call) => Promise<Delivery>;
}

/**
 * 배달 상태를 화면 간 공유하는 전역 스토어.
 * 배달 목록(활동·수익 파생 소스)과 진행 라이프사이클을 함께 관리한다. URL에 노출하지 않는다.
 */
export const useDeliveryStore = create<DeliveryState>((set) => ({
  status: "픽업중",
  setStatus: (status) => set({ status }),
  advance: () =>
    set((s) => ({ status: s.status === "픽업중" ? "배송중" : s.status })),
  reset: () => set({ status: "픽업중" }),

  deliveries: SEED_DELIVERIES,
  activeId: null,
  createRequest: async (dto) => {
    const delivery = await createDelivery(dto);
    set((s) => ({
      deliveries: [delivery, ...s.deliveries],
      activeId: delivery.id,
    }));
    return delivery;
  },
  acceptCall: async (call) => {
    const delivery = await acceptCallApi(call);
    set((s) => ({
      deliveries: [delivery, ...s.deliveries],
      activeId: delivery.id,
      status: "픽업중", // 레거시 호환(track/detail 화면)
    }));
    return delivery;
  },
}));

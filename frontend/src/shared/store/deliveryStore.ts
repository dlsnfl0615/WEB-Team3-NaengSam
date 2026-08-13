import { create } from "zustand";
import { createDelivery } from "@/shared/mock/requestService";
import {
  advanceDelivery as advanceApi,
  cancelDelivery as cancelApi,
  completeDelivery as completeApi,
} from "@/shared/mock/deliveryService";
import { SEED_DELIVERIES } from "@/shared/mock/seed";
import type { CreateDeliveryRequest, Delivery } from "@/shared/mock/types";

/** 진행 화면(track/detail statuses.ts)이 다루는 축약 상태. */
export type DeliveryStatus = "픽업중" | "배송중" | "지연";

interface DeliveryState {
  deliveries: Delivery[];
  /** 현재 진행 중인 배달 id(진행 화면 구독 대상). */
  activeId: string | null;
  /** 부름 등록 → "매칭중" 배달 생성 후 활성 배달로 지정. */
  createRequest: (dto: CreateDeliveryRequest) => Promise<Delivery>;
  /** 활성 배달 픽업중 → 배송중. */
  advance: () => Promise<void>;
  /** 활성 배달 → 완료. */
  complete: () => Promise<void>;
  /** 활성 배달 취소/사고(사유에 "사고" 포함 시 사고). */
  cancel: (reason: string) => Promise<void>;
  /** 진행 화면이 구독할 활성 배달을 지정. */
  setActive: (id: string) => void;
}

/**
 * 배달 상태를 화면 간 공유하는 전역 스토어.
 * 배달 목록(활동·수익 파생 소스)과 진행 라이프사이클을 함께 관리한다. URL에 노출하지 않는다.
 */
export const useDeliveryStore = create<DeliveryState>((set, get) => ({
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
  advance: async () => {
    const { activeId } = get();
    if (!activeId) return;
    await advanceApi(activeId);
    set((s) => ({
      deliveries: s.deliveries.map((d) =>
        d.id === activeId && d.status === "픽업중"
          ? { ...d, status: "배송중", note: "배송 이동 중" }
          : d,
      ),
    }));
  },
  complete: async () => {
    const { activeId } = get();
    if (!activeId) return;
    await completeApi(activeId);
    set((s) => ({
      deliveries: s.deliveries.map((d) =>
        d.id === activeId ? { ...d, status: "완료", note: "배송 완료" } : d,
      ),
    }));
  },
  cancel: async (reason) => {
    const { activeId } = get();
    if (!activeId) return;
    await cancelApi(activeId, reason);
    const status: Delivery["status"] = reason.includes("사고")
      ? "사고"
      : "취소";
    set((s) => ({
      deliveries: s.deliveries.map((d) =>
        d.id === activeId ? { ...d, status, note: reason } : d,
      ),
    }));
  },
  setActive: (id) => set({ activeId: id }),
}));

/** 현재 진행 중인 활성 배달(없으면 null). */
export function useActiveDelivery(): Delivery | null {
  return useDeliveryStore(
    (s) => s.deliveries.find((d) => d.id === s.activeId) ?? null,
  );
}

/** 진행 중으로 간주하는 상태(역할 전환 잠금 기준). */
const IN_PROGRESS = new Set<string>(["매칭중", "픽업중", "배송중"]);

/** 활성 배달이 진행 중이면 true(역할 전환을 막는다). */
export function useRoleLocked(): boolean {
  return useDeliveryStore((s) => {
    const active = s.deliveries.find((d) => d.id === s.activeId);
    return !!active && IN_PROGRESS.has(active.status);
  });
}

/** id로 배달 1건 조회(없으면 null). */
export function useDeliveryById(id: string | null): Delivery | null {
  return useDeliveryStore((s) =>
    id ? (s.deliveries.find((d) => d.id === id) ?? null) : null,
  );
}

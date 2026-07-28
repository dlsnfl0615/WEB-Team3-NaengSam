import type { DeliveryStatus } from "@/shared/store/deliveryStore";

/** 드림 상세 화면이 다루는 배달 상태(전역 스토어에서 구독). */
export type DetailStatus = DeliveryStatus;

export const DETAIL_STATUSES: Record<
  DetailStatus,
  { title: string; showEta: boolean; cancelable: boolean }
> = {
  픽업중: {
    title: "물품을 픽업 중이에요",
    showEta: true,
    cancelable: true,
  },
  배송중: {
    title: "물품을 드림 중이에요",
    showEta: true,
    cancelable: false,
  },
  지연: {
    title: "물품배송이 지연되고 있어요",
    showEta: false,
    cancelable: false,
  },
};

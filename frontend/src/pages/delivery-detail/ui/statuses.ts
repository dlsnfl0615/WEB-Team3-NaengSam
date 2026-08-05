import type { DeliveryStatus } from "@/shared/store/deliveryStore";
import { DeliveryStatusResponseDtoStatus } from "@/shared/api";
import type { DeliveryStatusResponseDtoStatus as DeliveryCd } from "@/shared/api";

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

/** 실 모드(SSE)에서 백엔드 배달 상태(DeliveryCd)를 화면 표시용 정보로 변환한 결과. */
export interface RealTrackView {
  /** 본문 제목 */
  title: string;
  /** 종료 상태(완료/취소)이면 true — 이 시점에 화면 전환/토스트를 트리거한다. */
  terminal: boolean;
  /** 완료(true)/취소(false)/진행 중(null) 구분. terminal일 때만 유효. */
  completed: boolean | null;
}

const Cd = DeliveryStatusResponseDtoStatus;

/** 백엔드 DeliveryCd → 부르미 추적 화면 표시용 정보. */
export function realTrackView(status: DeliveryCd | undefined): RealTrackView {
  switch (status) {
    case Cd.DELIVERING:
      return { title: "물품을 드림 중이에요", terminal: false, completed: null };
    case Cd.DELIVERED:
      return { title: "배달이 완료됐어요", terminal: true, completed: true };
    case Cd.PICKUP_CANCELLED_BY_BOORMI:
    case Cd.PICKUP_CANCELLED_BY_DREAMI:
    case Cd.PICKUP_CANCELLED_BY_ADMIN:
      return { title: "배달이 취소됐어요", terminal: true, completed: false };
    case Cd.PICKUP_NORMAL:
    default:
      return { title: "물품을 픽업 중이에요", terminal: false, completed: null };
  }
}

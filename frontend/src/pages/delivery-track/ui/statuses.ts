import type { DeliveryStatus } from "@/shared/store/deliveryStore";

/** 드리미 배달 진행 화면이 다루는 단계. */
export type TrackStage = Extract<DeliveryStatus, "픽업중" | "배송중">;

interface TrackConfig {
  /** 본문 제목 */
  title: string;
  /** 하단 주 버튼 문구 */
  action: string;
  /** 픽업 전에만 노출하는 배달 취소 링크 표시 여부 */
  cancelable: boolean;
}

/** 단계별 문구·액션(드리미 기준). 부르미 문구와 다르므로 별도 관리. */
export const TRACK_STAGES: Record<TrackStage, TrackConfig> = {
  픽업중: {
    title: "물품을 픽업 중이에요",
    action: "픽업 완료",
    cancelable: true,
  },
  배송중: {
    title: "물품을 배송 중이에요",
    action: "전달 완료",
    cancelable: false,
  },
};

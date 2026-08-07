import {
  DeliveryStatusResponseDtoStatus as DeliveryCd,
  type DeliveryStatusResponseDtoStatus,
} from "@/shared/api";

export interface UntrackableDeliveryNotice {
  title: string;
  message: string;
}

/** 현재 추적 화면에서 다룰 수 없는 인계·반송·완료·취소·종료 상태의 안내 문구를 반환한다. */
export function getUntrackableDeliveryNotice(
  status: DeliveryStatusResponseDtoStatus | undefined,
): UntrackableDeliveryNotice | null {
  switch (status) {
    case DeliveryCd.PICKUP_CANCELLED_BY_BOORMI:
      return {
        title: "부르미에 의해 취소된 배달이에요",
        message: "취소된 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.PICKUP_CANCELLED_BY_DREAMI:
      return {
        title: "드리미에 의해 취소된 배달이에요",
        message: "취소된 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.PICKUP_CANCELLED_BY_ADMIN:
      return {
        title: "관리자에 의해 취소된 배달이에요",
        message: "취소된 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.DELIVERED:
      return {
        title: "배달이 완료되었어요.",
        message: "완료된 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.PARTNER_HANDOFF_PENDING:
      return {
        title: "파트너 인계 중인 배달이에요",
        message: "파트너 인계가 시작된 배달은 이 화면에서 추적할 수 없어요.",
      };
    case DeliveryCd.TRANSFERRED_TO_PARTNER:
      return {
        title: "파트너에게 인계된 배달이에요",
        message: "파트너에게 인계된 배달은 이 화면에서 추적할 수 없어요.",
      };
    case DeliveryCd.RETURNING:
      return {
        title: "반송 중인 배달이에요",
        message: "반송 중인 배달은 이 화면에서 추적할 수 없어요.",
      };
    case DeliveryCd.RETURNED:
      return {
        title: "이미 반송 완료된 배달이에요",
        message: "반송이 끝난 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.TERMINATED:
      return {
        title: "이미 종료된 배달이에요",
        message: "종료된 배달은 더 이상 추적할 수 없어요.",
      };
    default:
      return null;
  }
}

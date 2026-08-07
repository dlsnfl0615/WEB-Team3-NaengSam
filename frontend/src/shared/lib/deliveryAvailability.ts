import {
  DeliveryStatusResponseDtoStatus as DeliveryCd,
  type DeliveryStatusResponseDtoStatus,
} from "@/shared/api";

export interface ClosedDeliveryNotice {
  title: string;
  message: string;
}

/** 추적 화면에 다시 진입할 수 없는 완료·취소·종료 상태의 안내 문구를 반환한다. */
export function getClosedDeliveryNotice(
  status: DeliveryStatusResponseDtoStatus | undefined,
): ClosedDeliveryNotice | null {
  switch (status) {
    case DeliveryCd.PICKUP_CANCELLED_BY_BOORMI:
    case DeliveryCd.PICKUP_CANCELLED_BY_DREAMI:
    case DeliveryCd.PICKUP_CANCELLED_BY_ADMIN:
      return {
        title: "이미 취소된 배달이에요",
        message: "취소된 배달은 더 이상 추적할 수 없어요.",
      };
    case DeliveryCd.DELIVERED:
      return {
        title: "이미 완료된 배달이에요",
        message: "완료된 배달은 더 이상 추적할 수 없어요.",
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

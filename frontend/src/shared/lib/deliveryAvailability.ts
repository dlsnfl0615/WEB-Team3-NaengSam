import {
  DeliveryStatusResponseDtoStatus as DeliveryCd,
  type DeliveryStatusResponseDtoStatus,
} from "@/shared/api";

export interface UntrackableDeliveryNotice {
  title: string;
  message: string;
}

/**
 * 추적 화면에 다시 진입할 수 없는 상태의 안내 문구를 반환한다.
 * 완료·취소·종료 상태와, 서비스에서 사용하지 않는 상태(지연·파트너 인계·반송 중)를 모두 막는다.
 */
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
    // 서비스에서 사용하지 않는 상태 — 추적을 막고 잘못된 상태로 안내한다.
    case DeliveryCd.PICKUP_DELAYED:
    case DeliveryCd.PARTNER_HANDOFF_PENDING:
    case DeliveryCd.TRANSFERRED_TO_PARTNER:
    case DeliveryCd.RETURNING:
      return {
        title: "잘못된 배송 상태입니다",
        message: "지원하지 않는 배송 상태라 더 이상 추적할 수 없어요.",
      };
    default:
      return null;
  }
}

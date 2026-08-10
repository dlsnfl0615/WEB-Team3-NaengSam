import {
  DeliveryStatusResponseDtoStatus as DeliveryCd,
  type DeliveryStatusResponseDtoStatus,
} from "@/shared/api";

export interface UntrackableDeliveryNotice {
  title: string;
  message: string;
}

/** 추적 화면에 다시 진입할 수 없는 완료·취소·종료 상태의 안내 문구를 반환한다. */
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
    case DeliveryCd.PICKUP_DELAYED:
      // TODO: 픽업 지연 상태의 추적 화면 정책이 정해지면 처리한다.
      return null;
    case DeliveryCd.PARTNER_HANDOFF_PENDING:
      // TODO: 파트너 인계 대기 상태의 추적 화면 정책이 정해지면 처리한다.
      return null;
    case DeliveryCd.TRANSFERRED_TO_PARTNER:
      // TODO: 파트너 인계 완료 상태의 추적 화면 정책이 정해지면 처리한다.
      return null;
    case DeliveryCd.RETURNING:
      // TODO: 반송 중 상태의 추적 화면 정책이 정해지면 처리한다.
      return null;
    default:
      return null;
  }
}

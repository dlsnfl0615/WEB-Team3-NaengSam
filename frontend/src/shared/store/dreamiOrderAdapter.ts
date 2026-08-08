import type { IconName } from "@/shared/ui";
import {
  DreamiDeliveryHistoryDtoDeliveryCd,
  DreamiDeliveryHistoryDtoOrderCd,
  type DreamiDeliveryHistoryDto,
} from "@/shared/api";
import {
  itemCdToIcon,
  orderCdToFilter,
  orderCdToLabel,
  type OrderFilter,
} from "./boormiOrderAdapter";

/** 드리미 활동 내역 화면 모델(DreamiDeliveryHistoryDto에서 파생). */
export interface DreamiOrder {
  id: string;
  icon: IconName;
  title: string;
  /** "출발 상세주소 → 도착 상세주소" 라벨. */
  route: string;
  /** Badge가 아는 한글 상태 라벨. */
  statusLabel: string;
  /** 필터 칩 분류. */
  filter: OrderFilter;
  /** 배달 요금(원). */
  amount: number;
  /** 완료 시각 라벨(예: "8/4 14:20"). 아직 완료되지 않았으면 빈 문자열. */
  time: string;
  /** 완료된 건에 매겨진 평점(없으면 undefined). */
  rating?: number;
}

/**
 * 주문 상태 + 배달 상태로 한글 라벨을 정한다. IN_PROGRESS인 주문 중 아직 픽업 전(PICKUP_NORMAL/PICKUP_DELAYED)이면
 * "픽업중", 그 외는 orderCdToLabel과 동일(배송중/완료/취소/사고).
 */
function toStatusLabel(
  orderCd?: DreamiDeliveryHistoryDtoOrderCd,
  deliveryCd?: DreamiDeliveryHistoryDtoDeliveryCd,
): string {
  const isPickingUp =
    deliveryCd === DreamiDeliveryHistoryDtoDeliveryCd.PICKUP_NORMAL ||
    deliveryCd === DreamiDeliveryHistoryDtoDeliveryCd.PICKUP_DELAYED;
  if (orderCd === DreamiDeliveryHistoryDtoOrderCd.IN_PROGRESS && isPickingUp) {
    return "픽업중";
  }
  return orderCdToLabel(orderCd);
}

/** 기본주소+상세주소를 이어붙인다. 둘 다 없으면 별칭, 그마저 없으면 fallback. */
function toDetailAddress(
  alias?: string,
  line1?: string,
  line2?: string,
  fallback = "",
): string {
  const detail = [line1, line2].filter(Boolean).join(" ");
  return detail || alias || fallback;
}

/** ISO 일시 → 목록 표시용 라벨(예: "8/4 14:20"). 값이 없으면 빈 문자열. */
function formatDreamiTime(dtm?: string): string {
  if (!dtm) return "";
  const date = new Date(dtm);
  if (Number.isNaN(date.getTime())) return dtm;
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hh}:${mm}`;
}

/** DreamiDeliveryHistoryDto → 화면 모델. */
export function toDreamiOrder(dto: DreamiDeliveryHistoryDto): DreamiOrder {
  const origin = toDetailAddress(
    dto.originAlias,
    dto.originAddressLine1,
    dto.originAddressLine2,
    "출발지",
  );
  const destination = toDetailAddress(
    dto.destinationAlias,
    dto.destinationAddressLine1,
    dto.destinationAddressLine2,
    "도착지",
  );
  return {
    id: dto.orderId ?? "",
    icon: itemCdToIcon(dto.itemCd),
    title: dto.itemName ?? "물품 배송",
    route: `${origin} → ${destination}`,
    statusLabel: toStatusLabel(dto.orderCd, dto.deliveryCd),
    filter: orderCdToFilter(dto.orderCd),
    amount: dto.deliveryAmount ?? 0,
    time: formatDreamiTime(dto.deliveryEndDtm),
    // 아직 평가되지 않은 건은 백엔드가 rating: null로 내려준다. record.rating !== undefined 체크가
    // null은 걸러내지 못해 렌더링에서 null.toFixed()가 터지므로, 여기서 undefined로 정규화한다.
    rating: dto.rating ?? undefined,
  };
}

import type { IconName } from "@/shared/ui";
import {
  OrderSummaryDtoItemCd,
  OrderSummaryDtoOrderCd,
  type OrderSummaryDto,
} from "@/shared/api";

/** 활동 내역 필터 칩 분류(부르미·드리미 공용). */
export type OrderFilter = "진행중" | "완료" | "취소";

/** 부르미 주문 화면 모델(OrderSummaryDto에서 파생). */
export interface BoormiOrder {
  id: string;
  icon: IconName;
  /** 목록 제목(물건 이름). */
  title: string;
  /** "출발 → 도착" 경로 라벨. */
  route: string;
  /** 상단 상세주소가 있으면 표시(활동 상세용). */
  originAddress: string;
  destinationAddress: string;
  /** Badge가 아는 한글 상태 라벨. */
  statusLabel: string;
  /** 필터 칩 분류. */
  filter: OrderFilter;
  /** 백엔드 원본 상태 코드(진행 중 판별·진행바용). */
  orderCd: OrderSummaryDtoOrderCd;
  /** 배달 요금(원). */
  amount: number;
  /** 예상 소요(초). 없으면 undefined. */
  eta?: number;
  /** 목록 표시용 시각 라벨. */
  time: string;
  imageKey?: string;
}

/** 진행 중으로 간주하는 주문 상태(홈 "진행 중인 부름" 필터 기준). */
export const ONGOING_ORDER_CDS = new Set<OrderSummaryDtoOrderCd>([
  OrderSummaryDtoOrderCd.MATCHING,
  OrderSummaryDtoOrderCd.PENDING_BOORMI_CONFIRMATION,
  OrderSummaryDtoOrderCd.IN_PROGRESS,
  OrderSummaryDtoOrderCd.WAITING_CONFIRMATION,
]);

/** 아직 드리미가 확정되지 않아 매칭 대기 화면으로 보내야 하는 주문 상태. */
export const MATCHING_ORDER_CDS = new Set<OrderSummaryDtoOrderCd>([
  OrderSummaryDtoOrderCd.MATCHING,
  OrderSummaryDtoOrderCd.PENDING_BOORMI_CONFIRMATION,
]);

/** 진행 상태 라벨 → 진행바 값(홈 DeliveryCard). */
export const ORDER_PROGRESS: Record<string, number> = {
  매칭중: 20,
  픽업중: 45,
  배송중: 75,
};

/** 주문 상태 코드 → Badge가 아는 한글 라벨(toneForStatus와 정합). */
export function orderCdToLabel(cd?: OrderSummaryDtoOrderCd): string {
  switch (cd) {
    case OrderSummaryDtoOrderCd.MATCHING:
    case OrderSummaryDtoOrderCd.PENDING_BOORMI_CONFIRMATION:
      return "매칭중";
    case OrderSummaryDtoOrderCd.IN_PROGRESS:
      return "배송중";
    case OrderSummaryDtoOrderCd.WAITING_CONFIRMATION:
      return "배송중";
    case OrderSummaryDtoOrderCd.COMPLETED:
      return "완료";
    case OrderSummaryDtoOrderCd.CANCELLED:
      return "취소";
    case OrderSummaryDtoOrderCd.CLAIM_REVIEW:
      return "사고";
    default:
      return "매칭중";
  }
}

/** 주문 상태 코드 → 필터 칩 분류. */
export function orderCdToFilter(cd?: OrderSummaryDtoOrderCd): OrderFilter {
  switch (cd) {
    case OrderSummaryDtoOrderCd.COMPLETED:
      return "완료";
    case OrderSummaryDtoOrderCd.CANCELLED:
    case OrderSummaryDtoOrderCd.CLAIM_REVIEW:
      return "취소";
    default:
      return "진행중";
  }
}

/** 물건 유형 코드 → 아이콘 이름. */
export function itemCdToIcon(cd?: OrderSummaryDtoItemCd): IconName {
  switch (cd) {
    case OrderSummaryDtoItemCd.DOCUMENT:
      return "document";
    case OrderSummaryDtoItemCd.PACKAGE:
      return "package";
    case OrderSummaryDtoItemCd.SAMPLE:
      return "star";
    default:
      return "more";
  }
}

/** ISO 일시 → 목록 표시용 라벨(예: "8/4 14:20"). 값이 없으면 빈 문자열. */
export function formatOrderTime(dtm?: string): string {
  if (!dtm) return "";
  const date = new Date(dtm);
  if (Number.isNaN(date.getTime())) return dtm;
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${month}/${day} ${hh}:${mm}`;
}

/** OrderSummaryDto → 화면 모델. */
export function toBoormiOrder(dto: OrderSummaryDto): BoormiOrder {
  const origin = dto.originAlias ?? dto.originAddressLine1 ?? "출발지";
  const destination =
    dto.destinationAlias ?? dto.destinationAddressLine1 ?? "도착지";
  return {
    id: dto.orderId ?? "",
    icon: itemCdToIcon(dto.itemCd),
    title: dto.itemName ?? "물품 배송",
    route: `${origin} → ${destination}`,
    originAddress: dto.originAddressLine1 ?? "",
    destinationAddress: dto.destinationAddressLine1 ?? "",
    statusLabel: orderCdToLabel(dto.orderCd),
    filter: orderCdToFilter(dto.orderCd),
    orderCd: dto.orderCd ?? OrderSummaryDtoOrderCd.MATCHING,
    amount: dto.deliveryAmount ?? 0,
    eta: dto.deliveryEta,
    time: formatOrderTime(dto.deliveryRequestDtm),
    imageKey: dto.imageKey,
  };
}

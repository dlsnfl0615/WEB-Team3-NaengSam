import type { IconName } from "@/shared/ui";
import type { OrderSummaryDtoOrderCd } from "@/shared/api";
import type { BoormiOrder } from "@/shared/store/boormiOrderAdapter";

/** 활동 내역 필터 칩 목록. */
export const ACTIVITY_FILTERS = ["전체", "진행중", "완료", "취소"] as const;

export type ActivityFilter = (typeof ACTIVITY_FILTERS)[number];

/**
 * 필터 칩에 표시할 라벨. 내부 값("진행중")은 OrderFilter·activityDetail 쿼리와 맞춰 두고
 * 화면 문구만 뱃지와 같은 "배송중"으로 보여준다.
 */
export const ACTIVITY_FILTER_LABEL: Record<ActivityFilter, string> = {
  전체: "전체",
  진행중: "배송중",
  완료: "완료",
  취소: "취소",
};

export interface ActivityRecord {
  id: string;
  icon: IconName;
  title: string;
  route: string;
  /** 뱃지에 표시할 상태 문자열. */
  status: string;
  /** 필터 칩 분류. */
  filter: Exclude<ActivityFilter, "전체">;
  time: string;
  note: string;
  amount: string;
  /** 백엔드 원본 상태 코드. 부르미 "진행중" 카드가 매칭 전/후 중 어디로 갈지 분기하는 데 쓴다. */
  orderCd: OrderSummaryDtoOrderCd;
}

/** 드리미 배달 내역(실제 API) → 활동 내역 레코드(드리미 관점). */
export function toActivityRecordFromDreamiOrder(
  order: BoormiOrder,
): ActivityRecord {
  return {
    id: order.id,
    icon: order.icon,
    title: order.title,
    route: order.route,
    status: order.statusLabel,
    filter: order.filter,
    time: order.time,
    note: "",
    amount: `+₩${order.amount.toLocaleString()}`,
    orderCd: order.orderCd,
  };
}

/** 부르미 주문(실제 API) → 활동 내역 레코드(부르미 관점). */
export function toActivityRecordFromOrder(order: BoormiOrder): ActivityRecord {
  return {
    id: order.id,
    icon: order.icon,
    title: order.title,
    route: order.route,
    status: order.statusLabel,
    filter: order.filter,
    time: order.time,
    note: "",
    amount: `₩${order.amount.toLocaleString()}`,
    orderCd: order.orderCd,
  };
}

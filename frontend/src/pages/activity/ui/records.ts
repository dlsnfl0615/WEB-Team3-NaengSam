import type { IconName } from "@/shared/ui";
import type { BoormiOrder } from "@/shared/store/boormiOrderAdapter";
import type { DreamiOrder } from "@/shared/store/dreamiOrderAdapter";

/** 활동 내역 필터 칩 목록. */
export const ACTIVITY_FILTERS = ["전체", "진행중", "완료", "취소"] as const;

export type ActivityFilter = (typeof ACTIVITY_FILTERS)[number];

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
  /** 있으면 별 아이콘과 함께 표시합니다. */
  rating?: number;
  amount: string;
}

/** 드리미 배달 내역(실제 API) → 활동 내역 레코드(드리미 관점). */
export function toActivityRecordFromDreamiOrder(
  order: DreamiOrder,
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
    rating: order.rating,
    amount: `+₩${order.amount.toLocaleString()}`,
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
  };
}

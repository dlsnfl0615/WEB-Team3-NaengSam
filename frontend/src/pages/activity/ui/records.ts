import type { IconName } from "@/shared/ui";
import type { Role } from "@/shared/lib/role/RoleContext";
import type { Delivery, DeliveryStatus } from "@/shared/mock/types";

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

/** 배달 상태 → 필터 칩 분류. */
function statusToFilter(status: DeliveryStatus): Exclude<ActivityFilter, "전체"> {
  if (status === "완료") return "완료";
  if (status === "취소" || status === "사고") return "취소";
  return "진행중";
}

/** 배달 목록을 현재 역할 관점의 활동 내역으로 파생. */
export function toActivityRecords(
  deliveries: Delivery[],
  role: Role,
): ActivityRecord[] {
  return deliveries
    .filter((d) => d.myRole === role)
    .map((d) => ({
      id: d.id,
      icon: d.icon,
      title: d.title,
      route: `${d.pickup} → ${d.dropoff}`,
      status: d.status,
      filter: statusToFilter(d.status),
      time: d.time,
      note: d.note ?? "",
      rating: d.rating,
      amount:
        role === "드리미"
          ? `+₩${d.price.toLocaleString()}`
          : `₩${d.price.toLocaleString()}`,
    }));
}

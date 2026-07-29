import type { IconName } from "@/shared/ui";

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

/** 부르미(요청한 배달) 내역. */
export const SENDER_RECORDS: ActivityRecord[] = [
  {
    id: "s1",
    icon: "document",
    title: "서류 배송",
    route: "A동 102호 → B동 405호",
    status: "배송중",
    filter: "진행중",
    time: "오늘 14:20",
    note: "드리미 '민'",
    amount: "₩12,000",
  },
  {
    id: "s2",
    icon: "package",
    title: "소형택배",
    route: "C동 3F → A동 로비",
    status: "완료",
    filter: "완료",
    time: "7/21",
    note: "평가함",
    rating: 5.0,
    amount: "₩8,000",
  },
  {
    id: "s3",
    icon: "drink",
    title: "음료 배송",
    route: "1F 카페 → 12F 회의실",
    status: "완료",
    filter: "완료",
    time: "7/20",
    note: "드리미 '조이'",
    amount: "₩4,500",
  },
];

/** 드리미(수행한 배달) 내역. */
export const DRIVER_RECORDS: ActivityRecord[] = [
  {
    id: "d1",
    icon: "drink",
    title: "음료 배송 #B-882",
    route: "파르나스 24F → 12F",
    status: "픽업중",
    filter: "진행중",
    time: "오늘 15:02",
    note: "부르미 '민'",
    amount: "+₩3,500",
  },
  {
    id: "d2",
    icon: "document",
    title: "서류 배송",
    route: "B동 405호 → 5F 사무실",
    status: "완료",
    filter: "완료",
    time: "오늘 13:40",
    note: "받음",
    rating: 5.0,
    amount: "+₩6,000",
  },
  {
    id: "d3",
    icon: "package",
    title: "소형택배",
    route: "A동 로비 → C동 7F",
    status: "사고",
    filter: "취소",
    time: "오늘 11:15",
    note: "받음",
    rating: 4.8,
    amount: "+₩5,000",
  },
];

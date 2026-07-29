import type { IconName } from "../Icon/icons";

export interface Place {
  name: string;
  detail: string;
  /** 최근(time) / 즐겨찾기(star) 구분 아이콘 */
  icon: IconName;
}

export const QUICK_OPTIONS = ["현재 위치", "지도에서 선택"] as const;

export const PLACES: Place[] = [
  { name: "B동 405호", detail: "마케팅팀 사무실", icon: "time" },
  { name: "A동 로비", detail: "1층 안내데스크", icon: "time" },
  { name: "C동 7F 회의실", detail: "즐겨찾기", icon: "star" },
];

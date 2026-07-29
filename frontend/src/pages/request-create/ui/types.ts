/** 대면/비대면 전달 방식. */
export type Meeting = "대면" | "비대면";

/** 부름 등록 폼 상태(UI 전용, API 미연동). */
export interface RequestForm {
  pickup: string;
  dropoff: string;
  /** 픽업 전달 방식 */
  pickupMeeting: Meeting;
  /** 도착 수령 방식 */
  dropoffMeeting: Meeting;
  /** 물품 유형 */
  itemType: "서류" | "소형택배" | "샘플" | "기타";
  /** 물품 크기 */
  itemSize: "S" | "M";
  itemName: string;
  detail: string;
  /** 배송 요청사항(단일 선택) */
  requestTag: "없음" | "도착 시 연락" | "파손주의" | "기타";
  etc: string;
}

export type UpdateForm = (patch: Partial<RequestForm>) => void;

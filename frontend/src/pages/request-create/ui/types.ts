/** 대면/비대면 전달 방식. */
export type Meeting = "대면" | "비대면";

/** 부름 등록 폼 상태. */
export interface RequestForm {
  /** 픽업지 도로명 주소(originAddressLine1). */
  pickup: string;
  /** 픽업지 상세주소(originAddressLine2). */
  pickupDetail: string;
  /** 도착지 도로명 주소(destinationAddressLine1). */
  dropoff: string;
  /** 도착지 상세주소(destinationAddressLine2). */
  dropoffDetail: string;
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
  /** 업로드한 물품 이미지의 S3 key(imageKey). */
  imageKey?: string;
}

export type UpdateForm = (patch: Partial<RequestForm>) => void;

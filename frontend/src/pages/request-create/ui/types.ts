/** 부름 등록 폼 상태(UI 전용, API 미연동). */
export interface RequestForm {
  pickup: string;
  dropoff: string;
  /** 전달 방식 */
  meeting: "대면" | "비대면";
  /** 물품 유형 */
  itemType: "서류" | "소형택배" | "샘플" | "기타";
  /** 물품 크기 */
  itemSize: "S" | "M";
  itemName: string;
  detail: string;
  /** 배송 요청사항(다중 선택) */
  tags: string[];
  etc: string;
}

export type UpdateForm = (patch: Partial<RequestForm>) => void;

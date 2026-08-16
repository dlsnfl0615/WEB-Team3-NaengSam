/**
 * 백엔드 `delivery_eta_unavailable` SSE payload(EtaUnavailableDto).
 * SSE는 OpenAPI 스펙에 없어 orval이 만들어주지 않으므로 여기서 직접 정의한다.
 *
 * 서버가 '드리미→픽업지' 경로·배송완료예상시간 계산에 실패했다는 뜻이다. `message`는
 * 실패 원인 에러코드가 갖고 있는 한글 문구(예: "출발지와 도착지가 너무 멀리 떨어져 있어요.")라
 * 화면은 그대로 노출하면 된다. `code`는 원인별 분기가 필요해질 때 쓴다.
 *
 * 서버가 30초 쿨다운을 두고 계산을 재시도하므로, 실패가 이어지는 동안 이 이벤트도 그 주기로 반복
 * 도착한다. 배지는 계속 숨기되 안내(토스트)는 첫 회만 띄운다.
 */
export interface EtaUnavailablePayload {
  orderId: string;
  code: string;
  message: string;
}

/** 화면 상단 안내에 쓰는 공통 제목. 이유(message)는 본문에 싣는다. */
export const ETA_UNAVAILABLE_TITLE = "배송 예상 시간을 계산할 수 없어요";

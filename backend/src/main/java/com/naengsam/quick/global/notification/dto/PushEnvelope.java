package com.naengsam.quick.global.notification.dto;

/**
 * 웹푸시 페이로드로 실려 나가는 wake-up 봉투. 서비스워커({@code frontend/src/sw.ts})의 push 핸들러가 읽는 계약이므로
 * 필드 이름을 바꾸려면 양쪽을 같이 고쳐야 한다.
 *
 * <p><b>주문 상세를 담지 않는다.</b> 수수료·주소·품목은 넣지 않는데, (a) 오퍼의 30초 TTL을 견디는 유일한 설계가
 * 순수 wake-up이고 (b) 잠금화면에 주문 정보가 새지 않으며 (c) 이 패키지가 도메인 DTO를 import하지 않아도 되기
 * 때문이다. 실제 상태는 앱이 열린 뒤 기존 스냅샷 API가 가져온다.
 *
 * @param url 알림을 탭했을 때 이동할 경로
 * @param tag 같은 tag의 알림은 기존 것을 교체한다. SSE 이벤트 이름을 그대로 써서 연속 오퍼가 쌓이지 않게 한다.
 */
public record PushEnvelope(String title, String body, String url, String tag) {
}

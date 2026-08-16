/**
 * 웹푸시 가능 여부 판별. 전부 순수 함수라 부수효과가 없고, UI가 중첩 조건문 대신
 * 하나의 상태로 분기할 수 있게 한다.
 */

/**
 * 인앱브라우저(카카오톡·인스타그램·네이버 등)인지.
 *
 * 인앱브라우저에는 서비스워커 푸시도 홈 화면 추가도 없고 우회법이 존재하지 않는다. 카카오톡은
 * 한국에서 링크 공유의 가장 흔한 진입점이라, 여기서 걸러 외부 브라우저 안내를 띄우는 것이
 * 이 사용자들에게 푸시가 존재할 수 있는 유일한 경로다.
 */
export function isInAppBrowser(): boolean {
  return /KAKAOTALK|Instagram|FBAN|FBAV|NAVER|Line|DaumApps/i.test(
    navigator.userAgent,
  );
}

/** 홈 화면에 설치돼 standalone으로 실행 중인지. iOS는 표준 display-mode를 안 주던 시절의 navigator.standalone도 본다. */
export function isStandalone(): boolean {
  const iosStandalone = (navigator as { standalone?: boolean }).standalone;
  if (iosStandalone === true) return true;
  // matchMedia가 없는 환경(구형 웹뷰·테스트 런타임)에서 던지지 않게 한다. 이 판별이 실패해
  // 예외가 나면 푸시 상태 결정 전체가 멈춘다.
  return typeof window.matchMedia === "function"
    ? window.matchMedia("(display-mode: standalone)").matches
    : false;
}

export function isIos(): boolean {
  // iPadOS 13+는 UA가 Macintosh로 나오므로 터치 지원 여부로 함께 판별한다.
  return (
    /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.userAgent.includes("Macintosh") && navigator.maxTouchPoints > 1)
  );
}

/**
 * 이 브라우저에 푸시 API가 존재하는지. iOS Safari 탭에는 PushManager가 문자 그대로 없고,
 * 홈 화면에 설치해야 비로소 생긴다.
 */
export function supportsPush(): boolean {
  return (
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

/**
 * base64url VAPID 공개키를 pushManager.subscribe가 요구하는 바이트 배열로 바꾼다.
 * 표준 base64가 아니라 URL-safe 변형이고 패딩이 생략돼 있어 그대로 atob에 넣으면 실패한다.
 */
export function urlBase64ToUint8Array(
  base64Url: string,
): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = window.atob(base64);
  // ArrayBuffer를 먼저 만들어 감싼다. 기본 Uint8Array는 SharedArrayBuffer도 담을 수 있는 타입이라
  // applicationServerKey(BufferSource)에 그대로 넘어가지 않는다.
  const output = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) output[i] = raw.charCodeAt(i);
  return output;
}

// 카카오 맵 SDK / 다음 우편번호 서비스 전역 객체(외부 스크립트로 주입).
// 정식 타입 패키지를 도입하지 않고 최소 선언만 둔다.
declare global {
  interface Window {
    /* eslint-disable @typescript-eslint/no-explicit-any */
    kakao: any;
    daum: any;
    /* eslint-enable @typescript-eslint/no-explicit-any */
  }
}

export {};

/**
 * 세션 만료(401) 후처리를 앱 계층에 위임하기 위한 콜백 레지스트리.
 *
 * axios 인터셉터(shared)가 store/router(app)를 직접 import 하면 순환참조가 생기고
 * FSD의 import 방향(app → shared)도 어긋난다. 그래서 shared는 "이벤트"만 발생시키고,
 * 실제 로그아웃/로그인 이동은 app(`main.tsx`)이 `setUnauthorizedHandler`로 등록한다.
 */
type UnauthorizedHandler = () => void

let onUnauthorized: UnauthorizedHandler = () => {}

/** 세션 만료 시 실행할 처리를 등록한다(로그아웃 + 로그인 화면 이동 등). */
export const setUnauthorizedHandler = (handler: UnauthorizedHandler): void => {
  onUnauthorized = handler
}

/** 인터셉터가 세션 만료를 감지했을 때 호출한다. */
export const emitUnauthorized = (): void => onUnauthorized()

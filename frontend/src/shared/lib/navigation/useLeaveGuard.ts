import { useEffect, useRef } from "react";

/**
 * 뒤로가기로 소비될 더미 히스토리 항목을 하나 쌓는다.
 * state를 `null`이 아니라 현재 값 그대로 복사하는 이유: react-router는 히스토리 항목마다 `idx`를 심어
 * 두고 popstate에서 그 차이로 이동량을 계산한다. `null`로 덮으면 라우터의 인덱스가 깨져
 * `useBackOrHome` 같은 기존 로직이 오작동한다.
 */
function pushSentinel() {
  window.history.pushState(window.history.state, "", window.location.href);
}

/**
 * 화면을 벗어나려는 시도를 가로채는 이탈 가드.
 *
 * - 브라우저 뒤로가기·스와이프: 마운트 시 히스토리에 sentinel 항목을 하나 쌓아두고,
 *   `popstate`가 오면 sentinel을 다시 밀어 넣어 현재 URL에 머무르게 한 뒤 `onAttempt()`를 부른다.
 * - 새로고침·탭 닫기: `beforeunload`로 브라우저 기본 경고를 띄운다(문구는 브라우저가 정한다).
 *
 * 앱 내부 `navigate()`는 막지 않는다. react-router v7의 `useBlocker`는 data router를 요구하는데
 * 이 앱은 `BrowserRouter` + `useRoutes` 구성이라 쓸 수 없고, 어차피 화면 안에서 일어나는 이동
 * (완료 → 사진 인증, 취소 → 홈)은 전부 의도된 이탈이라 가로챌 대상이 아니다.
 *
 * @param enabled 가드를 켤지 여부. false면 리스너를 아예 붙이지 않는다.
 * @param onAttempt 뒤로가기로 나가려 할 때 호출(확인 모달 열기 등).
 */
export function useLeaveGuard(enabled: boolean, onAttempt: () => void): void {
  // onAttempt가 매 렌더 새 함수여도 effect를 다시 돌리지 않도록 ref로 최신 값만 들고 있는다.
  // (effect가 재실행되면 sentinel이 계속 쌓인다.)
  const onAttemptRef = useRef(onAttempt);
  useEffect(() => {
    onAttemptRef.current = onAttempt;
  }, [onAttempt]);

  useEffect(() => {
    if (!enabled) return;

    // 뒤로가기로 소비될 sentinel 항목. 이게 있어야 첫 뒤로가기에서 화면을 벗어나지 않는다.
    pushSentinel();

    const onPopState = () => {
      // 소비된 sentinel을 즉시 되돌려 놓고(=현재 URL 유지) 확인 절차로 넘긴다.
      pushSentinel();
      onAttemptRef.current();
    };

    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // 표준에선 deprecated지만, 구형 브라우저는 아직 이 값을 봐야 경고를 띄운다.
      (e as { returnValue?: string }).returnValue = "";
    };

    window.addEventListener("popstate", onPopState);
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => {
      window.removeEventListener("popstate", onPopState);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, [enabled]);
}

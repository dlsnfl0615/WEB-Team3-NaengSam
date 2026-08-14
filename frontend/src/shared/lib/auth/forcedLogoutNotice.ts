/**
 * 강제 로그아웃 표식(sessionStorage).
 *
 * 401 인터셉터는 window.location.assign으로 로그인 화면으로 이동하기 때문에 전체 리로드가
 * 일어나고, 메모리 상태(zustand·Toast)로는 안내 문구를 넘길 수 없다. 그래서 표식만 남기고
 * 로그인 화면이 읽어 문구를 띄운다.
 *
 * 백엔드는 세션 교체(다른 기기 로그인)와 유휴 만료를 모두 401 AUTH_001로 보내 구분할 수 없으므로
 * 문구는 두 원인을 함께 담는다. 읽기는 표식을 지우지 않는다(StrictMode 이중 렌더에서 문구가
 * 사라지지 않도록) — 정리는 로그인 성공 시 clearForcedLogout()으로만 한다.
 */
const STORAGE_KEY = "naengsam.forcedLogout";

/** 세션이 끊겨 로그인 화면으로 보낸다는 표식을 남긴다. */
export function markForcedLogout(): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, "1");
  } catch {
    // 저장 실패(용량·프라이빗 모드)는 안내 문구를 포기할 뿐 로그아웃 자체엔 영향이 없다.
  }
}

/** 강제 로그아웃으로 이 화면에 왔는지 여부. */
export function hasForcedLogout(): boolean {
  try {
    return sessionStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

/** 표식을 지운다(로그인 성공 시). */
export function clearForcedLogout(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // 삭제 실패는 다음 로그인 화면에 문구가 한 번 더 보일 뿐이다.
  }
}

/**
 * 권한 요청 카드를 언제 다시 띄울지 기억한다. 사용자가 닫으면 일정 기간 묻지 않는다 —
 * 조르면 되돌릴 수 없는 거절(denied)을 수집하게 되고, denied는 페이지에서 되돌릴 수 없다.
 */

const DISMISSED_AT_KEY = "push-prompt-dismissed-at";

/** 닫은 뒤 다시 묻지 않는 기간. */
const SNOOZE_MS = 7 * 24 * 60 * 60 * 1000;

/** localStorage는 사파리 프라이빗 모드 등에서 접근 자체가 던질 수 있어 전부 감싼다. */
function safeRead(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function markPromptDismissed(): void {
  try {
    window.localStorage.setItem(DISMISSED_AT_KEY, String(Date.now()));
  } catch {
    // 저장에 실패하면 다음에 한 번 더 묻게 될 뿐이라 무시한다.
  }
}

export function isPromptSnoozed(): boolean {
  const dismissedAt = Number(safeRead(DISMISSED_AT_KEY));
  if (!dismissedAt) return false;
  return Date.now() - dismissedAt < SNOOZE_MS;
}

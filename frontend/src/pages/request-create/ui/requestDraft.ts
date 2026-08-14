import type { RequestForm } from "./types";

/**
 * 작성 중인 부름 등록 폼의 임시저장(sessionStorage).
 *
 * 결제 스텝에서 포인트 충전 화면으로 이동하면 RequestCreateScreen이 언마운트되며 useState로
 * 들고 있던 입력이 전부 사라진다. 그 사이를 메우는 스냅샷이다(새로고침·실수로 뒤로가기도 함께 복원).
 * 등록에 성공하거나 사용자가 1스텝에서 화면을 벗어나면 지운다.
 */
const STORAGE_KEY = "naengsam.requestDraft";

export interface RequestDraft {
  /** 마지막으로 보고 있던 스텝(1~4). */
  step: number;
  form: RequestForm;
}

/** 작성 중인 폼을 저장한다. */
export function saveRequestDraft(draft: RequestDraft): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
  } catch {
    // 저장 실패(용량·프라이빗 모드)는 복원을 포기할 뿐 화면 동작에 영향이 없다.
  }
}

/** 저장된 폼을 돌려준다(없거나 형식이 깨졌으면 null → 빈 폼으로 시작). */
export function readRequestDraft(): RequestDraft | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<RequestDraft>;
    const step = parsed.step;
    if (typeof step !== "number" || step < 1 || step > 4) return null;
    if (!parsed.form || typeof parsed.form !== "object") return null;
    return { step, form: parsed.form as RequestForm };
  } catch {
    return null;
  }
}

/** 저장된 폼을 지운다. */
export function clearRequestDraft(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // 삭제 실패는 다음 진입에서 이전 입력이 한 번 더 복원될 뿐이다.
  }
}

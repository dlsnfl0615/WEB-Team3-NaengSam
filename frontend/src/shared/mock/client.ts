/** 목 API 지연·실패 시뮬레이션 옵션. */
export interface MockOptions {
  /** 응답 지연(ms). 기본 400. */
  delayMs?: number;
  /** 0~1 실패 확률. 기본 0(항상 성공). */
  failRate?: number;
  /** 실패 시 reject 메시지. */
  errorMessage?: string;
}

/**
 * 목 API 호출을 흉내내는 헬퍼. `setTimeout`으로 네트워크 지연을,
 * `failRate`로 확률적 실패를 시뮬레이션한다.
 *
 * 실제 API 연동 시 각 서비스의 `mockRequest(...)` 호출부만 Orval 생성
 * 클라이언트 호출로 교체하면 되도록, 반환 타입을 `Promise<T>`로 통일한다.
 */
export function mockRequest<T>(data: T, options: MockOptions = {}): Promise<T> {
  const {
    delayMs = 400,
    failRate = 0,
    errorMessage = "요청에 실패했어요. 다시 시도해주세요.",
  } = options;

  return new Promise<T>((resolve, reject) => {
    setTimeout(() => {
      if (failRate > 0 && Math.random() < failRate) {
        reject(new Error(errorMessage));
        return;
      }
      resolve(data);
    }, delayMs);
  });
}

let seq = 1000;

/** 목 전용 순차 ID 생성기(예: `nextId("D") → "D1001"`). */
export function nextId(prefix: string): string {
  seq += 1;
  return `${prefix}${seq}`;
}

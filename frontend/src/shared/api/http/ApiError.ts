/**
 * 공통 응답 envelope의 실패 body. 백엔드는 성공/실패 모두 아래 모양으로 응답하며,
 * 실패 시 실제 HTTP 상태코드(401/403/404/409/429/500…)를 함께 내려준다.
 */
export interface ApiFailBody {
  isSuccess: false
  code: string
  message: string
  result: null
}

/**
 * API 실패를 나타내는 표준 에러. axios 인터셉터가 모든 실패 응답을 이 타입으로 정규화한다.
 * - `message`: 백엔드가 내려준 한글 사용자 메시지 → UI에 그대로 노출.
 * - `code`: 도메인 에러코드(AUTH_006, USER_005, COMMON_010…) → 분기 로직에 사용.
 * - `status`: HTTP 상태코드.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/** catch 블록에서 API 에러를 판별하는 타입 가드. */
export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError

/** 응답 body가 없거나(네트워크 오류 등) 파싱 실패 시 사용할 폴백 메시지. */
export const FALLBACK_ERROR_MESSAGE =
  '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'

import axios, { AxiosError } from 'axios'
import { ApiError, FALLBACK_ERROR_MESSAGE, type ApiFailBody } from './ApiError'
import { emitUnauthorized } from './authEvents'

/** 세션 만료로 재로그인이 필요한 인증 에러코드(부르미 미로그인/세션 무효/만료). */
const SESSION_EXPIRED_CODES = new Set(['AUTH_001', 'AUTH_002', 'AUTH_003'])

/**
 * 앱 시작 시 세션 존재 여부를 확인하는 probe 요청에 싣는 헤더.
 * 이 헤더가 있는 요청의 401은 전역 로그인 리다이렉트를 유발하지 않는다(공개 페이지 보호).
 */
export const SESSION_PROBE_HEADER = 'X-Session-Probe'

/**
 * 모든 API 요청이 공유하는 axios 인스턴스.
 * - `baseURL`: 개발은 빈값(동일 출처 + Vite 프록시), 운영은 `VITE_API_BASE_URL` 오리진.
 *   생성 클라이언트의 요청 경로에 이미 `/api/v1`이 포함되므로 여기에는 오리진만 둔다.
 * - `withCredentials`: 세션 쿠키(JSESSIONID) 자동 송수신에 필수.
 */
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

// 실패 응답을 공통 ApiError로 정규화한다. 세션 만료면 앱에 알려 재로그인을 유도한다.
axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiFailBody>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const code = body?.code ?? 'COMMON_010'
    // 백엔드가 준 한글 메시지를 우선하고, 없으면(네트워크 오류·타임아웃 등 응답 바디 부재)
    // axios 원본 영문 메시지("Network Error") 대신 한글 폴백을 노출한다.
    const message = body?.message ?? FALLBACK_ERROR_MESSAGE

    // 세션 probe(앱 시작 확인)의 401은 리다이렉트 대상에서 제외한다.
    const isProbe = Boolean(error.config?.headers?.[SESSION_PROBE_HEADER])
    if (!isProbe && status === 401 && SESSION_EXPIRED_CODES.has(code)) {
      emitUnauthorized()
    }

    return Promise.reject(new ApiError(status, code, message))
  },
)

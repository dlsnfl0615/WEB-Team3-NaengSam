/**
 * API 공개 진입점. 사용: `import { api, isApiError } from '@/shared/api'`.
 *
 * 생성 클라이언트(orval)는 팩토리 `getOpenAPIDefinition()` 로 모든 오퍼레이션을 반환하므로,
 * 여기서 한 번 인스턴스화해 `api` 로 노출한다. 모든 함수는 공통 axios 인스턴스(customInstance)를
 * 거치므로 세션 쿠키·공통 에러(ApiError) 처리가 자동 적용된다.
 *
 * 예) `const { result } = await api.me()` / `await api.login({ email, password })`
 */
import { getOpenAPIDefinition } from './generated/endpoints'

export const api = getOpenAPIDefinition()

export * from './http/ApiError'
export * from './http/authEvents'
export { SESSION_PROBE_HEADER } from './http/axiosInstance'
export * from './generated/model'

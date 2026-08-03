import type { AxiosRequestConfig } from 'axios'
import { axiosInstance } from './axiosInstance'

/**
 * orval mutator. 생성된 모든 API 함수가 이 함수를 통해 요청을 보낸다.
 *
 * 공통 envelope(`{ isSuccess, code, message, result }`)를 **언랩하지 않고 그대로 반환**한다.
 * 생성 타입도 envelope 모양이므로 호출부는 `const { result } = await me()` 처럼 접근한다.
 * 실패는 `axiosInstance` 인터셉터가 `ApiError`로 정규화해 reject 한다.
 */
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> =>
  axiosInstance({ ...config, ...options }).then((response) => response.data as T)

export default customInstance

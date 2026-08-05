import { useEffect, useRef, useState } from 'react'

/** SSE 이벤트 이름 → 파싱된 payload를 받는 핸들러. */
export type SseHandlers = Record<string, (data: unknown) => void>

export interface UseSseOptions {
  /** false면 연결하지 않는다(예: 필수 파라미터가 아직 없을 때). 기본 true. */
  enabled?: boolean
}

export interface SseState {
  /** 최초 핸드셰이크(connected) 이후 true. */
  connected: boolean
}

/**
 * 로그인 사용자별 SSE 스트림(`GET /api/v1/sse/subscribe`)을 구독하는 범용 훅.
 *
 * - 인증은 세션 쿠키(JSESSIONID) 기반이라 `EventSource`의 `withCredentials`로 충분하다(커스텀 헤더 불필요).
 *   생성된 axios 클라이언트(`api.subscribe`)는 스트리밍을 못 하므로 여기서는 네이티브 `EventSource`를 쓴다.
 * - baseURL 규약은 `axiosInstance`와 동일: 개발은 빈 base(동일 출처 → Vite `/api` 프록시),
 *   운영은 `VITE_API_BASE_URL` 오리진(교차 출처 + `withCredentials`).
 * - 백엔드가 모든 도메인 이벤트를 하나의 연결로 멀티플렉싱하므로 이 훅은 도메인 비종속이다.
 *   소비 측이 필요한 이벤트 이름만 `handlers`로 등록한다.
 *
 * 연결은 컴포넌트 수명(mount~unmount) 동안만 유지되며, unmount 시 반드시 `close()` 한다.
 * `handlers`는 매 렌더 새로 생기므로 `ref`로 최신본을 보관해 `EventSource`를 재생성하지 않는다.
 */
export function useSse(handlers: SseHandlers, options: UseSseOptions = {}): SseState {

  // options 중에서 enabled만 뽑아 기본값 true를 준다. false면 연결하지 않는다.
  const { enabled = true } = options
  const handlersRef = useRef(handlers)

  // connected : 서버가 연결되어 있는지 반영 (connected가 바뀌면 UI가 바뀌므로 state로 관리)
  const [connected, setConnected] = useState(false)

  // 렌더 중이 아니라 커밋 후에 최신 핸들러를 보관한다(연결은 재생성하지 않고 dispatch만 최신화).
  useEffect(() => {
    handlersRef.current = handlers
  })

  // 참고 : useEffect()는 리액트가 직접 호출
  useEffect(() => {
    if (!enabled) return

    const base = import.meta.env.VITE_API_BASE_URL ?? ''
    const source = new EventSource(`${base}/api/v1/sse/subscribe`, {
      withCredentials: true,
    })

    // 최초 핸드셰이크: 연결 확립 신호.
    source.addEventListener('connected', () => setConnected(true))

    // 등록된 이벤트 이름들을 최신 핸들러로 위임한다.
    const names = Object.keys(handlersRef.current)
    const listeners = names.map((name) => {
      const listener = (event: MessageEvent) => {
        const handler = handlersRef.current[name]
        if (!handler) return
        try {
          handler(JSON.parse(event.data))
        } catch {
          // data가 JSON이 아니면(예: 빈 문자열) 원본을 그대로 넘긴다.
          handler(event.data)
        }
      }
      source.addEventListener(name, listener)
      return { name, listener }
    })

    // 네이티브 재연결에 맡기되, 끊긴 동안은 연결 배너 표시를 위해 상태를 내린다.
    source.onerror = () => setConnected(false)

    return () => {
      listeners.forEach(({ name, listener }) => source.removeEventListener(name, listener))
      source.close()
      setConnected(false)
    }
  }, [enabled])

  return { connected }
}

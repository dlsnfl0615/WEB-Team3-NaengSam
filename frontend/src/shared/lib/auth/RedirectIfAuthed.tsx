import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'

/**
 * 이미 로그인된 사용자를 홈으로 보낸다(게스트 전용 라우트: 온보딩·로그인).
 * RequireAuth의 반대 가드다.
 * 앱 시작 세션 확인(hydrated)이 끝나기 전에는 렌더를 보류해(null),
 * 쿠키 세션 복원 중 게스트 화면이 잠깐 노출되는 것을 막는다.
 */
export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const hydrated = useSessionStore((s) => s.hydrated)
  const isAuthenticated = useSessionStore((s) => s.isAuthenticated)

  if (!hydrated) return null
  if (isAuthenticated) return <Navigate to={ROUTES.home} replace />
  return <>{children}</>
}

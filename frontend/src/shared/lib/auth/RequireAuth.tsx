import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'

/**
 * 로그인 세션이 없으면 로그인 화면으로 보낸다.
 * 앱 시작 세션 확인(hydrated)이 끝나기 전에는 렌더를 보류해(로더 대신 null),
 * 쿠키가 유효한 사용자가 잠깐 로그인으로 튕기는 것을 막는다.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const hydrated = useSessionStore((s) => s.hydrated)
  const isAuthenticated = useSessionStore((s) => s.isAuthenticated)

  if (!hydrated) return null
  if (!isAuthenticated) return <Navigate to={ROUTES.login} replace />
  return <>{children}</>
}

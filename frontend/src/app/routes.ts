import { createElement } from 'react'
import { Navigate, type RouteObject } from 'react-router-dom'
import { GUEST_ONLY_ROUTES, PUBLIC_ROUTES, ROUTES } from '@/shared/config/routes'
import { RedirectIfAuthed } from '@/shared/lib/auth/RedirectIfAuthed'
import { RequireAuth } from '@/shared/lib/auth/RequireAuth'

/**
 * 화면별 라우트 자동 집계.
 * 각 페이지 슬라이스(src/pages/<name>/)가 `route.tsx`에서 `route: RouteObject`를 export하면
 * 여기서 자동으로 수집합니다. 새 화면을 추가해도 이 파일은 수정할 필요가 없습니다.
 *
 * - GUEST_ONLY_ROUTES(온보딩·로그인): 이미 로그인됐으면 홈으로 보냅니다(RedirectIfAuthed).
 * - PUBLIC_ROUTES의 나머지: 누구나 접근 가능(가드 없음).
 * - 그 외: RequireAuth로 감싸 로그인 세션을 요구합니다
 *   (신규 페이지는 기본 보호, 공개로 열려면 shared/config/routes.ts의 PUBLIC_ROUTES에 추가).
 */
const modules = import.meta.glob('../pages/*/route.tsx', {
  eager: true,
}) as Record<string, { route: RouteObject }>

const pageRoutes: RouteObject[] = Object.values(modules).map((m) => {
  const route = m.route
  if (route.path && GUEST_ONLY_ROUTES.includes(route.path)) {
    return { ...route, element: createElement(RedirectIfAuthed, null, route.element) }
  }
  if (route.path && PUBLIC_ROUTES.includes(route.path)) return route
  return { ...route, element: createElement(RequireAuth, null, route.element) }
})

export const routes: RouteObject[] = [
  ...pageRoutes,
  {
    path: '*',
    element: createElement(Navigate, { to: ROUTES.home, replace: true }),
  },
]

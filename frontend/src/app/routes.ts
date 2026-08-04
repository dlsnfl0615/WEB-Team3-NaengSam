import { createElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { PUBLIC_ROUTES } from '@/shared/config/routes'
import { RequireAuth } from '@/shared/lib/auth/RequireAuth'

/**
 * 화면별 라우트 자동 집계.
 * 각 페이지 슬라이스(src/pages/<name>/)가 `route.tsx`에서 `route: RouteObject`를 export하면
 * 여기서 자동으로 수집합니다. 새 화면을 추가해도 이 파일은 수정할 필요가 없습니다.
 *
 * PUBLIC_ROUTES에 없는 라우트는 RequireAuth로 감싸 로그인 세션을 요구합니다
 * (신규 페이지는 기본 보호, 공개로 열려면 shared/config/routes.ts의 PUBLIC_ROUTES에 추가).
 */
const modules = import.meta.glob('../pages/*/route.tsx', {
  eager: true,
}) as Record<string, { route: RouteObject }>

export const routes: RouteObject[] = Object.values(modules).map((m) => {
  const route = m.route
  if (route.path && PUBLIC_ROUTES.includes(route.path)) return route
  return { ...route, element: createElement(RequireAuth, null, route.element) }
})

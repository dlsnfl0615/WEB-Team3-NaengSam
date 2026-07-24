import type { RouteObject } from 'react-router-dom'

/**
 * 화면별 라우트 자동 집계.
 * 각 페이지 슬라이스(src/pages/<name>/)가 `route.tsx`에서 `route: RouteObject`를 export하면
 * 여기서 자동으로 수집합니다. 새 화면을 추가해도 이 파일은 수정할 필요가 없습니다.
 */
const modules = import.meta.glob('../pages/*/route.tsx', {
  eager: true,
}) as Record<string, { route: RouteObject }>

export const routes: RouteObject[] = Object.values(modules).map((m) => m.route)

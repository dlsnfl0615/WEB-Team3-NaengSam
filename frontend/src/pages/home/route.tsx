import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { HomeScreen } from './ui/HomeScreen'

export const route: RouteObject = {
  path: ROUTES.home,
  element: <HomeScreen />,
}

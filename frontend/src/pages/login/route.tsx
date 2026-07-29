import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { LoginScreen } from './ui/LoginScreen'

export const route: RouteObject = {
  path: ROUTES.login,
  element: <LoginScreen />,
}

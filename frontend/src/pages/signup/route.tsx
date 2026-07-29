import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { SignupScreen } from './ui/SignupScreen'

export const route: RouteObject = {
  path: ROUTES.signup,
  element: <SignupScreen />,
}

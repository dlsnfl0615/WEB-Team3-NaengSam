import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { VerifyScreen } from './ui/VerifyScreen'

export const route: RouteObject = {
  path: ROUTES.verify,
  element: <VerifyScreen />,
}

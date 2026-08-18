import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { DreamiPendingScreen } from './ui/DreamiPendingScreen'

export const route: RouteObject = {
  path: ROUTES.dreamiPending,
  element: <DreamiPendingScreen />,
}

import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { FaqScreen } from './ui/FaqScreen'

export const route: RouteObject = {
  path: ROUTES.faq,
  element: <FaqScreen />,
}

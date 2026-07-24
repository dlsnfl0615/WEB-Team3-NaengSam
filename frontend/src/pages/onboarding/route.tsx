import type { RouteObject } from 'react-router-dom'
import { ROUTES } from '@/shared/config/routes'
import { OnboardingScreen } from './ui/OnboardingScreen'

export const route: RouteObject = {
  path: ROUTES.onboarding,
  element: <OnboardingScreen />,
}

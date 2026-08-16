import { BrowserRouter, useRoutes } from 'react-router-dom'
import { MatchingPopup } from '@/pages/matching'
import { PushNavigationBridge } from '@/shared/lib/push/PushNavigationBridge'
import { routes } from './routes'

function AppRoutes() {
  return useRoutes(routes)
}

/** 앱 루트: 라우터 프로바이더 + 자동 집계된 라우트 + 전역 매칭 팝업. */
function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
      <MatchingPopup />
      <PushNavigationBridge />
    </BrowserRouter>
  )
}

export default App

import { BrowserRouter, useRoutes } from 'react-router-dom'
import { routes } from './routes'

function AppRoutes() {
  return useRoutes(routes)
}

/** 앱 루트: 라우터 프로바이더 + 자동 집계된 라우트. */
function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}

export default App

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/inter";
import "./app/styles/theme.css";
import "./app/styles/index.css";
import "./app/styles/App.css";
import App from "./app/App";
import { RoleProvider } from "./shared/lib/role/RoleProvider";
import { SseProvider } from "./shared/lib/sse/SseProvider";
import { SseStatusBanner } from "./shared/lib/sse/SseStatusBanner";
import { setUnauthorizedHandler } from "./shared/api";
import { useSessionStore } from "./shared/store/sessionStore";
import { ROUTES } from "./shared/config/routes";

// 세션 만료(401 AUTH_001~003) 시 로컬 세션을 비우고 로그인 화면으로 보낸다.
// store.logout()이 아니라 setState로 비운다 — 이미 미인증 상태에서 api.logout을 부르면
// 또 401이 나 무한 루프가 되기 때문이다.
setUnauthorizedHandler(() => {
  useSessionStore.setState({ user: null, isAuthenticated: false });
  if (window.location.pathname !== ROUTES.login) {
    window.location.assign(ROUTES.login);
  }
});

// 앱 시작 시 쿠키 세션으로 로그인 상태를 복원한다(라우트 가드가 hydrated를 기다림).
useSessionStore.getState().bootstrap();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RoleProvider>
      <SseProvider>
        <App />
        <SseStatusBanner />
      </SseProvider>
    </RoleProvider>
  </StrictMode>,
);

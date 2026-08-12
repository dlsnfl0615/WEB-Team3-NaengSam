/// <reference lib="webworker" />
// 서비스워커(Service Worker)는 웹페이지와 별도로 브라우저 백그라운드에서 실행되는 작은 JavaScript 프로그램
// 따라서 앱 탭이 닫혀 있어도 웹푸시 수신이 가능함
import { clientsClaim } from "workbox-core";
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
} from "workbox-precaching";
import { NavigationRoute, registerRoute } from "workbox-routing";

declare const self: ServiceWorkerGlobalScope;

// generateSW + autoUpdate가 자동으로 넣어주던 설치·갱신 동작을 직접 유지한다.
// 추후 Phase5a에서 서버에서 보내줄 예정
self.addEventListener("install", () => {
  void self.skipWaiting();
});
clientsClaim(); // 새 서비스워커가 활성화되는 즉시 현재 열려 있는 웹페이지들을 제어하게 만드는 Workbox 함수
precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

// SPA 딥링크의 오프라인 진입은 index.html로 되돌리되 API 요청은 절대 가로채지 않는다.
// (여기서 딥링크는 /가 아닌 특정 화면으로 바로 들어가는 URL)
registerRoute(
  new NavigationRoute(createHandlerBoundToURL("index.html"), {
    denylist: [/^\/api\//],
  }),
);

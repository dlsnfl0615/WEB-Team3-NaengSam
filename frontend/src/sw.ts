/// <reference lib="webworker" />
// 서비스워커(Service Worker)는 웹페이지와 별도로 브라우저 백그라운드에서 실행되는 작은 JavaScript 프로그램
// 따라서 앱 탭이 닫혀 있어도 웹푸시 수신이 가능함
import {clientsClaim} from "workbox-core";
import {cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute,} from "workbox-precaching";
import {NavigationRoute, registerRoute} from "workbox-routing";

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

interface PushEnvelope {
    title: string;
    body: string;
    url: string;
    tag: string;
}

// TypeScript 타입 정의의 누락을 보완하는 선언입니다. 브라우저가 renotify를 지원하지 않는 경우에는
//   해당 옵션을 무시하며 기본 알림 표시는 계속 동작
interface RenotifyNotificationOptions extends NotificationOptions {
    renotify: boolean;
}

self.addEventListener("push", (event) => {
    // 푸시 페이로드는 라우팅 봉투만 담는다. 파싱에 실패해도 사용자에게 알림은 표시한다.
    let data: Partial<PushEnvelope> = {};
    try {
        data = event.data?.json() ?? {};
    } catch {
        // iOS의 userVisibleOnly 정책을 지키기 위해 기본 문구로 계속 진행한다.
        // => 즉, 푸시 데이터 파싱이 실패해도 시스템 알림 표시를 포기하지 않는다는 것
        // 푸시 JSON 파싱이 실패했다고 그냥 종료하면 “보이지 않는 푸시(silent push)”가 됨.
        // 이런 경우, 브라우저는 이런 구독에 대해 이후 푸시 전달을 제한하거나 구독을 무효화할 수 있음.
    }

    const options: RenotifyNotificationOptions = {
        body: data.body ?? "새 알림이 도착했어요",
        icon: "/pwa-192x192.png",
        badge: "/pwa-192x192.png",
        // 같은 tag의 연속 오퍼는 기존 알림을 교체한다.
        tag: data.tag ?? "default",
        renotify: true,
        data: {url: data.url ?? "/"},
    };
    event.waitUntil(
        self.registration.showNotification(data.title ?? "쉼,부름", options),
    );
});

// 알림 클릭했을 때 앱이 열리거나 이미 열려 있으면 해당 탭으로 포커스한다.
self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const url =
        (event.notification.data as { url?: string } | undefined)?.url ?? "/";

    event.waitUntil(
        (async () => {
            const windows = await self.clients.matchAll({
                type: "window",
                includeUncontrolled: true,
            });
            const existing = windows.find(
                (client) => new URL(client.url).origin === self.location.origin,
            );

            if (existing) {
                // 기존 앱 상태와 SSE 연결을 보존하기 위해 새 창이나 전체 문서 탐색을 만들지 않는다.
                await existing.focus();
                existing.postMessage({type: "PUSH_NAVIGATE", url});
                return;
            }

            await self.clients.openWindow(url);
        })(),
    );
});

self.addEventListener("pushsubscriptionchange", (event) => {
    // 최종 복구는 앱 포그라운드 재등록이 담당하고, 여기서는 기존 키가 있을 때만 best-effort로 보정한다.
    event.waitUntil(
        (async () => {
            const key = event.oldSubscription?.options.applicationServerKey;
            if (!key) return;

            const subscription = await self.registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: key,
            });
            const base = import.meta.env.VITE_API_BASE_URL ?? "";
            await fetch(`${base}/api/v1/push/subscriptions`, { // Phase5a에서 구현 예정
                method: "POST",
                credentials: "include",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(subscription),
            });
        })(),
    );
});

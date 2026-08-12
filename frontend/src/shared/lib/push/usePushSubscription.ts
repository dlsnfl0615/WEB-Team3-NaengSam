import { useCallback, useEffect, useState } from "react";
import { api } from "@/shared/api";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useToastStore } from "@/shared/store/toastStore";
import {
  isInAppBrowser,
  isIos,
  isStandalone,
  supportsPush,
  urlBase64ToUint8Array,
} from "./pushCapability";
import { isPromptSnoozed, markPromptDismissed } from "./pushPromptStorage";

/**
 * 화면이 분기해야 하는 푸시 상태. UI가 중첩 조건문 대신 이 값 하나로 switch 할 수 있게 좁혀둔 것이다.
 *
 * - `unsupported`: 이 브라우저·서버 설정으로는 푸시가 불가능하다. **아무것도 렌더하지 않는다**(안내조차 소음이다)
 * - `in-app-browser`: 카카오톡 등 인앱브라우저. 외부 브라우저로 여는 것 외에 방법이 없다
 * - `needs-install`: iOS인데 홈 화면 미설치. iOS 웹푸시는 설치가 하드 요구사항이다
 * - `default`: 아직 묻지 않았다. 권한 요청 카드를 띄울 수 있다
 * - `granted`: 허용됨. 카드는 없고 포그라운드 복귀마다 구독만 재등록한다
 * - `denied`: 거절됨. 페이지에서 재요청이 불가능하므로 기기 설정 안내만 한다
 */
export type PushState =
  | "unsupported"
  | "in-app-browser"
  | "needs-install"
  | "default"
  | "granted"
  | "denied";

export interface PushSubscriptionControls {
  state: PushState;
  /** 권한 요청 카드를 지금 띄워도 되는지. state가 default이고 최근에 닫지 않았을 때만 참이다. */
  shouldPrompt: boolean;
  /** Android 등에서 홈 화면 설치를 제안할 수 있는지. 푸시 가능 여부와 독립이다. */
  canInstall: boolean;
  /** 사용자 제스처 안에서 호출해야 한다. 권한 요청 → 구독 생성 → 서버 등록까지 한 번에 한다. */
  enable: () => Promise<void>;
  /** 카드를 닫는다. 일정 기간 다시 묻지 않는다. */
  dismiss: () => void;
  /** 설치 프롬프트를 띄운다(캡처된 beforeinstallprompt가 있을 때만 동작). */
  promptInstall: () => Promise<void>;
  enabling: boolean;
}

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
}

/**
 * VAPID 공개키는 앱 수명 동안 바뀌지 않으므로 한 번만 받아 캐시한다.
 * null은 "서버에서 푸시가 꺼져 있음"을 뜻하고, 이 경우 어떤 푸시 UI도 렌더하지 않는다.
 */
let publicKeyPromise: Promise<string | null> | null = null;

function fetchPublicKey(): Promise<string | null> {
  publicKeyPromise ??= api
    .vapidPublicKey()
    .then((res) => res.result?.publicKey ?? null)
    .catch(() => null);
  return publicKeyPromise;
}

/**
 * 이 브라우저의 구독을 가져오고, 없으면 만든다.
 *
 * <p>알림 권한이 이미 granted인 상태에서만 호출해야 한다. 그 경우 subscribe는 권한 프롬프트를 띄우지
 * 않으므로 사용자 제스처 밖에서도 안전하다. 권한은 허용됐는데 구독이 없는 조합은 실제로 흔하다 —
 * 이전에 켰다가 사이트 데이터를 지웠거나, 브라우저·테스트 도구가 권한을 미리 부여한 경우다.
 * 여기서 만들어주지 않으면 그런 사용자는 영원히 푸시를 받지 못한다.
 */
async function ensureSubscription(publicKey: string): Promise<PushSubscription> {
  const registration = await navigator.serviceWorker.ready;
  return (
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true, // iOS가 강제한다. 무음 데이터 푸시는 존재하지 않는다.
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }))
  );
}

/** 현재 구독을 서버에 등록한다. 서버 쪽이 멱등 upsert라 몇 번 불려도 안전하다. */
async function postSubscription(subscription: PushSubscription): Promise<void> {
  const json = subscription.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) return;
  await api.createSubscription({
    endpoint: json.endpoint,
    keys: { p256dh: json.keys.p256dh, auth: json.keys.auth },
  });
}

/**
 * 웹푸시 구독 상태와 활성화 동작을 제공한다.
 *
 * <p>실제 구독 드리프트 복구는 `pushsubscriptionchange` 서비스워커 이벤트가 아니라 <b>여기서 하는
 * 포그라운드 재등록</b>이 담당한다. 그 이벤트는 Chrome이 드물게만 쏘고 Firefox는 사실상 쏘지 않으며
 * 서비스워커의 크로스오리진 쿠키 fetch가 실패할 수 있다. 반면 이 훅은 세션이 보장된 상태에서 값싸게
 * 돌기 때문에, 조용히 회전한 endpoint까지 모든 드리프트를 자가 치유한다.
 */
export function usePushSubscription(): PushSubscriptionControls {
  const isAuthenticated = useSessionStore((s) => s.isAuthenticated);
  const [state, setState] = useState<PushState>("unsupported");
  const [snoozed, setSnoozed] = useState(isPromptSnoozed);
  const [enabling, setEnabling] = useState(false);
  const [installEvent, setInstallEvent] =
    useState<BeforeInstallPromptEvent | null>(null);

  // Android는 설치 없이 탭에서도 푸시가 되므로, 설치 제안은 푸시 상태를 가리지 않는 별도 축으로 둔다.
  useEffect(() => {
    const handler = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", handler);
    return () => window.removeEventListener("beforeinstallprompt", handler);
  }, []);

  useEffect(() => {
    let active = true;

    const resolveState = async () => {
      if (isInAppBrowser()) {
        if (active) setState("in-app-browser");
        return;
      }
      // 서버가 푸시를 끄면(공개키 없음) 어떤 안내도 띄우지 않는다.
      const publicKey = await fetchPublicKey();
      if (!active) return;
      if (!publicKey) {
        setState("unsupported");
        return;
      }
      if (isIos() && !isStandalone()) {
        setState("needs-install");
        return;
      }
      if (!supportsPush()) {
        setState("unsupported");
        return;
      }
      setState(Notification.permission as PushState);
    };

    void resolveState();
    return () => {
      active = false;
    };
  }, []);

  // 허용된 사용자는 포그라운드로 돌아올 때마다 구독을 확보하고 다시 등록한다(드리프트 자가 치유).
  useEffect(() => {
    if (state !== "granted" || !isAuthenticated) return;

    const revalidate = async () => {
      try {
        const publicKey = await fetchPublicKey();
        if (!publicKey) return;
        // 구독이 없으면 여기서 만든다. 권한이 granted인데 구독만 없는 상태를 복구하는 유일한 경로다.
        await postSubscription(await ensureSubscription(publicKey));
      } catch {
        // 재등록 실패는 다음 포그라운드 복귀에서 다시 시도되므로 조용히 넘어간다.
      }
    };

    void revalidate();
    const onVisible = () => {
      if (document.visibilityState === "visible") void revalidate();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [state, isAuthenticated]);

  const enable = useCallback(async () => {
    setEnabling(true);
    const showToast = useToastStore.getState().show;
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setState(permission as PushState);
        markPromptDismissed();
        setSnoozed(true);
        // denied(사용자가 차단)와 default(프롬프트를 닫았거나 브라우저가 조용히 억제)는 복구 방법이 다르다.
        // 하나로 뭉뚱그리면 프롬프트조차 못 본 사용자에게 엉뚱한 안내를 하게 된다.
        showToast(
          permission === "denied"
            ? {
                icon: "bell",
                title: "알림이 차단됐어요",
                description: "기기 설정 → 알림에서 쉼,부름을 허용할 수 있어요.",
                dedupeKey: "push-permission",
              }
            : {
                icon: "bell",
                title: "알림을 켜지 않았어요",
                description:
                  "주소창의 알림 아이콘에서 다시 허용할 수 있어요.",
                dedupeKey: "push-permission",
              },
        );
        return;
      }

      const publicKey = await fetchPublicKey();
      if (!publicKey) return;

      await postSubscription(await ensureSubscription(publicKey));
      setState("granted");
      // 과대 약속을 하지 않는다 - 푸시는 콜을 나르지 않고 "열어보라"고 깨우기만 한다.
      showToast({
        icon: "bell",
        title: "알림을 켰어요",
        description: "앱을 닫아도 놓친 알림을 알려드릴게요.",
        dedupeKey: "push-permission",
      });
    } catch {
      showToast({
        icon: "bell",
        title: "알림을 켜지 못했어요",
        description: "잠시 후 다시 시도해 주세요.",
        dedupeKey: "push-permission",
      });
    } finally {
      setEnabling(false);
    }
  }, []);

  const dismiss = useCallback(() => {
    markPromptDismissed();
    setSnoozed(true);
  }, []);

  const promptInstall = useCallback(async () => {
    if (!installEvent) return;
    await installEvent.prompt();
    setInstallEvent(null);
  }, [installEvent]);

  return {
    state,
    shouldPrompt: state === "default" && !snoozed && isAuthenticated,
    canInstall: installEvent !== null && !isStandalone(),
    enable,
    dismiss,
    promptInstall,
    enabling,
  };
}

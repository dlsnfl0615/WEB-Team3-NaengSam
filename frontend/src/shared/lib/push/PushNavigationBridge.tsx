import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useToastStore } from "@/shared/store/toastStore";

interface PushNavigationMessage {
  type: "PUSH_NAVIGATE";
  url: string;
}

function isPushNavigationMessage(data: unknown): data is PushNavigationMessage {
  if (!data || typeof data !== "object") return false;
  const message = data as Partial<PushNavigationMessage>;
  return (
    message.type === "PUSH_NAVIGATE" &&
    typeof message.url === "string" &&
    message.url.startsWith("/") &&
    !message.url.startsWith("//")
  );
}

/** 서비스워커의 알림 클릭을 SPA 이동과 최신 매칭 상태 복구로 연결한다. */
export function PushNavigationBridge() {
  const navigate = useNavigate();

  useEffect(() => {
    const syncMatching = () => {
      if (!useSessionStore.getState().isAuthenticated) return null;
      return useMatchingStore.getState().syncCurrentMatching();
    };

    const handleMessage = (event: MessageEvent<unknown>) => {
      if (!isPushNavigationMessage(event.data)) return;

      navigate(event.data.url);
      const isMatchingNotification =
        new URL(event.data.url, window.location.origin).pathname ===
        ROUTES.matching;
      const sync = syncMatching();
      if (!sync) return;
      void sync.then(() => {
        const { pendingOffer, incomingDreami } = useMatchingStore.getState();
        if (isMatchingNotification && !pendingOffer && !incomingDreami) {
          useToastStore.getState().show({
            icon: "bell",
            title: "요청이 이미 마감됐어요",
            dedupeKey: "offer-expired",
          });
        }
      });
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") void syncMatching();
    };

    navigator.serviceWorker?.addEventListener("message", handleMessage);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      navigator.serviceWorker?.removeEventListener("message", handleMessage);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [navigate]);

  return null;
}

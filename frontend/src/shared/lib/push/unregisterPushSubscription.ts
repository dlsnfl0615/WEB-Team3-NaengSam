import { api } from "@/shared/api";

/**
 * 서버에 등록된 이 기기의 푸시 구독을 지운다. **로그아웃 요청 전에** 호출해야 한다 —
 * 로그아웃 후에는 세션이 없어 이 API를 부를 수 없다.
 *
 * <p>브라우저 쪽 구독은 일부러 해제하지 않는다({@code subscription.unsubscribe()}를 부르지 않는다).
 * 알림 권한이 남아 있으므로 다시 로그인하면 두 번째 권한 프롬프트 없이 조용히 재등록된다. 전달을
 * 실제로 막는 것은 서버의 구독 행이라 공용 기기에서도 안전하다.
 *
 * <p>실패해도 로그아웃을 막지 않는다. 남은 행은 다음 로그인의 재등록이나 서버의 만료 정리가 걷어간다.
 */
export async function unregisterPushSubscription(): Promise<void> {
  try {
    if (!("serviceWorker" in navigator)) return;
    const registration = await navigator.serviceWorker.getRegistration();
    const subscription = await registration?.pushManager.getSubscription();
    if (!subscription) return;
    await api.deleteSubscription({ endpoint: subscription.endpoint });
  } catch {
    // 로그아웃 경로를 절대 막지 않는다.
  }
}

import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, MapCard, ScreenShell, TopBar } from "@/shared/ui";
import { useRole } from "@/shared/lib/role/useRole";
import { useDeliveryStore } from "@/shared/store/deliveryStore";

/**
 * 매칭(찾는 중) 화면(Figma node 191:763).
 * 지도 위에서 대기 상태를 보여준다. 실제 오퍼/콜 팝업은 전역 `MatchingPopup`이
 * 담당하므로 다른 화면으로 이동해도 이어서 뜬다.
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const { role } = useRole();
  const startSeeking = useDeliveryStore((s) => s.startSeeking);

  const isDriver = role === "드리미";
  const counterpart = isDriver ? "부르미" : "드리미";

  // 드리미: 매칭 진입 시 콜 탐색 시작(전역 콜 팝업 트리거).
  useEffect(() => {
    if (isDriver) startSeeking();
  }, [isDriver, startSeeking]);

  return (
    <ScreenShell>
      <TopBar
        title={`${counterpart}를 찾는 중`}
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <MapCard height={280} />

        <Card className="flex flex-col gap-1">
          <p className="flex items-center gap-2 text-base font-bold text-navy-900">
            <span className="size-2 rounded-pill bg-teal-500" />
            {isDriver
              ? "근방 300m 내 부름 5건 대기중"
              : "근방 300m 내 드리미 5명 대기중"}
          </p>
          <p className="text-2xs text-muted">
            요청을 보낸 {counterpart}의 수락을 기다리고 있어요...
          </p>
        </Card>
      </main>
    </ScreenShell>
  );
}

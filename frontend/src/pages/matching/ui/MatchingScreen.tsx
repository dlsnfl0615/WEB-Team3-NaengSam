import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, MapCard, ScreenShell, TopBar } from "@/shared/ui";
import { useRole } from "@/shared/lib/role/useRole";
import { useMatchingStore } from "@/shared/store/matchingStore";

/**
 * 매칭(찾는 중) 화면(Figma node 191:763).
 * 지도 위에서 대기 상태를 보여준다. 실제 오퍼/콜 팝업은 전역 `MatchingPopup`이
 * 담당하므로 다른 화면으로 이동해도 이어서 뜬다.
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const { role } = useRole();
  const goOnline = useMatchingStore((s) => s.goOnline);
  const loadNearbyCalls = useMatchingStore((s) => s.loadNearbyCalls);
  const nearbyCalls = useMatchingStore((s) => s.nearbyCalls);

  const isDriver = role === "드리미";
  const counterpart = isDriver ? "부르미" : "드리미";

  // 드리미: 진입 시 온라인 전환 + 주변 콜 조회. 오퍼 팝업은 전역 `MatchingPopup`이 받는다.
  // 화면을 떠나도 온라인은 유지한다(오프라인 전환은 명시적 토글로만).
  useEffect(() => {
    if (!isDriver) return;
    void goOnline().then(() => {
      // 온라인 전환이 실패(위치 권한 거부 등)했으면 콜 조회도 의미 없다.
      if (useMatchingStore.getState().online) void loadNearbyCalls();
    });
  }, [isDriver, goOnline, loadNearbyCalls]);

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
              ? `근방 3km 내 부름 ${nearbyCalls.length}건 대기중`
              : `${counterpart}를 찾고 있어요`}
          </p>
          <p className="text-2xs text-muted">
            요청을 보낸 {counterpart}의 수락을 기다리고 있어요...
          </p>
        </Card>
      </main>
    </ScreenShell>
  );
}

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Card, MapCard, ScreenShell, TopBar } from "@/shared/ui";
import { DriverOffer } from "./DriverOffer";

/**
 * 드리미 매칭(찾는 중) 화면(Figma node 191:763).
 * 지도 위에서 대기 상태를 보여주고, 도착한 드리미 요청을 수락·거절합니다(UI 전용).
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const [offerVisible, setOfferVisible] = useState(true);

  return (
    <ScreenShell>
      <TopBar
        title="드리미를 찾는 중"
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <MapCard height={280} />

        <Card className="flex flex-col gap-1">
          <p className="flex items-center gap-2 text-base font-bold text-navy-900">
            <span className="size-2 rounded-pill bg-teal-500" />
            근방 300m 내 드리미 5명 대기중
          </p>
          <p className="text-2xs text-muted">
            요청을 보낸 드리미의 수락을 기다리고 있어요...
          </p>
        </Card>
      </main>

      {offerVisible && (
        <footer className="pt-3">
          <DriverOffer
            name="드리미 '핀'"
            rating={4.9}
            deliveries={132}
            distance="120m"
            onReject={() => setOfferVisible(false)}
            onAccept={() => setOfferVisible(false)}
          />
        </footer>
      )}
    </ScreenShell>
  );
}

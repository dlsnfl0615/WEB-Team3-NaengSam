import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Icon, MapCard, ScreenShell, TopBar } from "@/shared/ui";
import { TrackOverlay } from "./TrackOverlay";

/**
 * 실시간 배송 추적 화면(Figma node 191:972, 191:989).
 * 픽업 중 → 배송 중 두 단계를 로컬 상태로 전환합니다(UI 전용).
 */
export function DeliveryTrackScreen() {
  const navigate = useNavigate();
  const [stage, setStage] = useState<"pickup" | "delivery">("pickup");
  const isPickup = stage === "pickup";

  return (
    <ScreenShell>
      <TopBar
        title="실시간 배송 추적"
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <MapCard
          height={380}
          overlay={<TrackOverlay eta="3분" distance="450m" />}
        />

        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          {isPickup ? "물품을 픽업 중이에요" : "물품을 배송 중이에요"}
        </h1>

        <Card className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-pill bg-teal-50 text-teal-700">
            <Icon name="pin" size={18} />
          </span>
          <div className="flex flex-col">
            <span className="text-2xs text-muted">도착지</span>
            <span className="text-md font-bold text-navy-900">A동 102호</span>
          </div>
        </Card>
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <div className="flex w-full gap-2">
          <Button variant="outline">연락하기</Button>
          <Button
            block
            onClick={() => (isPickup ? setStage("delivery") : navigate(-1))}
          >
            {isPickup ? "픽업 완료" : "전달 완료"}
          </Button>
        </div>
        {isPickup && (
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="text-2xs text-muted"
          >
            배달 취소하기 (픽업 전에만 가능)
          </button>
        )}
      </footer>
    </ScreenShell>
  );
}

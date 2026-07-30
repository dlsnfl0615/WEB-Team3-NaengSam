import { useNavigate } from "react-router-dom";
import { Button, Card, Icon, MapCard, ScreenShell } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import {
  useActiveDelivery,
  useDeliveryStore,
} from "@/shared/store/deliveryStore";
import { TRACK_STAGES, type TrackStage } from "./statuses";
import { TrackOverlay } from "./TrackOverlay";

/**
 * 실시간 배송 추적 화면(Figma node 191:972, 191:989).
 * 지도 풀블리드 + 지도 위 뒤로가기. 활성 배달의 픽업중 → 배송중 → 완료를 전역 스토어로 전환합니다(URL 미노출).
 */
export function DeliveryTrackScreen() {
  const navigate = useNavigate();
  const active = useActiveDelivery();
  const advance = useDeliveryStore((s) => s.advance);
  const complete = useDeliveryStore((s) => s.complete);
  const cancel = useDeliveryStore((s) => s.cancel);

  // 드리미 화면은 픽업중/배송중만 다룬다(그 외 상태는 배송중으로 취급).
  const stage: TrackStage = active?.status === "배송중" ? "배송중" : "픽업중";
  const isPickup = stage === "픽업중";
  const { title, action, cancelable } = TRACK_STAGES[stage];

  const destination = active?.dropoff ?? "A동 102호";
  const eta = active?.eta ?? "3분";
  const distance = active?.distance ?? "450m";

  const onAction = async () => {
    if (isPickup) {
      await advance();
    } else {
      await complete();
      navigate(ROUTES.deliveryComplete, { replace: true });
    }
  };

  const onCancel = async () => {
    await cancel("픽업 전 취소");
    navigate(ROUTES.home, { replace: true });
  };

  return (
    <ScreenShell>
      {/* 풀블리드 지도 + 지도 위 뒤로가기 */}
      <div className="relative -mx-4 -mt-6">
        <MapCard
          flat
          height={440}
          overlay={<TrackOverlay eta={eta} distance={distance} />}
        />
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="뒤로가기"
          className="absolute left-4 top-5 text-navy-900"
        >
          <Icon name="back" size={20} />
        </button>
      </div>

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          {title}
        </h1>

        <Card className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-pill bg-teal-50 text-teal-700">
            <Icon name="pin" size={18} />
          </span>
          <div className="flex flex-col">
            <span className="text-2xs text-muted">도착지</span>
            <span className="text-md font-bold text-navy-900">{destination}</span>
          </div>
        </Card>
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <div className="flex w-full gap-2">
          <Button variant="outline">연락하기</Button>
          <Button block onClick={onAction}>
            {action}
          </Button>
        </div>
        {cancelable && (
          <button
            type="button"
            onClick={onCancel}
            className="text-2xs text-muted"
          >
            배달 취소하기 (픽업 전에만 가능)
          </button>
        )}
      </footer>
    </ScreenShell>
  );
}

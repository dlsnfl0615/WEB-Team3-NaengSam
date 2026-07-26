import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Badge,
  Button,
  IconChip,
  MapCard,
  RouteCard,
  ScreenShell,
  TopBar,
  toneForStatus,
} from "@/shared/ui";
import { DETAIL_STATUSES, type DetailStatus } from "./statuses";

/**
 * 드림 상세 화면(Figma node 191:802, 827, 849, 870).
 * ?status=픽업중|배송중|지연 으로 상태별 문구·오버레이·액션이 바뀝니다(UI 전용).
 */
export function DeliveryDetailScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const raw = params.get("status") as DetailStatus | null;
  const status: DetailStatus = raw && raw in DETAIL_STATUSES ? raw : "픽업중";
  const { title, showEta, cancelable } = DETAIL_STATUSES[status];

  return (
    <ScreenShell>
      <TopBar title="드림 상세" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center gap-3">
          <IconChip name="package" size={44} />
          <div className="flex flex-col gap-0.5">
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
                {title}
              </h1>
              <Badge tone={toneForStatus(status)}>{status}</Badge>
            </div>
            <p className="text-2xs text-muted">드리미 '핀'이 출발지 도착</p>
          </div>
        </div>

        <MapCard
          height={340}
          overlay={
            <div className="flex items-center gap-6 rounded-pill bg-navy-900 px-6 py-2.5 text-white">
              {showEta && (
                <div className="flex flex-col items-center">
                  <span className="text-2xs opacity-70">예상 도착</span>
                  <span className="text-md font-bold">3분</span>
                </div>
              )}
              <div className="flex flex-col items-center">
                <span className="text-2xs opacity-70">남은 거리</span>
                <span className="text-md font-bold">450m</span>
              </div>
            </div>
          }
        />

        <RouteCard origin="A동 102호" destination="B동 405호" />
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <Button block>연락하기</Button>
        {cancelable && (
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

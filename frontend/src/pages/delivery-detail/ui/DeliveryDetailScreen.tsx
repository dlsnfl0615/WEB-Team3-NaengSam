import { useNavigate } from "react-router-dom";
import {
  Button,
  MapCard,
  RouteCard,
  ScreenShell,
  TopBar,
} from "@/shared/ui";
import { useDeliveryStore } from "@/shared/store/deliveryStore";
import { DETAIL_STATUSES } from "./statuses";

/**
 * 드림 상세 화면(Figma node 191:802, 827, 849, 870).
 * 전역 스토어의 배달 상태에 따라 문구·오버레이·액션이 바뀝니다(URL 미노출).
 */
export function DeliveryDetailScreen() {
  const navigate = useNavigate();
  const status = useDeliveryStore((s) => s.status);
  const { title, showEta, cancelable } = DETAIL_STATUSES[status];

  return (
    <ScreenShell>
      <TopBar title="드림 상세" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center gap-3">
          <span className="size-11 shrink-0 rounded-pill bg-teal-50" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
              {title}
            </h1>
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

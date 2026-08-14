import { useSearchParams } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import {
  Button,
  MapCard,
  RouteCard,
  ScreenShell,
  TopBar,
} from "@/shared/ui";
import { useActiveDelivery } from "@/shared/store/deliveryStore";
import { DeliveryStatusResponseDtoStatus } from "@/shared/api";
import { DETAIL_STATUSES, type DetailStatus } from "./statuses";
import { RealDeliveryTracking } from "./RealDeliveryTracking";

/** URL `?status=` 문자열을 유효한 DeliveryCd로만 좁힌다(그 외/없음이면 undefined). */
function parseStatusParam(
  raw: string | null,
): DeliveryStatusResponseDtoStatus | undefined {
  if (raw && raw in DeliveryStatusResponseDtoStatus) {
    return DeliveryStatusResponseDtoStatus[
      raw as keyof typeof DeliveryStatusResponseDtoStatus
    ];
  }
  return undefined;
}

/**
 * 드림 상세 화면(Figma node 191:802, 827, 849, 870).
 * 활성 배달 상태에 따라 문구·오버레이·액션이 바뀝니다(URL 미노출).
 *
 * 실 백엔드 모드: `?orderId=` 가 있으면 SSE로 실시간 추적하는 부르미 수령인 화면으로 동작한다.
 * orderId 가 없으면 기존 mock 흐름(전역 스토어 구독)을 그대로 탄다.
 */
export function DeliveryDetailScreen() {
  const backOrHome = useBackOrHome();
  const [params] = useSearchParams();
  const orderId = params.get("orderId");
  const active = useActiveDelivery();

  if (orderId) {
    return (
      <RealDeliveryTracking
        orderId={orderId}
        initialStatus={parseStatusParam(params.get("status"))}
      />
    );
  }

  const detailStatus: DetailStatus =
    active?.status === "배송중" ? "배송중" : "픽업중";
  const { title, showEta, cancelable } = DETAIL_STATUSES[detailStatus];

  const origin = active?.pickup ?? "A동 102호";
  const destination = active?.dropoff ?? "B동 405호";
  const driverName = active?.driverName ?? "핀";
  const eta = active?.eta ?? "3분";
  const distance = active?.distance ?? "450m";

  return (
    <ScreenShell>
      <TopBar title="드림 상세" onBack={backOrHome} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center gap-3">
          <span className="size-11 shrink-0 rounded-pill bg-teal-50" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
              {title}
            </h1>
            <p className="text-2xs text-muted">드리미 '{driverName}'이 출발지 도착</p>
          </div>
        </div>

        <MapCard
          height={340}
          overlay={
            <div className="flex items-center gap-6 rounded-pill bg-navy-900 px-6 py-2.5 text-white">
              {showEta && (
                <div className="flex flex-col items-center">
                  <span className="text-2xs opacity-70">예상 도착</span>
                  <span className="text-md font-bold">{eta}</span>
                </div>
              )}
              <div className="flex flex-col items-center">
                <span className="text-2xs opacity-70">남은 거리</span>
                <span className="text-md font-bold">{distance}</span>
              </div>
            </div>
          }
        />

        <RouteCard origin={origin} destination={destination} />
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <Button block>연락하기</Button>
        {cancelable && (
          <button
            type="button"
            onClick={backOrHome}
            className="text-2xs text-muted"
          >
            배달 취소하기 (픽업 전에만 가능)
          </button>
        )}
      </footer>
    </ScreenShell>
  );
}

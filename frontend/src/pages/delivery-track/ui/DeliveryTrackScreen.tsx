import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Button,
  BlockingLoadErrorModal,
  Card,
  Icon,
  MapCard,
  Modal,
  ScreenShell,
  DeliveryRouteMap,
} from "@/shared/ui";
import { api, isApiError, DeliveryStatusResponseDtoStatus } from "@/shared/api";
import type {
  DeliveryStatusResponseDto,
  DreamiLocationResponseDto,
  RoutePointDto,
} from "@/shared/api";
import {
  recallDeliveryStage,
  rememberDeliveryStage,
  getUntrackableDeliveryNotice,
  useDeliveryDetailGate,
  useSse,
  useSseReconnectSync,
  useDreamiLocationBroadcast,
  formatArrivalTime,
  type SseHandlers,
} from "@/shared/lib";

/** 좌표가 온전한 점만 골라 지도 폴리라인용 배열로 변환한다. */
function onlyValidCoords(points: RoutePointDto[] | undefined) {
  return points
    ?.filter(
      (p): p is { latitude: number; longitude: number } =>
        p.latitude != null && p.longitude != null,
    )
    .map((p) => ({ latitude: p.latitude, longitude: p.longitude }));
}
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
 *
 * 실 백엔드 모드: `?orderId=` 가 있으면 /delivery-test 에서 실제 배달을 들고 넘어온 것으로 보고,
 * 픽업 완료를 mock 이 아니라 사진 인증 화면(/delivery-proof)으로 넘겨 실제 pickup-finish 로 처리한다.
 * `?status=DELIVERING` 이면 pickup-finish 후 배송중으로 돌아온 상태다. orderId 가 없으면 기존 mock 흐름.
 */
export function DeliveryTrackScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const orderId = params.get("orderId");
  const statusParam = params.get("status");
  const isRealMode = Boolean(orderId);

  // 실 배달의 상세 조회가 성공해야만 위치 전송·SSE·상태 전이 기능을 활성화한다.
  const {
    detail,
    ready: detailReady,
    loading: detailLoading,
    blockingModal,
    retry: retryDeliveryDetail,
    refresh: refreshDeliveryDetail,
    block: blockDeliveryDetail,
  } = useDeliveryDetailGate(orderId, { enabled: isRealMode });

  // 서버가 위치 전송 응답으로 내려준 '드리미→픽업지' 경로·배송완료예상시간을 담아둔다.
  // 아직 경로가 없는 동안에만 includeRoute=true로 요청하고, 한 번 받으면 false가 돼 이후엔 좌표를 중복 수신하지 않는다.
  const [livePickupRoute, setLivePickupRoute] = useState<RoutePointDto[]>();
  const [liveCompletionTime, setLiveCompletionTime] = useState<string>();
  const includeRoute = livePickupRoute === undefined;
  const handleLocationResult = useCallback(
    (result: DreamiLocationResponseDto | undefined) => {
      if (!result) return;
      // 이미 담아둔 값이 있으면 유지한다(같은 좌표 배열로 다시 세팅하면 폴리라인이 깜빡이므로 한 번만 반영).
      const route = result.deliveryRoutePath;
      if (route?.length) setLivePickupRoute((prev) => prev ?? route);
      const completion = result.estimatedCompletionTime;
      if (completion) setLiveCompletionTime((prev) => prev ?? completion);
    },
    [],
  );

  // 실 모드(드리미)에서만 현재 GPS 위치를 5초 주기로 백엔드에 전송한다(픽업중·배송중 모두 커버).
  // 반환된 최신 좌표는 이 화면 지도에도 표시하고, 전송 응답(경로·배송완료예상시간)은 위 콜백으로 받아 화면에 반영한다.
  // 이 position은 서버에서 반환하는게 아니라, 브라우저에서 측정한 GPS 값임
  const { position } = useDreamiLocationBroadcast(orderId, {
    enabled: isRealMode && detailReady,
    includeRoute,
    onResult: handleLocationResult,
  });
  const active = useActiveDelivery();
  const advance = useDeliveryStore((s) => s.advance);
  const complete = useDeliveryStore((s) => s.complete);
  const cancel = useDeliveryStore((s) => s.cancel);

  // 실 모드에서 조회한 출발지·도착지 좌표(+도착지 주소)를 사용한다(엔드포인트가 드리미도 허용).
  const pickup =
    detail?.originLatitude != null && detail.originLongitude != null
      ? {
          latitude: detail.originLatitude,
          longitude: detail.originLongitude,
        }
      : undefined;
  const dropoff =
    detail?.destinationLatitude != null && detail.destinationLongitude != null
      ? {
          latitude: detail.destinationLatitude,
          longitude: detail.destinationLongitude,
        }
      : undefined;
  const destAddress = detail?.destinationAddressLine1;
  // 지도 폴리라인용 경로. 리렌더(위치 갱신 등)마다 새 배열을 만들면 폴리라인이 다시 그려지므로 useMemo로 참조를 고정한다.
  // 픽업 전에는 드리미→픽업지(Delivery, 위치 전송 응답이 오면 그 값 우선), 픽업 후에는 픽업지→도착지(Order) 경로를 쓴다.
  const orderRoutePath = useMemo(
    () => onlyValidCoords(detail?.routePath),
    [detail?.routePath],
  );
  const deliveryRoutePath = useMemo(
    () => onlyValidCoords(livePickupRoute ?? detail?.deliveryRoutePath),
    [livePickupRoute, detail?.deliveryRoutePath],
  );

  // 픽업 취소 확인 모달 상태
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const sseHandlers: SseHandlers = {
    delivery_cancelled: (data) => {
      const dto = data as DeliveryStatusResponseDto;
      if (dto?.orderId !== orderId) return;
      const cancelledStatus =
        dto.status ?? DeliveryStatusResponseDtoStatus.PICKUP_CANCELLED_BY_ADMIN;
      const notice = getUntrackableDeliveryNotice(cancelledStatus);
      rememberDeliveryStage(orderId, cancelledStatus);
      setConfirmOpen(false);
      blockDeliveryDetail({
        title: notice?.title ?? "배달이 취소됐어요",
        message:
          dto.message ?? notice?.message ?? "상대방이 배달을 취소했어요.",
      });
    },
  };

  // 실 모드에서만 드리미 세션으로 SSE를 구독한다(mock 모드는 구독하지 않음).
  const { status: sseStatus } = useSse(sseHandlers, {
    enabled: isRealMode && detailReady,
  });

  // 위치 전송 폴링과 별개로, SSE 재연결 시 스냅샷을 다시 맞춰 놓친 취소 등을 복구한다.
  useSseReconnectSync(sseStatus, refreshDeliveryDetail, {
    enabled: isRealMode && detailReady,
  });

  // 배송중으로 넘어온 순간을 기록해 둔다. 홈 카드로 다시 들어오면 `?status=` 가 없어
  // 픽업중으로 되돌아가므로, 그때 이 스냅샷으로 단계를 복원한다.
  useEffect(() => {
    if (orderId && statusParam === DeliveryStatusResponseDtoStatus.DELIVERING) {
      rememberDeliveryStage(
        orderId,
        DeliveryStatusResponseDtoStatus.DELIVERING,
      );
    }
  }, [orderId, statusParam]);

  // 드리미 화면은 픽업중/배송중만 다룬다(그 외 상태는 배송중으로 취급).
  // 실 모드는 status 파라미터(없으면 마지막 스냅샷)로, mock 모드는 활성 배달 상태로 단계를 결정한다.
  const realStage =
    statusParam ?? (orderId ? recallDeliveryStage(orderId) : undefined);
  const stage: TrackStage = isRealMode
    ? realStage === DeliveryStatusResponseDtoStatus.DELIVERING
      ? "배송중"
      : "픽업중"
    : active?.status === "배송중"
      ? "배송중"
      : "픽업중";
  const isPickup = stage === "픽업중";
  const { title, action, cancelable } = TRACK_STAGES[stage];

  const destination = destAddress ?? active?.dropoff ?? "A동 102호";
  // 픽업 전엔 드리미→픽업지, 픽업 후엔 픽업지→도착지 경로를 그린다(실 모드에서만; mock 모드는 경로 없음).
  const route = isPickup ? deliveryRoutePath : orderRoutePath;
  // 배송완료예상시간은 위치 전송 응답으로 받은 값을 우선 쓰고, 없으면 상세 조회 값으로 대체한다.
  const arrivalTime = formatArrivalTime(
    liveCompletionTime ?? detail?.estimatedCompletionTime,
  );

  const onAction = async () => {
    if (!detailReady) return;
    if (isRealMode) {
      // 실 모드: 픽업 완료/전달 완료 모두 사진 인증 화면에서 presign+업로드 후
      // pickup-finish / finish 로 처리한다(둘 다 인증 사진 필수).
      const intent = isPickup ? "pickup" : "finish";
      navigate(
        `${ROUTES.deliveryProof}?mode=photo&orderId=${orderId}&intent=${intent}`,
      );
      return;
    }
    if (isPickup) {
      await advance();
    } else {
      await complete();
      navigate(ROUTES.deliveryComplete, { replace: true });
    }
  };

  // 취소 버튼 클릭 → 바로 취소하지 않고 확인 모달을 띄운다.
  const onCancel = () => {
    if (!detailReady) return;
    setCancelError(null);
    setConfirmOpen(true);
  };

  // 모달에서 "취소하기" 확정 시 실제로 취소를 진행한다.
  // 실 모드는 백엔드에 드리미 픽업 취소를 요청하고, mock 모드는 기존 스토어 흐름을 탄다.
  const confirmCancel = async () => {
    if (canceling || !detailReady) return;
    setCanceling(true);
    setCancelError(null);
    try {
      if (isRealMode && orderId) {
        await api.cancelByDreami(orderId);
      } else {
        await cancel("픽업 전 취소");
      }
      setConfirmOpen(false);
      navigate(ROUTES.home, { replace: true });
    } catch (e) {
      setCancelError(
        isApiError(e)
          ? e.message
          : "픽업 취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      {/* 풀블리드 지도 + 지도 위 뒤로가기 */}
      <div className="relative -mx-4 -mt-6">
        <MapCard
          flat
          height={440}
          overlay={<TrackOverlay arrivalTime={arrivalTime} />}
        >
          <DeliveryRouteMap
            flat
            pickup={pickup}
            dropoff={dropoff}
            driver={position ?? undefined}
            route={route}
            height={440}
          />
        </MapCard>
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
            <span className="text-md font-bold text-navy-900">
              {destination}
            </span>
          </div>
        </Card>
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <div className="flex w-full gap-2">
          <Button variant="outline">연락하기</Button>
          <Button block disabled={!detailReady} onClick={onAction}>
            {action}
          </Button>
        </div>
        {detailReady && cancelable && (
          <button
            type="button"
            onClick={onCancel}
            className="text-2xs text-muted"
          >
            배달 취소하기 (픽업 전에만 가능)
          </button>
        )}
      </footer>

      <Modal
        open={confirmOpen}
        label="픽업 취소 확인"
        onClose={canceling ? undefined : () => setConfirmOpen(false)}
      >
        <Card className="flex flex-col gap-4 text-center">
          <div className="flex flex-col gap-1">
            <h2 className="text-md font-bold text-navy-900">
              정말로 취소하시겠습니까?
            </h2>
            <p className="text-2xs text-muted">
              픽업을 취소하면 배달이 종료돼요.
            </p>
          </div>
          {cancelError && (
            <p className="text-2xs text-status-danger">{cancelError}</p>
          )}
          <div className="flex gap-2">
            <Button
              variant="outline"
              block
              disabled={canceling}
              onClick={() => setConfirmOpen(false)}
            >
              돌아가기
            </Button>
            <Button block disabled={canceling} onClick={confirmCancel}>
              {canceling ? "취소 중…" : "취소하기"}
            </Button>
          </div>
        </Card>
      </Modal>

      <BlockingLoadErrorModal
        open={blockingModal.open}
        title={blockingModal.title}
        message={blockingModal.message}
        guidance={blockingModal.guidance}
        retrying={detailLoading}
        canRetry={blockingModal.canRetry}
        onRetry={retryDeliveryDetail}
        onExit={() => navigate(ROUTES.home, { replace: true })}
      />
    </ScreenShell>
  );
}

import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  BlockingLoadErrorModal,
  Card,
  DeliveryRouteMap,
  Icon,
  MapCard,
  Modal,
  ScreenShell,
  Toast,
  TopBar,
} from "@/shared/ui";
import type { Coords } from "@/shared/ui";
import {
  recallDeliveryStage,
  rememberDeliveryStage,
  getUntrackableDeliveryNotice,
  useDeliveryDetailGate,
  useSse,
  useSseReconnectSync,
  formatArrivalTime,
  type SseHandlers,
} from "@/shared/lib";
import { ROUTES } from "@/shared/config/routes";
import { api, isApiError, DeliveryStatusResponseDtoStatus } from "@/shared/api";
import type {
  DeliveryLocationDto,
  DeliveryStatusResponseDto,
} from "@/shared/api";
import { realTrackView } from "./statuses";

interface RealDeliveryTrackingProps {
  /** 추적할 주문 UUID(URL `?orderId=`). */
  orderId: string;
  /** 초기 상태(URL `?status=`, 없으면 픽업중). SSE 이벤트로 이후 갱신된다. */
  initialStatus?: DeliveryStatusResponseDtoStatus;
}

/** 진행 중 알림 토스트가 화면에 머무는 시간. */
const TRANSIENT_TOAST_MS = 4000;

/**
 * 부르미(수령인) 실시간 배송 추적 — 실 백엔드 모드.
 *
 * 마운트 시 `GET /api/v1/delivery/orders/{orderId}`로 출발지·도착지 좌표(+초기 드리미 위치)를 1회 받고,
 * `GET /api/v1/sse/subscribe` 스트림을 구독해 드리미 위치·상태 전이를 이후 갱신한다.
 * 초기 상태는 URL(`?status=`)/기본값으로 두고, 한 연결에 여러 주문 이벤트가 섞일 수 있어 orderId로 필터한다.
 */
export function RealDeliveryTracking({
  orderId,
  initialStatus,
}: RealDeliveryTrackingProps) {
  const navigate = useNavigate();

  // URL이 알려준 상태 > 마지막으로 관측한 상태 > 픽업중. 홈 카드로 재진입하면 URL에
  // 상태가 없어 픽업중으로 되돌아가므로 스냅샷으로 복원한다.
  const [status, setStatus] = useState<DeliveryStatusResponseDtoStatus>(
    initialStatus ??
      recallDeliveryStage(orderId) ??
      DeliveryStatusResponseDtoStatus.PICKUP_NORMAL,
  );

  // location이 바뀌면 RealDeliveryTracking을 다시 렌더링
  const [location, setLocation] = useState<DeliveryLocationDto | null>(null);

  // 상세 조회 성공 전에는 SSE·취소 등 모든 배달 기능을 차단한다.
  const {
    detail,
    ready: detailReady,
    loading: detailLoading,
    blockingModal,
    retry: retryDeliveryDetail,
    refresh: refreshDeliveryDetail,
    block: blockDeliveryDetail,
  } = useDeliveryDetailGate(orderId, {
    onLoaded: (loadedDetail) => {
      // SSE가 아직 위치를 안 줬으면 응답의 currentLocation으로 시드한다.
      setLocation(loadedDetail.currentLocation ?? null);
      if (loadedDetail.status) setStatus(loadedDetail.status);
    },
  });
  const pickup: Coords | undefined =
    detail?.originLatitude != null && detail.originLongitude != null
      ? {
          latitude: detail.originLatitude,
          longitude: detail.originLongitude,
        }
      : undefined;
  const dropoff: Coords | undefined =
    detail?.destinationLatitude != null && detail.destinationLongitude != null
      ? {
          latitude: detail.destinationLatitude,
          longitude: detail.destinationLongitude,
        }
      : undefined;
  // 백엔드가 내려준 카카오 추천 이동경로. 좌표가 온전한 점만 고르고,
  // 위치 SSE로 리렌더돼도 같은 배열을 재사용해 폴리라인을 다시 만들지 않는다.
  const orderRoutePath: Coords[] | undefined = useMemo(
    () =>
      detail?.routePath
        ?.filter(
          (p): p is { latitude: number; longitude: number } =>
            p.latitude != null && p.longitude != null,
        )
        .map((p) => ({ latitude: p.latitude, longitude: p.longitude })),
    [detail?.routePath],
  );
  const deliveryRoutePath: Coords[] | undefined = useMemo(
    () =>
      detail?.deliveryRoutePath
        ?.filter(
          (p): p is { latitude: number; longitude: number } =>
            p.latitude != null && p.longitude != null,
        )
        .map((p) => ({ latitude: p.latitude, longitude: p.longitude })),
    [detail?.deliveryRoutePath],
  );
  // 픽업 전에는 드리미→픽업지(Delivery), 픽업 후에는 픽업지→도착지(Order) 경로를 그린다.
  const isPickup =
    status === DeliveryStatusResponseDtoStatus.PICKUP_NORMAL ||
    status === DeliveryStatusResponseDtoStatus.PICKUP_DELAYED;
  const routePath = isPickup ? deliveryRoutePath : orderRoutePath;
  const arrivalTime = formatArrivalTime(detail?.estimatedCompletionTime);
  const [toast, setToast] = useState<{
    title: string;
    description?: string;
  } | null>(null);

  // 부르미 취소(확인 모달) 상태.
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const toastTimer = useRef<number | null>(null);

  // 상태 전이는 항상 스냅샷에 남긴다(재진입 시 픽업중으로 되돌아가지 않도록).
  const applyStatus = (next: DeliveryStatusResponseDtoStatus) => {
    setStatus(next);
    rememberDeliveryStage(orderId, next);
  };

  // SSE로 취소 상태를 받으면 토스트·지연 이동 대신 즉시 차단 모달로 전환한다.
  const showCancellationModal = (
    nextStatus: DeliveryStatusResponseDtoStatus,
    message?: string,
  ) => {
    const notice = getUntrackableDeliveryNotice(nextStatus);
    if (toastTimer.current !== null) {
      clearTimeout(toastTimer.current);
      toastTimer.current = null;
    }
    applyStatus(nextStatus);
    setConfirmOpen(false);
    setToast(null);
    blockDeliveryDetail({
      title: notice?.title ?? "배달이 종료됐어요",
      message:
        message ??
        notice?.message ??
        "종료된 배달은 더 이상 추적할 수 없어요.",
    });
  };

  // 진행 중 알림 토스트: 잠시 노출 후 자동으로 사라진다.
  const showTransientToast = (t: { title: string; description?: string }) => {
    setToast(t);
    if (toastTimer.current !== null) clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(
      () => setToast(null),
      TRANSIENT_TOAST_MS,
    );
  };

  useEffect(
    () => () => {
      if (toastTimer.current !== null) clearTimeout(toastTimer.current);
    },
    [],
  );

  // 이 주문의 이벤트만 통과시킨다.
  const forThisOrder = (data: unknown): DeliveryStatusResponseDto | null => {
    const dto = data as DeliveryStatusResponseDto;
    return dto?.orderId === orderId ? dto : null;
  };

  // handlers는 useSse 내부에서 ref로 최신화되므로 매 렌더 새로 만들어도 연결은 재생성되지 않는다.
  const handlers: SseHandlers = {
    // "delivery_location"은 백엔드에서 결정한 이름
    delivery_location: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      if (dto.currentLocation) setLocation(dto.currentLocation);
      // 첫 위치가 서버에 도달하면 '드리미→픽업지' 경로·배송완료예상시간이 계산된다.
      // 아직 최초 조회에 안 담겼으면 이 시점에 상세를 조용히 다시 불러온다(채워지면 조건이 false가 돼 멈춘다).
      const missingRoute =
        !detail?.estimatedCompletionTime ||
        (detail?.deliveryRoutePath?.length ?? 0) === 0;
      if (missingRoute) refreshDeliveryDetail();
    },
    // "delivery_delivering" 이라는 단어는 백엔드에서 결정한 이름
    delivery_delivering: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      applyStatus(dto.status ?? DeliveryStatusResponseDtoStatus.DELIVERING);
      showTransientToast({
        title: "드리미가 픽업을 완료했어요",
        description: dto.message ?? "지금부터 배송을 시작해요.",
      });
    },
    // "delivery_completed" 이라는 단어는 백엔드에서 결정한 이름임!!
    delivery_completed: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      const completedStatus =
        dto.status ?? DeliveryStatusResponseDtoStatus.DELIVERED;
      if (toastTimer.current !== null) {
        clearTimeout(toastTimer.current);
        toastTimer.current = null;
      }
      applyStatus(completedStatus);
      setToast(null);
      navigate(ROUTES.deliveryComplete, { replace: true });
    },
    delivery_cancelled: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      showCancellationModal(
        dto.status ?? DeliveryStatusResponseDtoStatus.PICKUP_CANCELLED_BY_ADMIN,
        dto.message,
      );
    },
  };

  const { connected, status: sseStatus } = useSse(handlers, {
    enabled: detailReady,
  });

  // SSE가 끊긴 사이 놓친 위치·상태 전이(배송중/취소/완료)를 재연결 시 스냅샷 재조회로 복구한다.
  useSseReconnectSync(sseStatus, refreshDeliveryDetail, {
    enabled: detailReady,
  });

  const view = realTrackView(status);
  const driver: Coords | undefined =
    location?.latitude != null && location?.longitude != null
      ? { latitude: location.latitude, longitude: location.longitude }
      : undefined;

  // 부르미가 배달 취소를 확정하면 백엔드에 취소를 요청하고 홈으로 돌아간다(드리미에게는 SSE로 통지됨).
  const confirmCancel = async () => {
    if (canceling || !detailReady) return;
    setCanceling(true);
    setCancelError(null);
    try {
      await api.cancelByBoormi(orderId);
      setConfirmOpen(false);
      navigate(ROUTES.home, { replace: true });
    } catch (e) {
      // 취소를 누르는 사이 SSE를 놓쳐 이미 종료된 건이면, 인라인 메시지 대신 종료 상태 전용 흐름으로 보낸다.
      if (isApiError(e) && e.code === "DELIVERY_013") {
        // 이미 배달 완료 → 리뷰(드리미 평가) 페이지로.
        setConfirmOpen(false);
        navigate(ROUTES.deliveryComplete, { replace: true });
        return;
      }
      if (isApiError(e) && e.code === "DELIVERY_012") {
        // 이미 취소됨 → 취소 안내 차단 모달(나가기 시 홈).
        setConfirmOpen(false);
        blockDeliveryDetail({
          title: "이미 취소된 배달이에요",
          message: "취소된 배달은 더 이상 추적할 수 없어요.",
        });
        return;
      }
      setCancelError(
        isApiError(e)
          ? e.message
          : "취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar title="실시간 배송" onBack={() => navigate(-1)} actions={[]} />

      {toast && (
        <div className="pt-2">
          <Toast
            icon="bell"
            title={toast.title}
            description={toast.description}
          />
        </div>
      )}

      {detailReady && !connected && !toast && (
        <p className="pt-2 text-2xs text-muted">실시간 연결 중…</p>
      )}

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          {view.title}
        </h1>

        <MapCard
          height={340}
          overlay={
            <div className="flex items-center gap-6 rounded-pill bg-navy-900 px-6 py-2.5 text-white">
              <div className="flex flex-col items-center">
                <span className="text-2xs opacity-70">배송 완료 예상</span>
                <span className="text-md font-bold">
                  {arrivalTime ?? "계산 중…"}
                </span>
              </div>
            </div>
          }
        >
          <DeliveryRouteMap
            pickup={pickup}
            dropoff={dropoff}
            driver={driver}
            route={routePath}
            height={340}
          />
        </MapCard>

        <Card className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-pill bg-teal-50 text-teal-700">
            <Icon name="pin" size={18} />
          </span>
          <div className="flex flex-col">
            <span className="text-2xs text-muted">실시간 상태</span>
            <span className="text-md font-bold text-navy-900">
              {view.title}
            </span>
          </div>
        </Card>
      </main>

      <footer className="flex flex-col items-center gap-2 pt-4">
        <Button block variant="outline">
          연락하기
        </Button>
        {detailReady && !view.terminal && (
          <button
            type="button"
            onClick={() => {
              setCancelError(null);
              setConfirmOpen(true);
            }}
            className="text-2xs text-muted"
          >
            배달 취소하기
          </button>
        )}
      </footer>

      <Modal
        open={confirmOpen}
        label="배달 취소 확인"
        onClose={canceling ? undefined : () => setConfirmOpen(false)}
      >
        <Card className="flex flex-col gap-4 text-center">
          <div className="flex flex-col gap-1">
            <h2 className="text-md font-bold text-navy-900">
              정말로 취소하시겠습니까?
            </h2>
            <p className="text-2xs text-muted">
              취소하면 배달이 종료되고 드리미에게 알림이 전송돼요.
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

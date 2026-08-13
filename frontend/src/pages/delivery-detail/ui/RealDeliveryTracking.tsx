import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import {
  ArrivalBadge,
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
  ContactSheet,
  useSse,
  useSseReconnectSync,
  formatArrivalTime,
  formatLastSeen,
  type SseHandlers,
} from "@/shared/lib";
import { ROUTES } from "@/shared/config/routes";
import { api, isApiError, DeliveryStatusResponseDtoStatus } from "@/shared/api";
import { useToastStore } from "@/shared/store/toastStore";
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

/** "마지막 확인 N분 전" 라벨을 다시 계산하는 주기. 분 단위 표시라 초 단위로 돌릴 필요가 없다. */
const LAST_SEEN_LABEL_REFRESH_MS = 15000;

/**
 * `delivery_dreami_offline` payload. SSE 전용이라 orval이 생성하지 않아 직접 선언한다
 * (matchingStore의 OfferPopupPayload와 같은 관례).
 */
interface DreamiOfflinePayload {
  orderId: string;
  secondsSinceLastLocation: number;
}

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
  const backOrHome = useBackOrHome();
  const navigate = useNavigate();
  const showToast = useToastStore((state) => state.show);
  const clearToasts = useToastStore((state) => state.clear);

  // URL이 알려준 상태 > 마지막으로 관측한 상태 > 픽업중. 홈 카드로 재진입하면 URL에
  // 상태가 없어 픽업중으로 되돌아가므로 스냅샷으로 복원한다.
  const [status, setStatus] = useState<DeliveryStatusResponseDtoStatus>(
    initialStatus ??
      recallDeliveryStage(orderId) ??
      DeliveryStatusResponseDtoStatus.PICKUP_NORMAL,
  );

  // location이 바뀌면 RealDeliveryTracking을 다시 렌더링
  const [location, setLocation] = useState<DeliveryLocationDto | null>(null);

  // 드리미 위치를 마지막으로 확인한 로컬 시각. null이면 정상(배너 없음).
  // 서버가 준 '경과 초'를 내 시계로 역산해 담으므로, 서버-클라이언트 시계 오차가 라벨에 새지 않는다.
  const [lastSeenAt, setLastSeenAt] = useState<number | null>(null);
  // "마지막 확인 N분 전" 라벨을 계산할 기준 시각. lastSeenAt과 함께 잡고, 배너가 뜬 동안 주기적으로 갱신한다.
  const [labelNow, setLabelNow] = useState(() => Date.now());

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
      // 드리미 연결 상태는 이벤트 공백으로 추론하지 않고 서버 스냅샷으로 잡는다.
      // 화면 재진입 시 즉시 정확하고, 끊긴 사이 놓친 오프라인 통보도 이 경로로 복구된다.
      const now = Date.now();
      setLastSeenAt(
        loadedDetail.dreamiOffline
          ? now - (loadedDetail.secondsSinceLastLocation ?? 0) * 1000
          : null,
      );
      setLabelNow(now);
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

  // 부르미 취소(확인 모달) 상태.
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [contactOpen, setContactOpen] = useState(false);

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
    clearToasts();
    applyStatus(nextStatus);
    setConfirmOpen(false);
    blockDeliveryDetail({
      title: notice?.title ?? "배달이 종료됐어요",
      message:
        message ?? notice?.message ?? "종료된 배달은 더 이상 추적할 수 없어요.",
    });
  };

  // 서버가 알려준 '마지막 위치 수신 이후 경과 초'를 내 시계 기준점으로 역산해 저장한다.
  // 라벨 기준 시각(labelNow)도 같이 잡아 배너가 뜨는 순간의 문구가 정확하게 나오도록 한다.
  const markDreamiOffline = (secondsSinceLastLocation: number) => {
    const now = Date.now();
    setLastSeenAt(now - secondsSinceLastLocation * 1000);
    setLabelNow(now);
  };

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
      // 위치가 다시 흐르면 곧 드리미가 살아난 것이다 → 별도 복구 이벤트 없이 여기서 배너를 내린다.
      setLastSeenAt(null);
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
      showToast({
        icon: "bell",
        title: "드리미가 픽업을 완료했어요",
        description: dto.message ?? "지금부터 배송을 시작해요.",
        dedupeKey: `delivery-delivering:${orderId}`,
      });
    },
    // "delivery_completed" 이라는 단어는 백엔드에서 결정한 이름임!!
    delivery_completed: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      const completedStatus =
        dto.status ?? DeliveryStatusResponseDtoStatus.DELIVERED;
      clearToasts();
      applyStatus(completedStatus);
      navigate(`${ROUTES.deliveryComplete}?orderId=${orderId}`, {
        replace: true,
      });
    },
    delivery_cancelled: (data) => {
      const dto = forThisOrder(data);
      if (!dto) return;
      showCancellationModal(
        dto.status ?? DeliveryStatusResponseDtoStatus.PICKUP_CANCELLED_BY_ADMIN,
        dto.message,
      );
    },
    // 서버가 판정한 '드리미 위치 무소식'. payload가 DeliveryStatusResponseDto가 아니라
    // DreamiOfflinePayload라서 forThisOrder를 쓰지 않고 직접 필터한다.
    delivery_dreami_offline: (data) => {
      const dto = data as DreamiOfflinePayload;
      if (dto?.orderId !== orderId) return;
      markDreamiOffline(dto.secondsSinceLastLocation ?? 0);
    },
  };

  const { status: sseStatus } = useSse(handlers, {
    enabled: detailReady,
  });

  // SSE가 끊긴 사이 놓친 위치·상태 전이(배송중/취소/완료)를 재연결 시 스냅샷 재조회로 복구한다.
  useSseReconnectSync(sseStatus, refreshDeliveryDetail, {
    enabled: detailReady,
  });

  // 배너가 떠 있는 동안만 "마지막 확인 N분 전" 라벨의 기준 시각을 갱신한다(정상일 때는 타이머를 걸지 않는다).
  // 기준 시각의 최초값은 markDreamiOffline이 lastSeenAt과 함께 잡아 주므로 여기서 따로 세팅하지 않는다.
  useEffect(() => {
    if (lastSeenAt === null) return;
    const id = window.setInterval(
      () => setLabelNow(Date.now()),
      LAST_SEEN_LABEL_REFRESH_MS,
    );
    return () => window.clearInterval(id);
  }, [lastSeenAt]);

  // 내 SSE가 끊긴 동안에는 위치가 안 오는 게 당연하므로 드리미 배너를 신뢰할 수 없다 → 숨긴다.
  // 이게 "내 인터넷이 끊긴 걸 드리미가 끊긴 것으로 오해"하는 상황을 막는 핵심이다.
  // 최초 마운트의 `connecting`은 정상 절차라 배너를 띄우지 않고(매 진입마다 깜빡이므로),
  // 영구 종료(`closed`)는 전역 SseStatusBanner 모달이 안내하므로 여기서는 드리미 배너만 억제한다.
  const isMyConnectionDown =
    sseStatus === "reconnecting" || sseStatus === "closed";

  // 연결·위치 상태 안내는 일회성 토스트가 아니므로 화면 안에 유지한다.
  const topNotice =
    sseStatus === "reconnecting"
      ? {
          icon: "transfer" as const,
          title: "실시간 연결이 불안정해요",
          description: "다시 연결 중…",
        }
      : !isMyConnectionDown && lastSeenAt !== null
        ? {
            icon: "pin" as const,
            title: "드리미 위치를 받지 못하고 있어요",
            description: `마지막 확인 ${formatLastSeen(lastSeenAt, labelNow)}`,
          }
        : null;

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
        navigate(`${ROUTES.deliveryComplete}?orderId=${orderId}`, {
          replace: true,
        });
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
      <TopBar title="실시간 배송" onBack={backOrHome} actions={[]} />

      {topNotice && (
        <div className="pt-2">
          <Toast
            icon={topNotice.icon}
            title={topNotice.title}
            description={topNotice.description}
          />
        </div>
      )}

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          {view.title}
        </h1>

        <MapCard
          height={340}
          overlay={<ArrivalBadge arrivalTime={arrivalTime} />}
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
        <Button block variant="outline" onClick={() => setContactOpen(true)}>
          연락
        </Button>
        {detailReady && isPickup && (
          <button
            type="button"
            onClick={() => {
              setCancelError(null);
              setConfirmOpen(true);
            }}
            className="text-2xs text-muted"
          >
            배달 취소하기 (픽업 전에만 가능)
          </button>
        )}
      </footer>

      <ContactSheet
        open={contactOpen}
        orderId={orderId}
        onClose={() => setContactOpen(false)}
      />

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

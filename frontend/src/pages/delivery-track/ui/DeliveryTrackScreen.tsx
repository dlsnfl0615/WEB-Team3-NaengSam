import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import {
  ArrivalBadge,
  Button,
  BlockingLoadErrorModal,
  Card,
  Icon,
  MapCard,
  Modal,
  PhotoLightboxModal,
  ScreenShell,
  DeliveryRouteMap,
  Toast,
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
  ContactSheet,
  useSse,
  useSseReconnectSync,
  useDreamiLocationBroadcast,
  useLeaveGuard,
  formatArrivalTime,
  ETA_UNAVAILABLE_TITLE,
  type EtaUnavailablePayload,
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

/**
 * 실시간 배송 추적 화면(Figma node 191:972, 191:989).
 * 지도 풀블리드 + 지도 위 뒤로가기. 활성 배달의 픽업중 → 배송중 → 완료를 전역 스토어로 전환합니다(URL 미노출).
 *
 * 실 백엔드 모드: `?orderId=` 가 있으면 /delivery-test 에서 실제 배달을 들고 넘어온 것으로 보고,
 * 픽업 완료를 mock 이 아니라 사진 인증 화면(/delivery-proof)으로 넘겨 실제 pickup-finish 로 처리한다.
 * `?status=DELIVERING` 이면 pickup-finish 후 배송중으로 돌아온 상태다. orderId 가 없으면 기존 mock 흐름.
 */
export function DeliveryTrackScreen() {
  const backOrHome = useBackOrHome();
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
  // 서버가 배송완료예상시간을 계산하지 못했다는 통보. 담겨 있으면 지도 위 예상 시각 배지를 지운다
  // ("계산 중…"으로 두면 아직 GPS를 못 받은 상태와 구분되지 않는다).
  const [etaUnavailable, setEtaUnavailable] =
    useState<EtaUnavailablePayload | null>(null);
  // 배지를 지운 이유를 알리는 토스트. 배지와 달리 잠깐만 띄운다 — 지도 위 뒤로가기를 계속 가리면 안 되고,
  // 실패가 이어지는 동안 서버 재시도 쿨다운(30초)마다 같은 이벤트가 오므로 첫 회에만 세운다(etaNoticeShown).
  const [etaNotice, setEtaNotice] = useState<EtaUnavailablePayload | null>(null);
  // 되돌릴 필요는 없다 — 계산이 한 번 성공하면 서버는 이 배달에 대해 다시 계산하지 않으므로 재실패가 없다.
  const etaNoticeShown = useRef(false);
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
  const { error: locationError, position } = useDreamiLocationBroadcast(
    orderId,
    {
      enabled: isRealMode && detailReady,
      includeRoute,
      onResult: handleLocationResult,
    },
  );
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

  // 요청 사항 모달 · 물품 사진 라이트박스 상태
  const [requestNoteOpen, setRequestNoteOpen] = useState(false);
  const [itemPhotoOpen, setItemPhotoOpen] = useState(false);

  // 픽업 취소 확인 모달 상태
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [contactOpen, setContactOpen] = useState(false);

  // 이탈 경고 모달 상태.
  // 이 화면을 벗어나면 useDreamiLocationBroadcast의 cleanup이 GPS 전송을 끊어 배달 추적이 멈춘다.
  // 그래서 실제 배달을 들고 있을 때(mock 흐름·조회 실패·취소된 배달 제외)만 붙잡는다.
  const [leaveConfirmOpen, setLeaveConfirmOpen] = useState(false);
  const leaveGuarded = isRealMode && detailReady && !blockingModal.open;
  useLeaveGuard(leaveGuarded, () => setLeaveConfirmOpen(true));

  // 화면 안 뒤로가기: 가드가 켜져 있으면 확인 모달을 거친다.
  const onBack = () => {
    if (leaveGuarded) {
      setLeaveConfirmOpen(true);
      return;
    }
    backOrHome();
  };

  // 부르미가 보낸 핑. 앱이 닫혀 있으면 웹푸시로 닿지만, 이 화면을 보고 있는 동안에는 토스트로 알린다.
  const [pinged, setPinged] = useState(false);
  useEffect(() => {
    if (!pinged) return;
    const timer = setTimeout(() => setPinged(false), 6000);
    return () => clearTimeout(timer);
  }, [pinged]);

  // 계산 실패 안내도 핑과 같은 방식으로 잠깐만 띄운다(배지가 사라진 상태 자체가 지속 신호 역할을 한다).
  useEffect(() => {
    if (!etaNotice) return;
    const timer = setTimeout(() => setEtaNotice(null), 6000);
    return () => clearTimeout(timer);
  }, [etaNotice]);

  const sseHandlers: SseHandlers = {
    // "delivery_ping"은 백엔드에서 결정한 이름(DeliveryEventType.DELIVERY_PING)
    delivery_ping: (data) => {
      const dto = data as DeliveryStatusResponseDto;
      if (dto?.orderId !== orderId) return;
      setPinged(true);
    },
    // 서버가 '드리미→픽업지' 경로 계산에 실패했다(내가 픽업지에서 너무 멀거나 카카오 응답 지연 등).
    delivery_eta_unavailable: (data) => {
      const dto = data as EtaUnavailablePayload;
      if (dto?.orderId !== orderId) return;
      setEtaUnavailable(dto);
      if (etaNoticeShown.current) return; // 재시도 쿨다운마다 다시 오므로 토스트는 첫 회만
      etaNoticeShown.current = true;
      setEtaNotice(dto);
    },
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
  // 서버는 위치 전송마다 계산을 다시 시도한다. 픽업지에 가까워지거나 카카오가 회복돼 예상 시각이
  // 채워지면 지난 실패는 무시하고 배지를 되살린다.
  const etaFailure = arrivalTime ? null : etaUnavailable;

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
      // 드리미가 전달 완료 → 부르미를 평가하는 리뷰 화면으로.
      navigate(`${ROUTES.deliveryComplete}?reviewee=boormi`, { replace: true });
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
      // 취소를 누르는 사이 SSE를 놓쳐 이미 종료된 건이면, 인라인 메시지 대신 종료 상태 전용 흐름으로 보낸다.
      if (isApiError(e) && e.code === "DELIVERY_013") {
        // 이미 배달 완료 → 리뷰(드리미가 부르미를 평가) 페이지로.
        setConfirmOpen(false);
        navigate(
          `${ROUTES.deliveryComplete}?reviewee=boormi${orderId ? `&orderId=${orderId}` : ""}`,
          { replace: true },
        );
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
          : "픽업 취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      {locationError && (
        <div className="ds-toast-down fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast
            icon="pin"
            title="GPS를 허용해주세요."
            description="배달을 계속하려면 기기의 위치 권한을 켜 주세요."
          />
        </div>
      )}

      {/* 배송 예상 시각 배지를 지운 이유. GPS 경고가 떠 있으면 그게 더 급한 원인이므로 양보한다. */}
      {etaNotice && !locationError && (
        <div className="ds-toast-down fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast
            icon="pin"
            title={ETA_UNAVAILABLE_TITLE}
            description={etaNotice.message}
          />
        </div>
      )}

      {/* GPS 경고·계산 실패 안내가 떠 있을 땐 그쪽이 더 급하므로 핑 토스트는 양보한다(같은 자리에 겹쳐 뜨지 않게). */}
      {pinged && !locationError && !etaNotice && (
        <div className="ds-toast-down fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast
            icon="bell"
            title="부르미가 핑을 보냈어요."
            description="배달 상황이 궁금한가 봐요. 연락해 주세요."
          />
        </div>
      )}

      {/* 풀블리드 지도 + 지도 위 뒤로가기 */}
      <div className="relative -mx-4 -mt-6">
        <MapCard
          flat
          height={440}
          // 계산에 실패했으면 배지를 통째로 지운다. 실패 이유는 알약에 넣기엔 문구가 길어 토스트로 안내한다.
          overlay={
            etaFailure ? undefined : <ArrivalBadge arrivalTime={arrivalTime} />
          }
        >
          <DeliveryRouteMap
            flat
            pickup={pickup}
            dropoff={dropoff}
            driver={position ?? undefined}
            driverPinImage={
              isPickup
                ? "/running-dreami-nopickup-1.png"
                : "/running-dreami-pickup-1.png"
            }
            driverRunningPinImage={
              isPickup
                ? "/running-dreami-nopickup-2.png"
                : "/running-dreami-pickup-2.png"
            }
            route={route}
            height={440}
          />
        </MapCard>
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로가기"
          className="absolute left-4 top-5 text-navy-900"
        >
          <Icon name="back" size={20} />
        </button>
      </div>

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center justify-between gap-2">
          <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
            {title}
          </h1>
          <div className="flex shrink-0 gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => setRequestNoteOpen(true)}
            >
              요청사항
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setItemPhotoOpen(true)}
            >
              물품사진
            </Button>
          </div>
        </div>

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
          {/* mock 흐름(orderId 없음)에서는 조회할 배달이 없어 비활성. */}
          {/* shrink-0: 옆의 액션 버튼(w-full)에 밀려 폭이 눌리면 라벨이 두 줄로 접힌다. */}
          <Button
            variant="outline"
            className="shrink-0"
            disabled={!orderId}
            onClick={() => setContactOpen(true)}
          >
            연락
          </Button>
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

      <ContactSheet
        open={contactOpen}
        orderId={orderId}
        onClose={() => setContactOpen(false)}
      />

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

      <Modal
        open={leaveConfirmOpen}
        label="배송 화면 나가기 확인"
        onClose={() => setLeaveConfirmOpen(false)}
      >
        <Card className="flex flex-col gap-4 text-center">
          <div className="flex flex-col gap-1">
            <h2 className="text-md font-bold text-navy-900">
              배송 화면을 나갈까요?
            </h2>
            <p className="text-2xs text-muted">
              배송 중에 페이지를 벗어나면 위치 전송이 멈춰 배달이 정상적으로
              진행되지 않을 수 있습니다.
            </p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              block
              onClick={() => setLeaveConfirmOpen(false)}
            >
              계속 배송하기
            </Button>
            {/* 가드가 히스토리에 sentinel을 하나 끼워 둬서 뒤로가기는 이 화면으로 되돌아온다.
                취소·차단 모달과 마찬가지로 홈으로 보낸다. */}
            <Button
              block
              onClick={() => {
                setLeaveConfirmOpen(false);
                navigate(ROUTES.home, { replace: true });
              }}
            >
              나가기
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

      <Modal
        open={requestNoteOpen}
        label="요청 사항"
        onClose={() => setRequestNoteOpen(false)}
      >
        <Card className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-md font-bold text-navy-900">요청 사항</h2>
            <button
              type="button"
              aria-label="닫기"
              onClick={() => setRequestNoteOpen(false)}
              className="text-muted"
            >
              <Icon name="close" size={18} />
            </button>
          </div>
          <p className="text-sm text-navy-900">
            {detail?.deliveryRequest || "요청 사항이 없어요."}
          </p>
        </Card>
      </Modal>

      <PhotoLightboxModal
        open={itemPhotoOpen}
        label="물품 사진"
        photoUrl={detail?.itemPhotoUrl}
        emptyMessage="등록된 물품 사진이 없어요."
        onClose={() => setItemPhotoOpen(false)}
      />
    </ScreenShell>
  );
}

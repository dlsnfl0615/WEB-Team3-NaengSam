import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Icon, MapCard, Modal, ScreenShell, Toast } from "@/shared/ui";
import { api, isApiError } from "@/shared/api";
import type { DeliveryStatusResponseDto } from "@/shared/api";
import { useSse, type SseHandlers } from "@/shared/lib";
import { ROUTES } from "@/shared/config/routes";
import {
  useActiveDelivery,
  useDeliveryStore,
} from "@/shared/store/deliveryStore";
import { TRACK_STAGES, type TrackStage } from "./statuses";
import { TrackOverlay } from "./TrackOverlay";

/** 상대편(부르미/관리자) 취소 알림을 보여준 뒤 홈으로 나가기까지의 대기 시간. */
const CANCEL_NAV_DELAY_MS = 1800;

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
  const isRealMode = Boolean(orderId);
  const active = useActiveDelivery();
  const advance = useDeliveryStore((s) => s.advance);
  const complete = useDeliveryStore((s) => s.complete);
  const cancel = useDeliveryStore((s) => s.cancel);

  // 픽업 취소 확인 모달 상태
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // 상대편(부르미/관리자)이 취소하면 SSE로 통지받아 알림을 띄우고 홈으로 나간다.
  const [sseToast, setSseToast] = useState<{ title: string; description?: string } | null>(
    null,
  );
  const navTimer = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (navTimer.current !== null) clearTimeout(navTimer.current);
    },
    [],
  );

  const sseHandlers: SseHandlers = {
    delivery_cancelled: (data) => {
      const dto = data as DeliveryStatusResponseDto;
      if (dto?.orderId !== orderId) return;
      setSseToast({
        title: "배달이 취소됐어요",
        description: dto.message ?? "상대방이 배달을 취소했어요.",
      });
      if (navTimer.current === null) {
        navTimer.current = window.setTimeout(
          () => navigate(ROUTES.home, { replace: true }),
          CANCEL_NAV_DELAY_MS,
        );
      }
    },
  };

  // 실 모드에서만 드리미 세션으로 SSE를 구독한다(mock 모드는 구독하지 않음).
  useSse(sseHandlers, { enabled: isRealMode });

  // 드리미 화면은 픽업중/배송중만 다룬다(그 외 상태는 배송중으로 취급).
  // 실 모드는 status 파라미터로, mock 모드는 활성 배달 상태로 단계를 결정한다.
  const stage: TrackStage = isRealMode
    ? params.get("status") === "DELIVERING"
      ? "배송중"
      : "픽업중"
    : active?.status === "배송중"
      ? "배송중"
      : "픽업중";
  const isPickup = stage === "픽업중";
  const { title, action, cancelable } = TRACK_STAGES[stage];

  const destination = active?.dropoff ?? "A동 102호";
  const eta = active?.eta ?? "3분";
  const distance = active?.distance ?? "450m";

  const onAction = async () => {
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
    setCancelError(null);
    setConfirmOpen(true);
  };

  // 모달에서 "취소하기" 확정 시 실제로 취소를 진행한다.
  // 실 모드는 백엔드에 드리미 픽업 취소를 요청하고, mock 모드는 기존 스토어 흐름을 탄다.
  const confirmCancel = async () => {
    if (canceling) return;
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
        isApiError(e) ? e.message : "픽업 취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      {/* 상대편 취소 SSE 알림(화면 위에 떠서 표시) */}
      {sseToast && (
        <div className="fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast
            icon="bell"
            title={sseToast.title}
            description={sseToast.description}
          />
        </div>
      )}

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
    </ScreenShell>
  );
}

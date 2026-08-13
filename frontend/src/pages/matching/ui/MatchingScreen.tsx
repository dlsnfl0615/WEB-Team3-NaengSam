import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  BlockingLoadErrorModal,
  Button,
  Card,
  MapCard,
  Modal,
  NearbyCallsMap,
  ScreenShell,
  TopBar,
  type NearbyCall,
} from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config/routes";
import { PushPrompt } from "@/shared/lib/push/PushPrompt";
import { useRole } from "@/shared/lib/role/useRole";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import { useToastStore } from "@/shared/store/toastStore";

/** 화면에 머무는 동안 주변 콜을 다시 조회하는 주기(ms). */
const NEARBY_POLL_MS = 5000;

const ITEM_CD_LABELS: Record<string, string> = {
  DOCUMENT: "서류",
  SAMPLE: "샘플",
  PACKAGE: "택배",
  ETC: "기타",
};

function itemCdLabel(itemCd?: string): string | null {
  return itemCd ? ITEM_CD_LABELS[itemCd] ?? null : null;
}

// 재매칭 대기 등으로 인해 짧은 순간 IN_PROGRESS/CANCELLED 등이 함께 보일 수 있어 전체 값에 라벨을 준비한다.
const ORDER_CD_LABELS: Record<string, string> = {
  MATCHING: "매칭 중",
  PENDING_BOORMI_CONFIRMATION: "다른 드리미가 수락, 부르미 확인 중",
  IN_PROGRESS: "이미 배달 시작됨",
  COMPLETED: "배달 완료됨",
  CANCELLED: "취소됨",
  CLAIM_REVIEW: "클레임 심사 중",
};

function orderCdLabel(orderCd?: string): string | null {
  return orderCd ? ORDER_CD_LABELS[orderCd] ?? null : null;
}

function fullAddress(line1?: string, line2?: string): string | null {
  if (!line1) return null;
  return line2 ? `${line1} ${line2}` : line1;
}

/**
 * 매칭(찾는 중) 화면(Figma node 191:763).
 * 드리미는 실제 지도 위에서 주변 콜 핀을 보고, 부르미는 대기 상태를 보여준다.
 * 오퍼/콜 팝업은 전역 `MatchingPopup`이 담당하므로 다른 화면으로 이동해도 이어서 뜬다.
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { role } = useRole();
  const loadNearbyCalls = useMatchingStore((s) => s.loadNearbyCalls);
  const goOnline = useMatchingStore((s) => s.goOnline);
  const goOffline = useMatchingStore((s) => s.goOffline);
  const myLocation = useMatchingStore((s) => s.myLocation);
  const nearbyCalls = useMatchingStore((s) => s.nearbyCalls);
  const nearbyCallsError = useMatchingStore((s) => s.nearbyCallsError);
  const online = useMatchingStore((s) => s.online);
  const message = useMatchingStore((s) => s.message);
  const showToast = useToastStore((state) => state.show);
  const dismissToast = useToastStore((state) => state.dismiss);

  const isDriver = role === "드리미";
  const counterpart = isDriver ? "부르미" : "드리미";

  // 부르미가 홈 카드로 들어오면 어떤 부름인지 알 수 있어 취소를 제공한다.
  const orderId = params.get("orderId");
  const cancelable = !isDriver && Boolean(orderId);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const [ending, setEnding] = useState(false);
  const [locationBlocked, setLocationBlocked] = useState(false);
  const callToastId = useRef<string | null>(null);

  // 부르미는 콜 등록을 마치고 이 화면에 들어오므로 진입 자체가 "찾으면 알려줘"라는 의도다.
  // 드리미는 시작하기를 누른 뒤에야 켜진다(handleStart).
  const [pushPromptOpen, setPushPromptOpen] = useState(cancelable);

  // 주변 콜 상세는 이 화면에 결합된 안내라 화면을 나가면 제거한다.
  useEffect(
    () => () => {
      if (callToastId.current) dismissToast(callToastId.current);
    },
    [dismissToast],
  );

  // 온라인 여부는 서버가 진실 소스다. 스토어는 메모리에만 있어 새로고침·새 탭이면 false로 시작하는데
  // 서버 등록은 살아 있을 수 있다. 화면에 들어온 시점에 한 번 맞춰 버튼이 실제와 어긋나지 않게 한다.
  useEffect(() => {
    void useMatchingStore.getState().syncCurrentMatching();
  }, []);

  // 드리미: 화면에 머무는 동안 주변 콜을 계속 갱신한다(시작하기 여부와 무관).
  // 오퍼 팝업 자체는 전역 `MatchingPopup`이 받으므로, 여기서는 지도용 목록만 폴링한다.
  useEffect(() => {
    if (!isDriver || locationBlocked) return;
    let active = true;
    const loadCalls = async () => {
      const result = await loadNearbyCalls();
      if (active && result === "location-unavailable") {
        setLocationBlocked(true);
      }
    };

    void loadCalls();
    const timer = window.setInterval(() => {
      void loadCalls();
    }, NEARBY_POLL_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [isDriver, loadNearbyCalls, locationBlocked]);

  const callsForMap: NearbyCall[] = nearbyCalls.flatMap((call) => {
    if (
      !call.orderId ||
      call.location?.latitude == null ||
      call.location?.longitude == null
    ) {
      return [];
    }
    return [
      {
        id: call.orderId,
        location: {
          latitude: call.location.latitude,
          longitude: call.location.longitude,
        },
        itemName: call.itemName,
        itemCd: call.itemCd,
        orderCd: call.orderCd,
        expectedRevenue: call.expectedRevenue,
        expectedEtaMinutes: call.expectedEtaMinutes,
        pickupEtaMinutes: call.pickupEtaMinutes,
        distanceMeters: call.distanceMeters,
        originAddressLine1: call.originAddressLine1,
        originAddressLine2: call.originAddressLine2,
        destinationAddressLine1: call.destinationAddressLine1,
        destinationAddressLine2: call.destinationAddressLine2,
      },
    ];
  });

  const handleCallClick = (call: NearbyCall) => {
    const origin = fullAddress(call.originAddressLine1, call.originAddressLine2);
    const destination = fullAddress(
      call.destinationAddressLine1,
      call.destinationAddressLine2,
    );
    const itemLabel = itemCdLabel(call.itemCd);
    const statusLabel = orderCdLabel(call.orderCd);

    const rows = [
      statusLabel ? `상태: ${statusLabel}` : null,
      origin ? `출발지: ${origin}` : null,
      destination ? `도착지: ${destination}` : null,
      call.expectedEtaMinutes != null
        ? `배달 예상: ${call.expectedEtaMinutes}분${
            call.expectedRevenue != null
              ? ` · 예상수익 ₩${call.expectedRevenue.toLocaleString()}`
              : ""
          }`
        : null,
      call.pickupEtaMinutes != null || call.distanceMeters != null
        ? `픽업까지: ${
            call.pickupEtaMinutes != null ? `약 ${call.pickupEtaMinutes}분` : ""
          }${
            call.distanceMeters != null
              ? ` (${Math.round(call.distanceMeters)}m)`
              : ""
          }`
        : null,
    ].filter((v): v is string => v !== null);

    callToastId.current = showToast({
      icon: "bell",
      title: itemLabel ? `${call.itemName ?? "콜 정보"} (${itemLabel})` : call.itemName ?? "콜 정보",
      description: (
        <div className="flex flex-col gap-0.5">
          {rows.map((row) => (
            <p key={row}>{row}</p>
          ))}
        </div>
      ),
      persistent: true,
      dedupeKey: "nearby-call-details",
    });
  };

  const handleStart = async () => {
    setStarting(true);
    await goOnline();
    // 온라인 전환에 성공한 직후에만 알림을 묻는다. 사용자가 방금 "시작하기"를 눌렀으므로
    // 의도가 문자 그대로 "콜을 알려줘"인 순간이다. 콜드 로드에서 묻지 않는 이유는
    // 되돌릴 수 없는 거절을 수집하게 되기 때문이다.
    if (useMatchingStore.getState().online) setPushPromptOpen(true);
    setStarting(false);
  };

  const handleEnd = async () => {
    setEnding(true);
    useMatchingStore.setState({ message: null });
    await goOffline();
    if (!useMatchingStore.getState().message) {
      showToast({
        icon: "bell",
        title: "오프라인으로 전환됐어요",
        description: "드리미 활동이 종료됐어요.",
        dedupeKey: "dreami-offline",
      });
    }
    setEnding(false);
  };

  // 모달에서 확정하면 매칭 큐에서 부름을 회수하고 홈으로 돌아간다.
  const confirmCancel = async () => {
    if (!orderId || canceling) return;
    setCanceling(true);
    setCancelError(null);
    try {
      await useBoormiOrderStore.getState().cancelOrder(orderId);
      setConfirmOpen(false);
      navigate(ROUTES.home, { replace: true });
    } catch (e) {
      setCancelError(
        isApiError(e)
          ? e.message
          : "부름 취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar
        title={`${counterpart}를 찾는 중`}
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <PushPrompt
          active={pushPromptOpen}
          description={
            isDriver
              ? "화면을 닫아도 놓친 콜을 알려드릴까요?"
              : "드리미를 찾으면 바로 알려드릴까요?"
          }
        />

        {isDriver ? (
          <NearbyCallsMap
            center={myLocation}
            calls={callsForMap}
            onCallClick={handleCallClick}
            fallbackMessage={nearbyCallsError}
            height={280}
          />
        ) : (
          <MapCard height={280} />
        )}

        {isDriver && !online ? (
          <Card className="flex flex-col gap-2">
            <p className="text-base font-bold text-navy-900">
              {message ? "콜을 받을 수 없는 상태예요" : "아직 시작하지 않았어요"}
            </p>
            <p className="text-2xs text-muted">
              {message ?? "하단의 시작하기를 눌러 콜을 받아보세요."}
            </p>
          </Card>
        ) : (
          <Card className="flex flex-col gap-1">
            <p className="flex items-center gap-2 text-base font-bold text-navy-900">
              <span className="size-2 rounded-pill bg-teal-500" />
              {isDriver
                ? `근방 3km 내 부름 ${nearbyCalls.length}건 대기중`
                : `${counterpart}를 찾고 있어요`}
            </p>
            <p className="text-2xs text-muted">
              요청을 보낸 {counterpart}의 수락을 기다리고 있어요...
            </p>
          </Card>
        )}
      </main>

      {cancelable && (
        <footer className="pt-4">
          <Button
            block
            variant="outline"
            onClick={() => {
              setCancelError(null);
              setConfirmOpen(true);
            }}
          >
            부름 취소하기
          </Button>
        </footer>
      )}

      {isDriver && (
        <footer className="flex gap-2 pt-4">
          <Button variant="outline" block disabled={!online || ending} onClick={handleEnd}>
            {ending ? "종료 중…" : "종료"}
          </Button>
          <Button variant="primary" block disabled={online || starting} onClick={handleStart}>
            {starting ? "확인 중…" : "시작하기"}
          </Button>
        </footer>
      )}

      <Modal
        open={confirmOpen}
        label="부름 취소 확인"
        onClose={canceling ? undefined : () => setConfirmOpen(false)}
      >
        <Card className="flex flex-col gap-4 text-center">
          <div className="flex flex-col gap-1">
            <h2 className="text-md font-bold text-navy-900">
              정말로 취소하시겠습니까?
            </h2>
            <p className="text-2xs text-muted">
              취소하면 매칭이 중단되고 등록한 부름이 사라져요.
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
        open={locationBlocked}
        title="위치 권한이 필요해요"
        message={nearbyCallsError ?? "현재 위치를 사용할 수 없어요."}
        guidance="드리미 매칭에는 GPS 위치 권한이 필요해요. 홈에서 권한을 허용한 뒤 다시 시작해 주세요."
        retrying={false}
        canRetry={false}
        onRetry={() => undefined}
        onExit={() => navigate(ROUTES.home, { replace: true })}
      />
    </ScreenShell>
  );
}

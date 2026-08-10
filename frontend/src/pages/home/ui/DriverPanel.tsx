import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  DeliveryCard,
  LocationBar,
  SectionHeader,
  StatCard,
  Toast,
} from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { api, isApiError } from "@/shared/api";
import { useCurrentAddress } from "@/shared/lib";
import { useMatchingStore } from "@/shared/store/matchingStore";
import {
  ORDER_PROGRESS,
  toBoormiOrder,
  type BoormiOrder,
} from "@/shared/store/boormiOrderAdapter";

const TRANSIENT_TOAST_MS = 4000;

/** 홈 화면의 드리미(배송인) 본문. 현재 수행 중인 배달을 실제 API로 조회한다. */
export function DriverPanel() {
  const navigate = useNavigate();
  const { address: currentAddress, error: currentAddressError } =
    useCurrentAddress();
  const [current, setCurrent] = useState<BoormiOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const goOffline = useMatchingStore((s) => s.goOffline);
  const matchingMessage = useMatchingStore((s) => s.message);
  const [endingSession, setEndingSession] = useState(false);
  const [toast, setToast] = useState<{ title: string; description?: string } | null>(
    null,
  );
  const toastTimer = useRef<number | null>(null);

  const onEndSession = async () => {
    setEndingSession(true);
    useMatchingStore.setState({ message: null });
    await goOffline();
    // 실패 시엔 matchingMessage가 채워지므로, 비어있을 때만(=성공) 토스트를 띄운다.
    if (!useMatchingStore.getState().message) {
      setToast({ title: "오프라인으로 전환됐어요", description: "드리미 활동이 종료됐어요." });
    }
    setEndingSession(false);
  };

  // 토스트 자동 소멸.
  useEffect(() => {
    if (!toast) return;
    toastTimer.current = window.setTimeout(() => setToast(null), TRANSIENT_TOAST_MS);
    return () => {
      if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    };
  }, [toast]);

  useEffect(() => {
    let alive = true;
    api
      .findCurrentDeliveryCard()
      .then(({ result }) => {
        if (!alive) return;
        setCurrent(result ? toBoormiOrder(result) : null);
        setError(null);
      })
      .catch((e) => {
        if (!alive) return;
        setError(
          isApiError(e) ? e.message : "진행 중인 드림을 불러오지 못했어요.",
        );
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      {toast && (
        <div className="fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast icon="bell" title={toast.title} description={toast.description} />
        </div>
      )}

      <LocationBar
        location={currentAddress ?? currentAddressError ?? "위치 확인 중…"}
      />

      <Card variant="hero" className="flex flex-col gap-3">
        <p className="text-xl font-bold tracking-[-0.4px]">드리미 시작하기</p>
        <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
        <div className="flex gap-2">
          <Button
            variant="outline"
            className="shrink-0 whitespace-nowrap"
            onClick={onEndSession}
            disabled={endingSession}
          >
            종료
          </Button>
          <Button
            variant="primary"
            arrow
            block
            onClick={() => navigate(ROUTES.matching)}
          >
            드리미 시작하기
          </Button>
        </div>
        {matchingMessage && (
          <p className="text-2xs text-status-danger">{matchingMessage}</p>
        )}
      </Card>

      <SectionHeader title="진행 중인 드림" count={current ? 1 : 0} />

      {loading ? (
        <p className="py-6 text-center text-sm text-muted">불러오는 중…</p>
      ) : error ? (
        <p className="py-6 text-center text-sm text-status-danger">{error}</p>
      ) : current ? (
        // 클릭 시 실제 배달 건의 추적 화면으로 이동한다(단계는 추적 화면이 복원).
        <DeliveryCard
          icon={current.icon}
          title={current.title}
          route={current.route}
          status={current.statusLabel}
          progress={ORDER_PROGRESS[current.statusLabel]}
          onClick={() =>
            navigate(`${ROUTES.deliveryTrack}?orderId=${current.id}`)
          }
        />
      ) : (
        <p className="py-6 text-center text-sm text-muted">
          진행 중인 드림이 없어요.
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="오늘의 수익" value="₩0" variant="accent" />
        <StatCard label="완료 건수" value="0건" />
      </div>
    </>
  );
}

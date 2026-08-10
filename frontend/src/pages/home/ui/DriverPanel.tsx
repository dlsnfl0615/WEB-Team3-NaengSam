import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  DeliveryCard,
  LocationBar,
  SectionHeader,
  StatCard,
} from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { api, isApiError } from "@/shared/api";
import { useCurrentAddress } from "@/shared/lib";
import {
  ORDER_PROGRESS,
  toBoormiOrder,
  type BoormiOrder,
} from "@/shared/store/boormiOrderAdapter";

/** 홈 화면의 드리미(배송인) 본문. 현재 수행 중인 배달을 실제 API로 조회한다. */
export function DriverPanel() {
  const navigate = useNavigate();
  const { address: currentAddress, error: currentAddressError } =
    useCurrentAddress();
  const [current, setCurrent] = useState<BoormiOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
      <LocationBar
        location={currentAddress ?? currentAddressError ?? "위치 확인 중…"}
      />

      <Card variant="hero" className="flex flex-col gap-3">
        <p className="text-xl font-bold tracking-[-0.4px]">드리미 시작하기</p>
        <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
        <Button variant="primary" arrow block onClick={() => navigate(ROUTES.matching)}>
          드리미 시작하기
        </Button>
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

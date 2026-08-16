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
import { api } from "@/shared/api";
import { useCurrentAddress } from "@/shared/lib";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import {
  MATCHING_ORDER_CDS,
  ONGOING_ORDER_CDS,
  ORDER_PROGRESS,
} from "@/shared/store/boormiOrderAdapter";

/** 홈 화면의 부르미(발송인) 본문. 현재 진행 중인 부름을 실제 API로 조회한다. */
export function SenderPanel() {
  const navigate = useNavigate();
  const { address: currentAddress, error: currentAddressError } =
    useCurrentAddress();
  const orders = useBoormiOrderStore((s) => s.orders);
  const loading = useBoormiOrderStore((s) => s.loading);
  const error = useBoormiOrderStore((s) => s.error);
  const load = useBoormiOrderStore((s) => s.load);
  const [completedCount, setCompletedCount] = useState(0);
  const [totalSavedAmount, setTotalSavedAmount] = useState(0);

  useEffect(() => {
    load();
  }, [load]);

  // 누적 이용 건수 · 절감 금액. 보조 지표라 실패해도 화면을 막지 않고 0으로 둔다.
  useEffect(() => {
    let alive = true;
    api
      .getBoormiDashboard()
      .then(({ result }) => {
        if (!alive) return;
        setCompletedCount(result?.completedCount ?? 0);
        setTotalSavedAmount(result?.totalSavedAmount ?? 0);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const ongoing = orders.filter((o) => ONGOING_ORDER_CDS.has(o.orderCd));

  return (
    <>
      <LocationBar
        location={currentAddress ?? currentAddressError ?? "위치 확인 중…"}
      />

      <Card variant="hero" className="flex flex-col gap-3">
        <p className="text-xl font-bold tracking-[-0.4px]">물품 보내기</p>
        {/*<div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />*/}
        <Button
          variant="primary"
          arrow
          block
          onClick={() => navigate(ROUTES.requestCreate)}
        >
          물품 보내기
        </Button>
      </Card>

      <SectionHeader
        title="진행 중인 부름"
        count={ongoing.length}
        action="전체 보기"
        onAction={() => navigate(ROUTES.activity)}
      />

      {loading && ongoing.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">불러오는 중…</p>
      ) : error ? (
        <p className="py-6 text-center text-sm text-status-danger">{error}</p>
      ) : ongoing.length > 0 ? (
        <div className="flex flex-col gap-3">
          {ongoing.map((o) => (
            // 아직 드리미가 없으면 매칭 대기 화면으로, 확정된 뒤에는
            // SSE 기반 실시간 추적(부르미 수령인) 화면으로 이동한다.
            <DeliveryCard
              key={o.id}
              icon={o.icon}
              title={o.title}
              route={o.route}
              status={o.statusLabel}
              progress={ORDER_PROGRESS[o.statusLabel]}
              onClick={() =>
                navigate(
                  MATCHING_ORDER_CDS.has(o.orderCd)
                    ? `${ROUTES.matching}?orderId=${o.id}`
                    : `${ROUTES.deliveryDetail}?orderId=${o.id}`,
                )
              }
            />
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center py-6">
          <img
            src="/boormi-no-boorm.png"
            alt=""
            className="h-20 w-20 object-contain"
          />
          <p className="text-center text-sm text-muted">
            진행 중인 부름이 없어요.
          </p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="총 이용" value={`${completedCount}건`} />
        <StatCard
          label="절감 금액"
          value={`₩${totalSavedAmount.toLocaleString()}`}
          variant="accent"
        />
      </div>
    </>
  );
}

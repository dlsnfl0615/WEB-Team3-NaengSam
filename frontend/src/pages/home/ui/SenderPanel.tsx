import { useEffect } from "react";
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
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import {
  ONGOING_ORDER_CDS,
  ORDER_PROGRESS,
} from "@/shared/store/boormiOrderAdapter";

/** 홈 화면의 부르미(발송인) 본문. 현재 진행 중인 부름을 실제 API로 조회한다. */
export function SenderPanel() {
  const navigate = useNavigate();
  const orders = useBoormiOrderStore((s) => s.orders);
  const loading = useBoormiOrderStore((s) => s.loading);
  const error = useBoormiOrderStore((s) => s.error);
  const load = useBoormiOrderStore((s) => s.load);

  useEffect(() => {
    load();
  }, [load]);

  const ongoing = orders.filter((o) => ONGOING_ORDER_CDS.has(o.orderCd));

  return (
    <>
      <LocationBar location="Office Hub: Zone A" status="Connected" />

      <Card variant="hero" className="flex flex-col gap-3">
        <p className="text-xl font-bold tracking-[-0.4px]">물품 보내기</p>
        <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
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
            // 상세/진행 화면은 아직 mock 영역이라 클릭 이동은 연결하지 않는다.
            <DeliveryCard
              key={o.id}
              icon={o.icon}
              title={o.title}
              route={o.route}
              status={o.statusLabel}
              progress={ORDER_PROGRESS[o.statusLabel]}
            />
          ))}
        </div>
      ) : (
        <p className="py-6 text-center text-sm text-muted">
          진행 중인 부름이 없어요.
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="총 이용" value={`${orders.length}건`} />
        <StatCard label="절감 금액" value="₩45,000" variant="accent" />
      </div>
    </>
  );
}

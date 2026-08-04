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
import { useDeliveryStore } from "@/shared/store/deliveryStore";

/** 진행 중 상태별 진행바 값. */
const PROGRESS: Record<string, number> = {
  요청됨: 10,
  매칭중: 20,
  픽업중: 45,
  배송중: 75,
};

/** 홈 화면의 부르미(발송인) 본문. */
export function SenderPanel() {
  const navigate = useNavigate();
  const deliveries = useDeliveryStore((s) => s.deliveries);
  const setActive = useDeliveryStore((s) => s.setActive);

  const ongoing = deliveries.filter(
    (d) => d.myRole === "부르미" && d.status in PROGRESS,
  );

  const openDelivery = (id: string, status: string) => {
    setActive(id);
    navigate(status === "매칭중" ? ROUTES.matching : ROUTES.deliveryDetail);
  };

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

      {ongoing.length > 0 ? (
        <div className="flex flex-col gap-3">
          {ongoing.map((d) => (
            <DeliveryCard
              key={d.id}
              icon={d.icon}
              title={d.title}
              route={`${d.pickup} → ${d.dropoff}`}
              status={d.status}
              progress={PROGRESS[d.status]}
              onClick={() => openDelivery(d.id, d.status)}
            />
          ))}
        </div>
      ) : (
        <p className="py-6 text-center text-sm text-muted">
          진행 중인 부름이 없어요.
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="총 이용" value="12건" />
        <StatCard label="절감 금액" value="₩45,000" variant="accent" />
      </div>
    </>
  );
}

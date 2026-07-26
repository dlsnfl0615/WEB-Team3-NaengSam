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

/** 홈 화면의 부르미(발송인) 본문. */
export function SenderPanel() {
  const navigate = useNavigate();

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

      <SectionHeader title="진행 중인 부름" count={2} action="전체 보기" />

      <div className="flex flex-col gap-3">
        <DeliveryCard
          icon="document"
          title="서류 배송#123"
          route="Zone A → Zone C"
          status="배송중"
          progress={55}
        />
        <DeliveryCard
          icon="package"
          title="소형 택배"
          route="Zone A → Zone B"
          status="픽업중"
          progress={25}
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="총 이용" value="12건" />
        <StatCard label="절감 금액" value="₩45,000" variant="accent" />
      </div>
    </>
  );
}

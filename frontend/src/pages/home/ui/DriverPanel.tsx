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

/** 홈 화면의 드리미(배송인) 본문. */
export function DriverPanel() {
  const navigate = useNavigate();

  return (
    <>
      <LocationBar location="Office Hub: Zone A" status="4층 대기" />

      <Card variant="hero" className="flex flex-col gap-3">
        <p className="text-xl font-bold tracking-[-0.4px]">드리미 시작하기</p>
        <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
        <Button
          variant="primary"
          arrow
          block
          onClick={() => navigate(ROUTES.matching)}
        >
          드리미 시작하기
        </Button>
      </Card>

      <SectionHeader title="진행 중인 드림" action="상세 보기" />

      <DeliveryCard
        icon="drink"
        title="음료 배송 #B-882"
        route="파르나스 24F → 12F"
        status="픽업중"
        progress={40}
        onClick={() => navigate(ROUTES.deliveryTrack)}
      />

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="오늘의 수익" value="₩42,500" variant="accent" />
        <StatCard label="완료 건수" value="8건" />
      </div>
    </>
  );
}

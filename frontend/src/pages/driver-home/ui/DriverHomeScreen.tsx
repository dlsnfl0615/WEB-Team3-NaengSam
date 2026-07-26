import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  BottomNav,
  Button,
  Card,
  DeliveryCard,
  Icon,
  LocationBar,
  ScreenShell,
  SectionHeader,
  SegmentedToggle,
  StatCard,
} from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";

/**
 * 드리미(배송인) 홈 화면(Figma node 191:1208).
 * 역할 토글에서 드리미를 선택했을 때의 홈이며, 진행 중인 드림과 오늘 실적을 보여줍니다(UI 전용).
 */
export function DriverHomeScreen() {
  const navigate = useNavigate();
  const [role, setRole] = useState("드리미");
  const [tab, setTab] = useState("home");

  return (
    <ScreenShell>
      <header className="flex items-center">
        <h1 className="flex-1 text-xl font-bold tracking-[-0.4px] text-navy-900">
          쉼,부름
        </h1>
        <div className="flex items-center gap-3">
          <Icon name="bell" size={20} className="text-navy-900" />
          <Icon name="profile" size={20} className="text-navy-900" />
        </div>
      </header>

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={(value) => {
            setRole(value);
            if (value === "부르미") navigate(ROUTES.home);
          }}
        />

        <LocationBar location="Office Hub: Zone A" status="4층 대기" />

        <Card variant="hero" className="flex flex-col gap-3">
          <p className="text-xl font-bold tracking-[-0.4px]">드리미 시작하기</p>
          <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
          <Button variant="primary" arrow block>
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
        />

        <div className="grid grid-cols-2 gap-3">
          <StatCard label="오늘의 수익" value="₩42,500" variant="accent" />
          <StatCard label="완료 건수" value="8건" />
        </div>
      </main>

      <div className="pt-4">
        <BottomNav active={tab} onChange={setTab} />
      </div>
    </ScreenShell>
  );
}

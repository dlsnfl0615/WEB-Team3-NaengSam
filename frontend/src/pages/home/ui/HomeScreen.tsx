import { useState } from "react";
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

/**
 * 쉼,부름 부르미(발송인) 홈 화면(Figma node 191:592).
 * 폰 목업 없이 모바일 폭(max-w) 셸로 렌더합니다.
 */
export function HomeScreen() {
  const [role, setRole] = useState("부르미");
  const [tab, setTab] = useState("home");

  return (
    <ScreenShell>
      {/* 헤더 */}
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
          onChange={setRole}
        />

        <LocationBar location="Office Hub: Zone A" status="Connected" />

        {/* 히어로 카드 */}
        <Card variant="hero" className="flex flex-col gap-3">
          <p className="text-xl font-bold tracking-[-0.4px]">물품 보내기</p>
          <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
          <Button variant="primary" arrow className="w-5/5">
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
      </main>

      {/* 하단 네비 */}
      <div className="pt-4">
        <BottomNav active={tab} onChange={setTab} />
      </div>
    </ScreenShell>
  );
}

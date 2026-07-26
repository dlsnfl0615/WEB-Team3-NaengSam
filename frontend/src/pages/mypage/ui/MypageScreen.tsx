import { useState } from "react";
import { Badge, BottomNav, Card, Icon, ScreenShell, TopBar } from "@/shared/ui";
import { AccountSection } from "./AccountSection";
import { MenuGroup } from "./MenuGroup";
import { ACCOUNT_MENU, SUPPORT_MENU } from "./menus";

/**
 * 마이페이지 화면(Figma node 191:1574 계좌 미등록 / 191:1657 계좌 등록됨).
 * 프로필, 현금화 계좌, 계정·지원 메뉴를 보여줍니다.
 */
export function MypageScreen() {
  const [registered, setRegistered] = useState(false);

  return (
    <ScreenShell>
      <TopBar title="마이페이지" actions={["profile"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card className="flex flex-col items-center gap-1 py-5">
          <span className="size-14 rounded-pill border border-line bg-teal-50" />
          <p className="mt-1 text-lg font-bold text-navy-900">김드림</p>
          <p className="text-2xs text-muted">드리미 · 부르미</p>
          <Badge className="mt-1 gap-1">
            <Icon name="star" size={12} />
            4.9
          </Badge>
        </Card>

        <AccountSection
          registered={registered}
          onRegister={() => setRegistered(true)}
          onChange={() => setRegistered(false)}
        />

        <MenuGroup title="계정" items={ACCOUNT_MENU} />
        <MenuGroup title="지원" items={SUPPORT_MENU} />
      </main>

      <div className="pt-4">
        <BottomNav />
      </div>
    </ScreenShell>
  );
}

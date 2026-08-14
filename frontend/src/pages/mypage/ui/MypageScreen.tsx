import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, BottomNav, Card, Icon, ScreenShell, TopBar } from "@/shared/ui";
import { useSessionStore } from "@/shared/store/sessionStore";
import { ROUTES } from "@/shared/config/routes";
import { AccountSection } from "./AccountSection";
import { MenuGroup } from "./MenuGroup";
import { ACCOUNT_MENU, SUPPORT_MENU } from "./menus";

/**
 * 마이페이지 화면(Figma node 191:1574 계좌 미등록 / 191:1657 계좌 등록됨).
 * 프로필, 현금화 계좌, 계정·지원 메뉴를 보여줍니다.
 */
export function MypageScreen() {
  const navigate = useNavigate();
  const [registered, setRegistered] = useState(false);
  const user = useSessionStore((s) => s.user);
  const logout = useSessionStore((s) => s.logout);

  const name = user?.name ?? "게스트";
  const rolesLabel = user?.roles.join(" · ") ?? "부르미";
  const rating = user?.rating ?? 0;

  const handleLogout = async () => {
    // api.logout이 실패(예: 이미 만료)해도 store가 상태를 비우므로 무조건 로그인 화면으로 이동.
    try {
      await logout();
    } catch {
      /* 세션은 이미 정리됨 */
    }
    navigate(ROUTES.login, { replace: true });
  };

  // "로그아웃" 항목에만 동작을 주입한다(나머지 지원 메뉴는 표시만).
  const supportMenu = SUPPORT_MENU.map((item) =>
    item.label === "로그아웃" ? { ...item, onClick: handleLogout } : item,
  );

  return (
    <ScreenShell footer={<BottomNav />}>
      <TopBar title="마이페이지" actions={["profile"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card className="flex flex-col items-center gap-1 py-5">
          <span className="size-14 rounded-pill border border-line bg-teal-50" />
          <p className="mt-1 text-lg font-bold text-navy-900">{name}</p>
          <p className="text-2xs text-muted">{rolesLabel}</p>
          <Badge className="mt-1 gap-1">
            <Icon name="star" size={12} />
            {rating.toFixed(1)}
          </Badge>
        </Card>

        <AccountSection
          registered={registered}
          onChange={() => setRegistered(false)}
        />

        <MenuGroup title="계정" items={ACCOUNT_MENU} />
        <MenuGroup title="지원" items={supportMenu} />
      </main>
    </ScreenShell>
  );
}

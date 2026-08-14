import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, BottomNav, Card, Icon, ScreenShell, TopBar } from "@/shared/ui";
import { useSessionStore } from "@/shared/store/sessionStore";
import { ROUTES } from "@/shared/config/routes";
import { getProfileImage } from "@/shared/lib";
import { AccountSection } from "./AccountSection";
import { MenuGroup } from "./MenuGroup";
import { ACCOUNT_MENU, SUPPORT_MENU, VERIFY_MENU_LABEL } from "./menus";

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
  const boormiRating = user?.boormiRating ?? 0;
  // 드리미 평점은 승인된 드리미에게만 내려온다. 없으면 뱃지를 아예 띄우지 않는다.
  const dreamiRating = user?.dreamiRating;
  const profileImage = getProfileImage(user?.id ?? "guest");

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

  // 드리미 등록 항목: 승인된 드리미면 "완료" 뱃지만, 아니면 눌러서 본인인증 화면으로.
  // (roles에 "드리미"가 있으면 /me의 isDreami가 true — HomeScreen과 같은 판정)
  const isDreami = user?.roles.includes("드리미") ?? false;
  const accountMenu = ACCOUNT_MENU.map((item) =>
    item.label === VERIFY_MENU_LABEL
      ? isDreami
        ? { ...item, badge: "완료" }
        : { ...item, onClick: () => navigate(ROUTES.verify) }
      : item,
  );

  return (
    <ScreenShell footer={<BottomNav />}>
      <TopBar title="마이페이지" actions={["profile"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card className="flex flex-col items-center gap-1 py-5">
          <img
            src={profileImage}
            alt="프로필"
            className="size-14 rounded-pill border border-line bg-teal-50 object-cover"
          />
          <p className="mt-1 text-lg font-bold text-navy-900">{name}</p>
          <p className="text-2xs text-muted">{rolesLabel}</p>
          <div className="mt-1 flex items-center gap-2">
            <Badge className="gap-1">
              <Icon name="star" size={12} />
              부르미 {boormiRating.toFixed(1)}
            </Badge>
            {dreamiRating != null && (
              <Badge className="gap-1">
                <Icon name="star" size={12} />
                드리미 {dreamiRating.toFixed(1)}
              </Badge>
            )}
          </div>
        </Card>

        <AccountSection
          registered={registered}
          onChange={() => setRegistered(false)}
        />

        <MenuGroup title="계정" items={accountMenu} />
        <MenuGroup title="지원" items={supportMenu} />
      </main>
    </ScreenShell>
  );
}

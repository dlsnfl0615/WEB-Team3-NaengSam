import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { BottomNav, Icon, ScreenShell, SegmentedToggle } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import type { Role } from "@/shared/lib/role/RoleContext";
import { useRoleLocked } from "@/shared/store/deliveryStore";
import { useSessionStore } from "@/shared/store/sessionStore";
import { DriverPanel } from "./DriverPanel";
import { SenderPanel } from "./SenderPanel";

/**
 * 쉼,부름 홈 화면(Figma node 191:592, 191:1208).
 * 상단 토글로 부르미·드리미 본문을 같은 화면에서 즉시 전환합니다.
 */
export function HomeScreen() {
  const { role, setRole } = useRole();
  const roleLocked = useRoleLocked();
  const navigate = useNavigate();
  // 드리미 가능 여부(=/me의 isDreami). roles에 "드리미"가 있으면 등록된 드리미.
  const canBeDriver = useSessionStore((s) => s.user?.roles.includes("드리미") ?? false);

  // 미등록 상태에서 드리미로 전환 시도 → 등록/본인인증 화면으로 유도(전환은 막음).
  const handleRoleChange = (value: string) => {
    const next = value as Role;
    if (next === "드리미" && !canBeDriver) {
      navigate(ROUTES.verify);
      return;
    }
    setRole(next);
  };

  // 계정 전환 등으로 드리미 상태가 남아있어도 미등록이면 부르미로 되돌린다.
  useEffect(() => {
    if (!canBeDriver && role === "드리미") setRole("부르미");
  }, [canBeDriver, role, setRole]);

  return (
    <ScreenShell footer={<BottomNav />}>
      {/* 헤더 */}
      <header className="flex items-center">
        <h1 className="flex-1 text-xl font-bold tracking-[-0.4px] text-navy-900">
          쉼,부름
        </h1>
        <div className="flex items-center gap-3">
          <Icon name="bell" size={20} className="text-navy-900" />
          <button
            type="button"
            aria-label="마이페이지"
            onClick={() => navigate(ROUTES.mypage)}
          >
            <Icon name="profile" size={20} className="text-navy-900" />
          </button>
        </div>
      </header>

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={handleRoleChange}
          disabled={roleLocked}
        />

        {role === "부르미" ? <SenderPanel /> : <DriverPanel />}
      </main>
    </ScreenShell>
  );
}

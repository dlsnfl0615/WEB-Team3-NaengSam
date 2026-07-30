import { useNavigate } from "react-router-dom";
import { BottomNav, Icon, ScreenShell, SegmentedToggle } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import type { Role } from "@/shared/lib/role/RoleContext";
import { DriverPanel } from "./DriverPanel";
import { SenderPanel } from "./SenderPanel";

/**
 * 쉼,부름 홈 화면(Figma node 191:592, 191:1208).
 * 상단 토글로 부르미·드리미 본문을 같은 화면에서 즉시 전환합니다.
 */
export function HomeScreen() {
  const { role, setRole } = useRole();
  const navigate = useNavigate();

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
          onChange={(value) => setRole(value as Role)}
        />

        {role === "부르미" ? <SenderPanel /> : <DriverPanel />}
      </main>
    </ScreenShell>
  );
}

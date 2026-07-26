import { BottomNav, ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { useRole } from "@/shared/lib/role/useRole";
import type { Role } from "@/shared/lib/role/RoleContext";
import { DriverEarnings } from "./DriverEarnings";
import { SenderSavings } from "./SenderSavings";

/**
 * 수익·절감 리포트 화면(Figma node 191:1007, 191:208).
 * 상단 토글로 부르미(절감 리포트)·드리미(수익) 본문을 같은 화면에서 전환합니다.
 */
export function EarningsScreen() {
  const { role, setRole } = useRole();

  const isDriver = role === "드리미";

  return (
    <ScreenShell>
      <TopBar
        title={isDriver ? "수익" : "절감 리포트"}
        actions={["document", "profile"]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={(value) => setRole(value as Role)}
        />

        {isDriver ? <DriverEarnings /> : <SenderSavings />}
      </main>

      <div className="pt-4">
        <BottomNav />
      </div>
    </ScreenShell>
  );
}

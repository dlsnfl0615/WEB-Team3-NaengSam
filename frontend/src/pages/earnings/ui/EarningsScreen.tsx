import { useNavigate } from "react-router-dom";
import { ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { useRole } from "@/shared/lib/role/useRole";
import type { Role } from "@/shared/lib/role/RoleContext";
import { useRoleLocked } from "@/shared/store/deliveryStore";
import { DriverEarnings } from "./DriverEarnings";
import { SenderSavings } from "./SenderSavings";

/**
 * 수익·절감 리포트 화면(Figma node 191:1007, 191:208).
 * 상단 토글로 부르미(절감 리포트)·드리미(수익) 본문을 같은 화면에서 전환합니다.
 * 탭 화면이 아니라 내 지갑에서 열리는 별도 화면이라 하단 탭 바가 없습니다.
 */
export function EarningsScreen() {
  const navigate = useNavigate();
  const { role, setRole } = useRole();
  const roleLocked = useRoleLocked();

  const isDriver = role === "드리미";

  return (
    <ScreenShell>
      <TopBar
        title={isDriver ? "수익" : "절감 리포트"}
        onBack={() => navigate(-1)}
        actions={["document", "profile"]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={(value) => setRole(value as Role)}
          disabled={roleLocked}
        />

        {isDriver ? <DriverEarnings /> : <SenderSavings />}
      </main>
    </ScreenShell>
  );
}

import { useEffect } from "react";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import { ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { useRole } from "@/shared/lib/role/useRole";
import { useRoleSwitch } from "@/shared/lib/role/useRoleSwitch";
import { useRoleLocked } from "@/shared/lib/role/useRoleLocked";
import { useSessionStore } from "@/shared/store/sessionStore";
import { DriverEarnings } from "./DriverEarnings";
import { SenderSavings } from "./SenderSavings";

/**
 * 수익·절감 리포트 화면(Figma node 191:1007, 191:208).
 * 상단 토글로 부르미(절감 리포트)·드리미(수익) 본문을 같은 화면에서 전환합니다.
 * 탭 화면이 아니라 내 지갑에서 열리는 별도 화면이라 하단 탭 바가 없습니다.
 */
export function EarningsScreen() {
  const backOrHome = useBackOrHome();
  const { role } = useRole();
  const { onRoleChange, pending, error } = useRoleSwitch();
  const { locked: roleLocked, reason: roleLockReason } = useRoleLocked();
  const refreshUser = useSessionStore((s) => s.refreshUser);

  // 토글이 보이는 화면에 들어올 때마다 수행 중인 역할을 최신화해 잠금 상태를 맞춘다.
  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const isDriver = role === "드리미";

  return (
    <ScreenShell>
      <TopBar
        title={isDriver ? "수익" : "절감 리포트"}
        onBack={backOrHome}
        actions={["profile"]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={onRoleChange}
          disabled={roleLocked || pending}
        />

        {error && <p className="text-2xs text-status-danger">{error}</p>}
        {!error && roleLocked && roleLockReason && (
          <p className="text-2xs text-navy-500">{roleLockReason}</p>
        )}

        {isDriver ? <DriverEarnings /> : <SenderSavings />}
      </main>
    </ScreenShell>
  );
}

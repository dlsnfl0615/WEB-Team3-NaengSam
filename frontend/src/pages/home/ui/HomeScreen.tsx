import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BottomNav, Icon, ScreenShell, SegmentedToggle } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { useRoleSwitch } from "@/shared/lib/role/useRoleSwitch";
import { useRoleLocked } from "@/shared/lib/role/useRoleLocked";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useToastStore } from "@/shared/store/toastStore";
import { DriverPanel } from "./DriverPanel";
import { SenderPanel } from "./SenderPanel";

/**
 * 쉼,부름 홈 화면(Figma node 191:592, 191:1208).
 * 상단 토글로 부르미·드리미 본문을 같은 화면에서 즉시 전환합니다.
 */
export function HomeScreen() {
  const { role, setRole } = useRole();
  const { locked: roleLocked, reason: lockReason } = useRoleLocked();
  const refreshUser = useSessionStore((s) => s.refreshUser);
  const navigate = useNavigate();
  const location = useLocation();
  const showToast = useToastStore((state) => state.show);
  // 드리미 가능 여부(=/me의 isDreami). roles에 "드리미"가 있으면 등록된 드리미.
  const canBeDriver = useSessionStore(
    (s) => s.user?.roles.includes("드리미") ?? false,
  );

  // 드리미 전환은 서버 검증(승인 여부·수행 중인 주문)을 통과해야 반영된다.
  // 미등록·미승인이면 훅이 본인인증 화면으로 보낸다.
  const { onRoleChange, pending, error } = useRoleSwitch();

  // 토글이 보이는 화면에 들어올 때마다 수행 중인 역할을 최신화해 잠금 상태를 맞춘다.
  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  // 계정 전환 등으로 드리미 상태가 남아있어도 미등록이면 부르미로 되돌린다.
  useEffect(() => {
    if (!canBeDriver && role === "드리미") setRole("부르미");
  }, [canBeDriver, role, setRole]);

  // 본인인증 제출 직후 안내하고, 뒤로가기/새로고침으로 다시 뜨지 않도록 router state를 비운다.
  useEffect(() => {
    const state = location.state as { dreamiVerificationSubmitted?: boolean } | null;
    if (!state?.dreamiVerificationSubmitted) return;
    showToast({
      icon: "bell",
      title: "심사 요청이 접수됐어요",
      description: "심사 요청이 접수됐어요.",
      dedupeKey: "dreami-verification-submitted",
    });
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate, showToast]);

  return (
    <ScreenShell footer={<BottomNav />}>
      {/* 헤더 */}
      <header className="flex items-center">
        <h1 className="flex-1 text-xl font-bold tracking-[-0.4px] text-navy-900">
          쉼,부름
        </h1>
        <div className="flex items-center gap-3">
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
          onChange={onRoleChange}
          disabled={roleLocked || pending}
        />

        {error && <p className="text-2xs text-status-danger">{error}</p>}
        {!error && roleLocked && lockReason && (
          <p className="text-2xs text-navy-500">{lockReason}</p>
        )}

        {role === "부르미" ? <SenderPanel /> : <DriverPanel />}
      </main>
    </ScreenShell>
  );
}

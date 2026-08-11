import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BottomNav, Icon, ScreenShell, SegmentedToggle, Toast } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { useRoleSwitch } from "@/shared/lib/role/useRoleSwitch";
import { useRoleLocked } from "@/shared/store/deliveryStore";
import { useSessionStore } from "@/shared/store/sessionStore";
import { DriverPanel } from "./DriverPanel";
import { SenderPanel } from "./SenderPanel";

const TRANSIENT_TOAST_MS = 4000;

/**
 * 쉼,부름 홈 화면(Figma node 191:592, 191:1208).
 * 상단 토글로 부르미·드리미 본문을 같은 화면에서 즉시 전환합니다.
 */
export function HomeScreen() {
  const { role, setRole } = useRole();
  const roleLocked = useRoleLocked();
  const navigate = useNavigate();
  const location = useLocation();
  // 드리미 가능 여부(=/me의 isDreami). roles에 "드리미"가 있으면 등록된 드리미.
  const canBeDriver = useSessionStore(
    (s) => s.user?.roles.includes("드리미") ?? false,
  );

  // 드리미 전환은 서버 검증(승인 여부·수행 중인 주문)을 통과해야 반영된다.
  // 미등록·미승인이면 훅이 본인인증 화면으로 보낸다.
  const { onRoleChange, pending, error } = useRoleSwitch();

  // 계정 전환 등으로 드리미 상태가 남아있어도 미등록이면 부르미로 되돌린다.
  useEffect(() => {
    if (!canBeDriver && role === "드리미") setRole("부르미");
  }, [canBeDriver, role, setRole]);

  // 본인인증 제출 직후 홈으로 오면 "심사 중" 토스트를 잠깐 띄운다(alert 대신 자연스럽게).
  // 최초 렌더 시점의 location.state를 그대로 초기값으로 삼는다(effect에서 setState하지 않음).
  const [toast, setToast] = useState<{ title: string; description?: string } | null>(() => {
    const state = location.state as { dreamiVerificationSubmitted?: boolean } | null;
    return state?.dreamiVerificationSubmitted
      ? {
          title: "심사 요청이 접수됐어요",
          description: "심사 요청이 접수됐어요.",
        }
      : null;
  });
  const toastTimer = useRef<number | null>(null);

  // 뒤로가기/새로고침으로 같은 토스트가 다시 뜨지 않도록 router state를 한 번만 비운다.
  useEffect(() => {
    const state = location.state as { dreamiVerificationSubmitted?: boolean } | null;
    if (!state?.dreamiVerificationSubmitted) return;
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  // 토스트 자동 소멸.
  useEffect(() => {
    if (!toast) return;
    toastTimer.current = window.setTimeout(() => setToast(null), TRANSIENT_TOAST_MS);
    return () => {
      if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    };
  }, [toast]);

  return (
    <ScreenShell footer={<BottomNav />}>
      {/* 심사 접수 토스트(화면 위에 떠서 표시) */}
      {toast && (
        <div className="fixed inset-x-0 top-4 z-50 mx-auto max-w-[420px] px-4">
          <Toast icon="bell" title={toast.title} description={toast.description} />
        </div>
      )}

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

        {role === "부르미" ? <SenderPanel /> : <DriverPanel />}
      </main>
    </ScreenShell>
  );
}

import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, MapCard, Modal, ScreenShell, TopBar } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";

/**
 * 매칭(찾는 중) 화면(Figma node 191:763).
 * 지도 위에서 대기 상태를 보여준다. 실제 오퍼/콜 팝업은 전역 `MatchingPopup`이
 * 담당하므로 다른 화면으로 이동해도 이어서 뜬다.
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { role } = useRole();
  const startDreamiSession = useMatchingStore((s) => s.startDreamiSession);
  const nearbyCalls = useMatchingStore((s) => s.nearbyCalls);
  const online = useMatchingStore((s) => s.online);
  const message = useMatchingStore((s) => s.message);

  const isDriver = role === "드리미";
  const counterpart = isDriver ? "부르미" : "드리미";

  // 부르미가 홈 카드로 들어오면 어떤 부름인지 알 수 있어 취소를 제공한다.
  const orderId = params.get("orderId");
  const cancelable = !isDriver && Boolean(orderId);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // 드리미: 진입 시 온라인 전환 + 주변 콜 조회. 오퍼 팝업은 전역 `MatchingPopup`이 받는다.
  // 화면을 떠나도 온라인은 유지한다(오프라인 전환은 명시적 토글로만).
  useEffect(() => {
    if (!isDriver) return;
    void startDreamiSession();
  }, [isDriver, startDreamiSession]);

  // 모달에서 확정하면 매칭 큐에서 부름을 회수하고 홈으로 돌아간다.
  const confirmCancel = async () => {
    if (!orderId || canceling) return;
    setCanceling(true);
    setCancelError(null);
    try {
      await useBoormiOrderStore.getState().cancelOrder(orderId);
      setConfirmOpen(false);
      navigate(ROUTES.home, { replace: true });
    } catch (e) {
      setCancelError(
        isApiError(e)
          ? e.message
          : "부름 취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setCanceling(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar
        title={`${counterpart}를 찾는 중`}
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <MapCard height={280} />

        {/* 드리미인데 온라인 전환에 실패하면 콜이 영영 안 오므로, 사유와 재시도를 화면에 드러낸다. */}
        {isDriver && !online ? (
          <Card className="flex flex-col gap-2">
            <p className="text-base font-bold text-navy-900">
              콜을 받을 수 없는 상태예요
            </p>
            <p className="text-2xs text-muted">
              {message ?? "위치를 확인하고 있어요..."}
            </p>
            {message && (
              <Button
                variant="primary"
                block
                onClick={() => void startDreamiSession()}
              >
                다시 시도
              </Button>
            )}
          </Card>
        ) : (
          <Card className="flex flex-col gap-1">
            <p className="flex items-center gap-2 text-base font-bold text-navy-900">
              <span className="size-2 rounded-pill bg-teal-500" />
              {isDriver
                ? `근방 3km 내 부름 ${nearbyCalls.length}건 대기중`
                : `${counterpart}를 찾고 있어요`}
            </p>
            <p className="text-2xs text-muted">
              요청을 보낸 {counterpart}의 수락을 기다리고 있어요...
            </p>
          </Card>
        )}
      </main>

      {cancelable && (
        <footer className="pt-4">
          <Button
            block
            variant="outline"
            onClick={() => {
              setCancelError(null);
              setConfirmOpen(true);
            }}
          >
            부름 취소하기
          </Button>
        </footer>
      )}

      <Modal
        open={confirmOpen}
        label="부름 취소 확인"
        onClose={canceling ? undefined : () => setConfirmOpen(false)}
      >
        <Card className="flex flex-col gap-4 text-center">
          <div className="flex flex-col gap-1">
            <h2 className="text-md font-bold text-navy-900">
              정말로 취소하시겠습니까?
            </h2>
            <p className="text-2xs text-muted">
              취소하면 매칭이 중단되고 등록한 부름이 사라져요.
            </p>
          </div>
          {cancelError && (
            <p className="text-2xs text-status-danger">{cancelError}</p>
          )}
          <div className="flex gap-2">
            <Button
              variant="outline"
              block
              disabled={canceling}
              onClick={() => setConfirmOpen(false)}
            >
              돌아가기
            </Button>
            <Button block disabled={canceling} onClick={confirmCancel}>
              {canceling ? "취소 중…" : "취소하기"}
            </Button>
          </div>
        </Card>
      </Modal>
    </ScreenShell>
  );
}

import { use, useState } from "react";
import { useSessionStore } from "@/shared/store/sessionStore";
import { Button, Card, Modal } from "@/shared/ui";
import { SseContext } from "./SseContext";

/**
 * 실시간 연결이 영구 종료(`closed`)됐을 때만 뜨는 안내 모달. 브라우저의 무한 자동 재연결이 멈춘
 * 상태이므로 사용자가 직접 다시 연결하도록 안내한다. 일시적 재연결(reconnecting)이나 대표 탭 승계
 * 과정에서는 뜨지 않는다 — 그 두 경우는 이 컴포넌트가 구독하는 `status`가 각각 `reconnecting`/
 * `connected`로 유지되기 때문이다. 닫기(dismiss)와 수동 재연결(`reconnect`)을 제공한다.
 */
export function SseStatusBanner() {
  const context = use(SseContext);
  const isAuthenticated = useSessionStore((s) => s.isAuthenticated);
  const [dismissed, setDismissed] = useState(false);
  const status = context?.status;

  // status가 바뀌면(예: closed에서 벗어남) 다음 closed에 다시 뜨도록 dismiss를 초기화한다.
  // effect가 아니라 렌더 중 조정 패턴을 쓴다 — https://react.dev/learn/you-might-not-need-an-effect
  const [seenStatus, setSeenStatus] = useState(status);
  if (status !== seenStatus) {
    setSeenStatus(status);
    setDismissed(false);
  }

  if (!context || !isAuthenticated || status !== "closed" || dismissed) return null;

  return (
    <Modal
      open
      label="실시간 연결 종료 안내"
      onClose={() => setDismissed(true)}
    >
      <Card className="flex flex-col gap-4 text-center">
        <div className="flex flex-col gap-1">
          <h2 className="text-md font-bold text-navy-900">
            실시간 연결이 종료됐어요
          </h2>
          <p className="text-2xs text-muted">
            네트워크 상태를 확인한 뒤 다시 연결해주세요.
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" block onClick={() => setDismissed(true)}>
            닫기
          </Button>
          <Button block onClick={() => context.reconnect()}>
            다시 연결
          </Button>
        </div>
      </Card>
    </Modal>
  );
}

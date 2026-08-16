import { Card, Modal, ProgressBar } from "@/shared/ui";
import type { LoginQueueState } from "@/shared/store/sessionStore";

export interface LoginQueueModalProps {
  /** 대기 중이 아니면 null. */
  queue: LoginQueueState | null;
}

/** 예상 대기 시간을 "약 3분 20초" 형태로. */
function formatWait(seconds: number): string {
  if (seconds < 60) return `약 ${seconds}초`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return rest === 0 ? `약 ${minutes}분` : `약 ${minutes}분 ${rest}초`;
}

/**
 * 로그인 대기열 안내 모달.
 * `onClose`를 넘기지 않아 닫히지 않는다 — 대기 중에 로그인 폼으로 돌아가 다시 제출하면
 * 티켓만 하나 더 늘고 순번은 뒤로 밀린다.
 */
export function LoginQueueModal({ queue }: LoginQueueModalProps) {
  if (!queue) return null;

  const done = queue.initialPosition - queue.position;

  return (
    <Modal open label="로그인 대기열">
      <Card className="flex flex-col gap-4 text-center">
        <div className="flex flex-col gap-1">
          <h2 className="text-md font-bold text-navy-900">
            접속자가 많아 대기 중이에요
          </h2>
          <p className="text-2xs text-muted">
            차례가 되면 자동으로 로그인돼요. 이 화면을 닫지 말아주세요.
          </p>
        </div>

        <div className="flex flex-col gap-2">
          <ProgressBar value={(done / queue.initialPosition) * 100} />
          <div className="flex justify-between text-2xs text-muted">
            <span>
              내 순번{" "}
              <strong className="text-navy-900">{queue.position}</strong>번째
            </span>
            <span>총 {queue.totalWaiting}명 대기</span>
          </div>
        </div>

        <p className="text-2xs text-muted">
          예상 대기 시간 {formatWait(queue.estimatedWaitSeconds)}
        </p>
      </Card>
    </Modal>
  );
}

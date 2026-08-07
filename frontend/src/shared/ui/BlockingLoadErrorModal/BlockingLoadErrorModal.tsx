import { Button } from "../Button/Button";
import { Card } from "../Card/Card";
import { Modal } from "../Modal/Modal";

export interface BlockingLoadErrorModalProps {
  open: boolean;
  message: string;
  retrying: boolean;
  canRetry: boolean;
  onRetry: () => void;
  onExit: () => void;
}

/** 필수 초기 데이터를 불러오지 못했을 때 화면 기능을 차단하는 오류 모달. */
export function BlockingLoadErrorModal({
  open,
  message,
  retrying,
  canRetry,
  onRetry,
  onExit,
}: BlockingLoadErrorModalProps) {
  return (
    <Modal open={open} label="배달 정보 조회 실패">
      <Card className="flex flex-col gap-4 text-center">
        <div className="flex flex-col gap-1">
          <h2 className="text-md font-bold text-navy-900">
            배달 정보를 불러오지 못했어요
          </h2>
          <p className="text-2xs text-muted">{message}</p>
          <p className="text-2xs text-muted">
            정보를 확인하기 전에는 배달 기능을 사용할 수 없어요.
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" block onClick={onExit}>
            홈으로 돌아가기
          </Button>
          {canRetry && (
            <Button block disabled={retrying} onClick={onRetry}>
              {retrying ? "재시도 중…" : "재시도하기"}
            </Button>
          )}
        </div>
      </Card>
    </Modal>
  );
}

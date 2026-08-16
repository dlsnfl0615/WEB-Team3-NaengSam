import { Button, Card, Icon } from "@/shared/ui";

export interface PushEnableCardProps {
  /** 왜 지금 묻는지 설명하는 한 줄. 역할별로 다르다. */
  description: string;
  onEnable: () => void;
  onDismiss: () => void;
  enabling?: boolean;
}

/**
 * 알림 권한을 요청하는 제스처 표면.
 *
 * <p>콜드 로드에서 자동으로 띄우지 않는다. 자동 요청은 되돌릴 수 없는 거절을 수집하는 방법이고,
 * `denied`는 페이지에서 재요청할 수 없다. 사용자의 의도가 최고조인 순간(온라인 전환, 콜 등록 직후)에만
 * 이 카드를 보여주고, 실제 권한 요청은 버튼 클릭이라는 제스처 안에서 일어난다.
 */
export function PushEnableCard({
  description,
  onEnable,
  onDismiss,
  enabling = false,
}: PushEnableCardProps) {
  return (
    <Card variant="accent" className="flex flex-col gap-3">
      <div className="flex items-start gap-2">
        <Icon name="bell" className="mt-0.5 shrink-0" />
        <div className="flex flex-col gap-1">
          <p className="text-md font-bold">{description}</p>
          {/* 문구 규율: "콜을 받을 수 있어요"는 거짓이다. 푸시는 wake-up 봉투만 나른다. */}
          <p className="text-sm text-muted">
            앱을 닫아도 놓친 알림을 알려드려요.
          </p>
        </div>
      </div>
      <div className="flex gap-2">
        <Button size="sm" onClick={onEnable} disabled={enabling}>
          {enabling ? "여는 중…" : "알림 허용하기"}
        </Button>
        <Button size="sm" variant="outline" onClick={onDismiss}>
          나중에
        </Button>
      </div>
    </Card>
  );
}

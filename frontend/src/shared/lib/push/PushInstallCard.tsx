import { Button, Card, Icon } from "@/shared/ui";

export interface PushInstallCardProps {
  onInstall: () => void;
}

/**
 * 홈 화면 설치 제안(Android). `beforeinstallprompt`를 캡처했을 때만 렌더된다.
 *
 * <p>설치는 푸시의 전제조건이 <b>아니다</b> — Android Chrome은 일반 탭에서도 푸시를 받는다.
 * 그래서 알림 권한 요청과 동시에 띄우지 않고, 물어볼 것이 없을 때만 조용히 제안한다.
 * (iOS는 반대로 설치가 하드 요구사항이라 {@link PushGuidanceCard}가 수동 안내를 담당한다.)
 */
export function PushInstallCard({ onInstall }: PushInstallCardProps) {
  return (
    <Card className="flex items-center justify-between gap-3">
      <div className="flex items-start gap-2">
        <Icon name="home" className="mt-0.5 shrink-0" />
        <div className="flex flex-col gap-1">
          <p className="text-base font-bold text-navy-900">앱으로 설치할까요?</p>
          <p className="text-2xs text-muted">
            홈 화면에서 바로 열 수 있어요.
          </p>
        </div>
      </div>
      <Button size="sm" variant="outline" onClick={onInstall}>
        설치
      </Button>
    </Card>
  );
}

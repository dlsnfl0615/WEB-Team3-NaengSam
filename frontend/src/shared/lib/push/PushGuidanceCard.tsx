import { useState } from "react";
import { Button, Card, Icon } from "@/shared/ui";
import type { PushState } from "./usePushSubscription";

export interface PushGuidanceCardProps {
  state: PushState;
}

/** iOS 홈 화면 추가 절차. 자동화도 프롬프트도 불가능해서 정적 안내가 기술적으로 가능한 전부다. */
const IOS_INSTALL_STEPS = [
  "하단 공유 버튼을 누르세요",
  '목록에서 "홈 화면에 추가"를 고르세요',
  '오른쪽 위 "추가"를 누르세요',
];

/**
 * 푸시를 켤 수 없는 상태의 폴백 안내.
 *
 * <p>인앱브라우저 카드는 장식이 아니라 하중을 받는 인프라다. 카카오톡으로 유입된 사용자에게는
 * 서비스워커 푸시도 홈 화면 추가도 없고 우회법이 존재하지 않으므로, 외부 브라우저로 여는 것이
 * 이 사용자들에게 푸시가 존재할 수 있는 유일한 경로다.
 */
export function PushGuidanceCard({ state }: PushGuidanceCardProps) {
  const [copied, setCopied] = useState(false);

  if (state === "in-app-browser") {
    const copyLink = async () => {
      try {
        await navigator.clipboard.writeText(window.location.href);
        setCopied(true);
      } catch {
        setCopied(false);
      }
    };

    return (
      <Card variant="accent" className="flex flex-col gap-3">
        <div className="flex items-start gap-2">
          <Icon name="bell" className="mt-0.5 shrink-0" />
          <div className="flex flex-col gap-1">
            <p className="text-md font-bold">알림을 받으려면 브라우저로 열어주세요</p>
            <p className="text-sm text-muted">
              카카오톡 등 앱 안의 브라우저에서는 알림을 켤 수 없어요. 오른쪽 아래 메뉴에서
              &ldquo;다른 브라우저로 열기&rdquo;를 눌러주세요.
            </p>
          </div>
        </div>
        <Button size="sm" variant="outline" onClick={() => void copyLink()}>
          {copied ? "링크를 복사했어요" : "링크 복사하기"}
        </Button>
      </Card>
    );
  }

  if (state === "needs-install") {
    return (
      <Card variant="accent" className="flex flex-col gap-3">
        <div className="flex items-start gap-2">
          <Icon name="bell" className="mt-0.5 shrink-0" />
          <div className="flex flex-col gap-1">
            <p className="text-md font-bold">홈 화면에 추가하면 알림을 받을 수 있어요</p>
            <p className="text-sm text-muted">
              아이폰은 홈 화면에 추가한 뒤에만 알림을 켤 수 있어요.
            </p>
          </div>
        </div>
        <ol className="flex flex-col gap-1 text-sm text-navy-700">
          {IOS_INSTALL_STEPS.map((step, index) => (
            <li key={step}>
              {index + 1}. {step}
            </li>
          ))}
        </ol>
      </Card>
    );
  }

  if (state === "denied") {
    return (
      <Card variant="accent" className="flex items-start gap-2">
        <Icon name="bell" className="mt-0.5 shrink-0" />
        <div className="flex flex-col gap-1">
          <p className="text-md font-bold">알림이 꺼져 있어요</p>
          <p className="text-sm text-muted">
            기기 설정 → 알림에서 쉼,부름을 허용해주세요. 앱 안에서는 다시 요청할 수 없어요.
          </p>
        </div>
      </Card>
    );
  }

  // unsupported/default/granted는 안내할 것이 없다. 안내 자체가 소음이다.
  return null;
}

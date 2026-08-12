import { PushEnableCard } from "./PushEnableCard";
import { PushGuidanceCard } from "./PushGuidanceCard";
import { PushInstallCard } from "./PushInstallCard";
import { usePushSubscription, type PushState } from "./usePushSubscription";

export interface PushPromptProps {
  /**
   * 지금이 물어봐도 되는 순간인지. 온라인 전환 직후, 콜 등록 직후처럼 사용자의 의도가
   * 문자 그대로 "알려줘"인 시점에만 true로 준다. 콜드 로드에서는 절대 켜지 않는다.
   */
  active: boolean;
  /** 왜 지금 묻는지 설명하는 한 줄. 역할에 맞는 문구를 호출처가 준다. */
  description: string;
}

/** 안내 카드를 띄울 상태들. 나머지는 안내할 것이 없다(안내 자체가 소음이다). */
const GUIDED_STATES = new Set<PushState>([
  "in-app-browser",
  "needs-install",
  "denied",
]);

/**
 * 푸시 권한 요청과 폴백 안내를 상태에 따라 하나로 묶는다. 화면은 "지금이 물어볼 순간인가"만
 * 판단하고, 어떤 카드를 띄울지는 이 컴포넌트가 정한다.
 */
export function PushPrompt({ active, description }: PushPromptProps) {
  const {
    state,
    shouldPrompt,
    canInstall,
    enable,
    dismiss,
    promptInstall,
    enabling,
  } = usePushSubscription();

  if (!active) return null;

  // 우선순위: 물어볼 것 → 안내할 것 → 둘 다 없을 때만 설치 제안.
  // 알림 권한과 설치를 한 화면에서 동시에 묻지 않기 위한 순서다.
  if (shouldPrompt) {
    return (
      <PushEnableCard
        description={description}
        onEnable={() => void enable()}
        onDismiss={dismiss}
        enabling={enabling}
      />
    );
  }
  if (GUIDED_STATES.has(state)) {
    return <PushGuidanceCard state={state} />;
  }
  if (canInstall) {
    return <PushInstallCard onInstall={() => void promptInstall()} />;
  }
  return null;
}

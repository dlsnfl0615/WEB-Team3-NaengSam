import { Icon, OfferCountdownBar } from "@/shared/ui";

export interface AwaitingBoormiCardProps {
  /** 수락한 콜의 물품명. payload에 없으면 null. */
  itemName: string | null;
  /** 부르미 확정 응답 카운트다운. */
  countdown: { remainingSeconds: number; progressPercent: number };
}

/**
 * 드리미가 콜을 수락한 뒤, 부르미가 확정할 때까지 기다리는 동안 띄우는 카드.
 *
 * 수락 = 배달 시작이 아니다. 부르미가 한 번 더 확정해야 배달이 열리는데, 그 사이 드리미 화면에는
 * 아무것도 남지 않아 "수락이 씹혔나?"로 보였다. 이 카드는 그 공백만 메우고 액션은 두지 않는다 —
 * 결말(확정·거절·마감)은 전부 서버 이벤트가 정하고, 그때 카드가 사라진다.
 */
export function AwaitingBoormiCard({
  itemName,
  countdown,
}: AwaitingBoormiCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-md border border-line bg-surface p-4 shadow-card">
      <div className="flex items-center gap-3">
        <span className="flex size-9 shrink-0 animate-pulse items-center justify-center rounded-pill bg-teal-50 text-teal-700">
          <Icon name="bell" size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-base font-bold text-navy-900">
            부르미의 응답을 기다리고 있어요…
          </p>
          <p className="break-words text-2xs text-muted">
            {itemName ? `'${itemName}' 콜을 수락했어요. ` : "콜을 수락했어요. "}
            부르미가 확정하면 배달이 시작돼요.
          </p>
        </div>
      </div>

      <OfferCountdownBar
        label="부르미 응답 대기"
        remainingSeconds={countdown.remainingSeconds}
        progressPercent={countdown.progressPercent}
      />
    </div>
  );
}

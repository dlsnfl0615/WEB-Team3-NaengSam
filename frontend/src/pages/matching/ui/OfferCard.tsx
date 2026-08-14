import { getProfileImage } from "@/shared/lib";
import { Button, Icon, OfferCountdownBar } from "@/shared/ui";

export interface OfferCardProps {
  /** 카드 상단 안내 문구(예: "새 드리미 요청 도착!") */
  heading: string;
  /** 요청을 보낸 드리미 UUID. 기본 프로필 이미지 선택에 사용합니다. */
  dreamiId: string;
  name: string;
  /** 드리미 평점(getProfile 응답의 dreamiAvgScore). */
  rating: number;
  /** 픽업 예상 소요 시간(분). 아직 확인되지 않았으면 null. */
  pickupEtaMinutes: number | null;
  /** 확정 응답 카운트다운(부르미가 드리미를 확정하는 시간). */
  countdown: { remainingSeconds: number; progressPercent: number };
  onReject: () => void;
  onAccept: () => void;
}

/**
 * 부르미에게 도착한 드리미 요청 카드(수락·거절).
 * 드리미가 받는 부름은 필드가 달라 CallCard를 씁니다.
 */
export function OfferCard({
  heading,
  dreamiId,
  name,
  rating,
  pickupEtaMinutes,
  countdown,
  onReject,
  onAccept,
}: OfferCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-teal-500 bg-white p-4 shadow-card">
      <p className="text-2xs font-bold text-teal-700">{heading}</p>

      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <img
            src={getProfileImage(dreamiId)}
            alt="드리미 프로필"
            className="size-9 rounded-pill bg-teal-50 object-cover"
          />
          <div className="flex flex-col">
            <p className="text-base font-bold text-navy-900">{name}</p>
            <p className="flex items-center gap-1 text-2xs text-muted">
              <Icon name="star" size={12} />
              {rating}
            </p>
          </div>
        </div>

        <div className="flex flex-col items-end">
          <p className="text-2xs text-muted">픽업까지</p>
          <p className="text-base font-bold text-navy-900">
            {pickupEtaMinutes == null ? "픽업 시간 확인 중" : `약 ${pickupEtaMinutes}분`}
          </p>
        </div>
      </div>

      <OfferCountdownBar
        remainingSeconds={countdown.remainingSeconds}
        progressPercent={countdown.progressPercent}
      />

      <div className="flex gap-2">
        <Button variant="outline" block onClick={onReject}>
          거절
        </Button>
        <Button
          variant="navy"
          block
          onClick={onAccept}
          disabled={countdown.remainingSeconds <= 0}
        >
          수락하기
        </Button>
      </div>
    </div>
  );
}

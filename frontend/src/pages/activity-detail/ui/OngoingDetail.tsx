import { Button, Card, MapCard, RouteCard } from "@/shared/ui";

export interface OngoingDetailProps {
  onCancel: () => void;
}

/** 진행 중인 배달의 상세 내용(경로·실시간 지도·현재 상태·연락 수단). */
export function OngoingDetail({ onCancel }: OngoingDetailProps) {
  return (
    <>
      <RouteCard origin="A동 102호" destination="B동 405호" />

      <MapCard height={300} />

      <Card variant="hero" className="flex items-center justify-center gap-8">
        <div className="flex flex-col items-center">
          <span className="text-2xs opacity-70">예상 도착</span>
          <span className="text-md font-bold">3분</span>
        </div>
        <span className="h-8 w-px bg-white/20" />
        <div className="flex flex-col items-center">
          <span className="text-2xs opacity-70">남은 거리</span>
          <span className="text-md font-bold">450m</span>
        </div>
      </Card>

      <div className="flex items-center gap-3">
        <span className="size-9 rounded-pill bg-teal-50" />
        <div className="flex flex-col">
          <span className="text-base font-bold text-navy-900">
            물품을 픽업 중이에요
          </span>
          <span className="text-2xs text-muted">드리미 '핀'이 출발지 도착</span>
        </div>
      </div>

      <div className="flex gap-3">
        <Button variant="navy" block>
          전화하기
        </Button>
        <Button
          variant="outline"
          block
          className="border-transparent bg-teal-50"
        >
          채팅하기
        </Button>
      </div>

      <button
        type="button"
        onClick={onCancel}
        className="text-center text-2xs text-muted"
      >
        배달 취소하기 (픽업 전에만 가능)
      </button>
    </>
  );
}

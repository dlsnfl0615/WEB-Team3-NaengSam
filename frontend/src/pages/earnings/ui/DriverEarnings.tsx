import { BarChart, Button, Card, InfoRow } from "@/shared/ui";
import { MONTHLY_TREND } from "./trend";

/** 드리미 수익 리포트 본문(총 수익·월간 추이·퀵 대비 추가 수익). */
export function DriverEarnings() {
  return (
    <>
      <Card variant="hero" className="flex flex-col gap-1">
        <div className="flex items-center justify-between">
          <span className="text-2xs opacity-70">이번 달 총 수익</span>
          <span className="rounded-pill border border-white/30 px-2.5 py-0.5 text-2xs">
            7월
          </span>
        </div>
        <p className="text-3xl font-bold tracking-[-0.6px]">₩312,500</p>
        <p className="text-2xs text-teal-500">▲ 지난달 대비 +18%</p>
      </Card>

      <div className="flex flex-col gap-2">
        <p className="text-2xs text-muted">월간 추이</p>
        <Card>
          <BarChart data={MONTHLY_TREND} />
        </Card>
      </div>

      <Card variant="accent" className="flex flex-col gap-2.5 border-teal-500">
        <InfoRow label="시장 퀵 평균 단가">건당 ₩5,800</InfoRow>
        <div className="flex items-center justify-between border-t border-teal-500/30 pt-3">
          <span className="text-sm font-bold text-navy-900">
            퀵 대비 추가로 번 금액
          </span>
          <span className="text-lg font-bold text-teal-700">+₩58,500</span>
        </div>
        <p className="text-2xs text-muted">
          이번 달 52건 · 자투리 시간 활용 수익
        </p>
      </Card>

      <Button variant="navy" block>
        정산 계좌로 출금하기
      </Button>
    </>
  );
}

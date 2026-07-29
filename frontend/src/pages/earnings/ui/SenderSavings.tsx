import { BarChart, Button, Card, InfoRow } from "@/shared/ui";
import { MONTHLY_TREND } from "./trend";

/** 부르미 절감 리포트 본문(총 절감액·월간 추이·퀵 대비 절약 금액). */
export function SenderSavings() {
  return (
    <>
      <Card variant="hero" className="flex flex-col gap-1">
        <div className="flex items-center justify-between">
          <span className="text-2xs opacity-70">이번 달 총 절감액</span>
          <span className="rounded-pill border border-white/30 px-2.5 py-0.5 text-2xs">
            7월
          </span>
        </div>
        <p className="text-3xl font-bold tracking-[-0.6px]">₩45,000</p>
        <p className="text-2xs text-teal-500">▲ 지난달 대비 +12%</p>
      </Card>

      <div className="flex flex-col gap-2">
        <p className="text-2xs text-muted">월간 추이</p>
        <Card>
          <BarChart data={MONTHLY_TREND} />
        </Card>
      </div>

      <Card variant="accent" className="flex flex-col gap-2.5 border-teal-500">
        <InfoRow label="시장 퀵 평균 단가">건당 ₩5,800</InfoRow>
        <InfoRow label="쉼,부름 평균 단가">건당 ₩2,050</InfoRow>
        <div className="flex items-center justify-between border-t border-teal-500/30 pt-3">
          <span className="text-sm font-bold text-navy-900">
            퀵 대비 절약한 금액
          </span>
          <span className="text-lg font-bold text-teal-700">₩45,000</span>
        </div>
        <p className="text-2xs text-muted">이번 달 12건 이용 기준</p>
      </Card>

      <Button variant="outline" block className="border-transparent bg-track">
        절감 리포트 공유하기
      </Button>
    </>
  );
}

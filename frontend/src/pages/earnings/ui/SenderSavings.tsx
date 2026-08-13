import { useEffect, useState } from "react";
import { BarChart, Button, Card } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { api, isApiError } from "@/shared/api";
import type { BoormiDashboardDto } from "@/shared/api";
import { toMonthLabel } from "./monthLabel";

/** 부르미 절감 리포트 본문. /api/v1/boormi/dashboard로 이번 달 절감액·월간 추이를 조회한다. */
export function SenderSavings() {
  const [dashboard, setDashboard] = useState<BoormiDashboardDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .getBoormiDashboard()
      .then(({ result }) => {
        if (!cancelled) setDashboard(result ?? null);
      })
      .catch((e) => {
        if (!cancelled) {
          setError(
            isApiError(e) ? e.message : "절감 정보를 불러오지 못했어요.",
          );
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const trend = (dashboard?.recentSixMonths ?? []).map((m) => ({
    label: toMonthLabel(m.month),
    value: m.savedAmount ?? 0,
  }));
  const currentMonthLabel = trend.at(-1)?.label ?? "";
  const growthPercent = dashboard?.monthOverMonthGrowthPercent ?? 0;
  const isIncrease = growthPercent > 0;

  return (
    <>
      <Card variant="hero" className="flex flex-col gap-1">
        <div className="flex items-center justify-between">
          <span className="text-2xs opacity-70">이번 달 총 절감액</span>
          <span className="rounded-pill border border-white/30 px-2.5 py-0.5 text-2xs">
            {currentMonthLabel}
          </span>
        </div>
        <p className="text-3xl font-bold tracking-[-0.6px]">
          ₩{(dashboard?.thisMonthSavedAmount ?? 0).toLocaleString()}
        </p>
        <p
          className={cn(
            "text-2xs",
            isIncrease ? "text-status-danger" : "text-teal-500",
          )}
        >
          {isIncrease ? "▲" : "▼"} 지난달 대비 {isIncrease ? "+" : ""}
          {growthPercent}% · 이번 달 {dashboard?.thisMonthCount ?? 0}건
        </p>
      </Card>

      <div className="flex flex-col gap-2">
        <p className="text-2xs text-muted">월간 추이</p>
        <Card>
          <BarChart data={trend} />
        </Card>
      </div>

      {error && <p className="text-2xs text-status-danger">{error}</p>}

      <Button variant="outline" block className="border-transparent bg-track">
        절감 리포트 공유하기
      </Button>
    </>
  );
}

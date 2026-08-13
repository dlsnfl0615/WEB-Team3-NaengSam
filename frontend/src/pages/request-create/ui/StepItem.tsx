import { Card, Icon, IconChip, type IconName } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { ExpectedValueDto } from "@/shared/api";
import type { RequestForm, UpdateForm } from "./types";

const TYPES: { key: RequestForm["itemType"]; icon: IconName }[] = [
  { key: "서류", icon: "document" },
  { key: "소형택배", icon: "package" },
  { key: "샘플", icon: "star" },
  { key: "기타", icon: "more" },
];

const SIZES: { key: RequestForm["itemSize"]; label: string; weight: string }[] =
  [
    { key: "S", label: "소형 (S)", weight: "5kg 미만" },
    { key: "M", label: "중형 (M)", weight: "15kg 미만" },
  ];

export interface StepItemProps {
  form: RequestForm;
  update: UpdateForm;
  /** expectedValue API 견적(없으면 미표시). */
  estimate: ExpectedValueDto | null;
  /** 견적 조회 중. */
  estimating: boolean;
}

/** 스텝 2: 물품 — 유형/크기 선택 + 예상 요금(expectedValue). */
export function StepItem({
  form,
  update,
  estimate,
  estimating,
}: StepItemProps) {
  const fareLabel = estimating
    ? "계산 중…"
    : estimate?.expectedValue != null
      ? `${estimate.expectedValue.toLocaleString()} 원`
      : "주소 입력 후 확인";
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
        어떤 물품을 보내시나요?
      </h2>

      {/* 물품 유형 */}
      <div className="grid grid-cols-4 gap-2">
        {TYPES.map(({ key, icon }) => {
          const selected = form.itemType === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => update({ itemType: key })}
              className={cn(
                "flex h-[82px] flex-col items-center justify-center gap-2 rounded-sm border border-dashed",
                selected
                  ? "border-navy-900 bg-teal-50 text-navy-900"
                  : "border-line bg-canvas text-muted",
              )}
            >
              <Icon name={icon} size={20} />
              <span className="text-2xs">{key}</span>
            </button>
          );
        })}
      </div>

      {/* 크기와 무게 */}
      <div className="flex flex-col gap-2">
        <p className="text-sm text-muted">크기와 무게</p>
        {SIZES.map(({ key, label, weight }) => {
          const selected = form.itemSize === key;
          return (
            <Card
              key={key}
              role="button"
              tabIndex={0}
              className={cn(
                "flex cursor-pointer flex-col items-center gap-1.5 text-center",
                selected ? "border-navy-900" : "",
              )}
              onClick={() => update({ itemSize: key })}
            >
              <IconChip name="package" />
              <div>
                <p className="text-base font-bold text-navy-900">{label}</p>
                <p className="text-2xs text-muted">{weight}</p>
              </div>
              <span
                className={cn(
                  "size-7 rounded-pill border-2",
                  selected
                    ? "border-teal-500 bg-teal-50"
                    : "border-track bg-surface",
                )}
              />
            </Card>
          );
        })}
      </div>

      {/* 예상 배송 요금 */}
      <Card variant="hero" className="flex flex-col gap-2">
        <p className="text-center text-xs text-track">예상 배송 요금</p>
        <p className="text-center text-lg font-bold text-white">{fareLabel}</p>
        {estimate?.expectedDistance != null ||
        estimate?.expectedTime != null ? (
          <div className="flex justify-center gap-3 text-2xs text-track">
            {estimate?.expectedDistance != null && (
              <span>
                거리 {(estimate.expectedDistance / 1000).toFixed(1)}km
              </span>
            )}
            {estimate?.expectedTime != null && (
              <span>예상 {estimate.expectedTime}분</span>
            )}
          </div>
        ) : (
          <p className="text-center text-2xs text-track">
            출발·도착지 기준 예상 금액이에요
          </p>
        )}
      </Card>
    </div>
  );
}

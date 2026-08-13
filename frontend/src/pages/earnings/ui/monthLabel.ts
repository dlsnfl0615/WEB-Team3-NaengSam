/** "2026-07" 형태의 YearMonth 문자열 → "7월" 라벨. */
export function toMonthLabel(month?: string): string {
  if (!month) return "";
  const m = Number(month.split("-")[1]);
  return Number.isNaN(m) ? month : `${m}월`;
}

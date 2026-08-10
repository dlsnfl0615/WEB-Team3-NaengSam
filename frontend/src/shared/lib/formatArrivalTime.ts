/**
 * 백엔드 LocalDateTime(예: "2026-08-10T15:25:30", 타임존 없음 = 로컬 벽시계)을
 * "오전/오후 h시 m분" 표시 문자열로 바꾼다. 값이 없거나 파싱 실패면 null(호출부에서 대체 문구 처리).
 * 실시간 남은 시간은 계산하지 않고, 예상 완료 '시각'만 보여준다.
 */
export function formatArrivalTime(iso?: string | null): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;

  const hours = date.getHours();
  const minutes = date.getMinutes();
  const meridiem = hours < 12 ? "오전" : "오후";
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  return `${meridiem} ${hour12}시 ${minutes}분`;
}

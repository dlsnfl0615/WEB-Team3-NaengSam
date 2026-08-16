/**
 * "마지막으로 확인한 시각"을 지금 기준 경과 문구로 바꾼다(예: "방금 전", "3분 전", "1시간 12분 전").
 *
 * 인자는 서버가 준 경과 초를 클라이언트 시계로 역산한 로컬 타임스탬프(ms)다. 서버 시각을 그대로
 * 쓰지 않기 때문에 클라이언트 시계가 어긋나 있어도 문구가 틀리지 않는다.
 */
export function formatLastSeen(sinceMs: number, now: number = Date.now()): string {
  const totalMinutes = Math.floor(Math.max(0, now - sinceMs) / 60_000);
  if (totalMinutes < 1) return "방금 전";
  if (totalMinutes < 60) return `${totalMinutes}분 전`;

  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes === 0 ? `${hours}시간 전` : `${hours}시간 ${minutes}분 전`;
}

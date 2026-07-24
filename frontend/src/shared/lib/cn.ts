/** 조건부 클래스명을 합치는 작은 헬퍼(falsy 값 제거). */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

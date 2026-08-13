/**
 * 전화번호를 화면 표시용으로 하이픈을 넣어 돌려준다(`01012345678` → `010-1234-5678`).
 *
 * 백엔드는 숫자만 남겨 저장하므로(PhoneNumbers.normalize) 하이픈은 프론트가 넣는다.
 * 자릿수가 예상과 다르면 손대지 않고 원본을 그대로 돌려준다. `tel:` href에는 포맷 대신 숫자열을 쓴다.
 */
export function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  if (digits.length === 11) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  }
  if (digits.length === 10) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  }
  return phone;
}

/**
 * 폼 필드 형식 검증 유틸(login/signup 공용).
 * 각 함수는 유효하면 true를 반환한다. 빈 값 여부는 호출부에서 별도로 판단한다.
 */

/** 이메일 형식(로컬@도메인.tld). */
export const isEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim());

/** 비밀번호: 5~20자, 영문+숫자 조합(백엔드 @Size(min=5, max=20)에 맞춤). */
export const isPassword = (v: string) =>
  /^(?=.*[A-Za-z])(?=.*\d).{5,20}$/.test(v);

/** 휴대폰 번호: 01x-0000-0000(하이픈 선택). */
export const isPhone = (v: string) => /^01\d-?\d{3,4}-?\d{4}$/.test(v.trim());

/** 생년월일: YYYY.M.D ~ YYYY.MM.DD. */
export const isBirth = (v: string) => /^\d{4}\.\d{1,2}\.\d{1,2}$/.test(v.trim());

/** 인증번호: 숫자 6자리. */
export const isCode = (v: string) => /^\d{6}$/.test(v.trim());

/** 필드별 형식 안내 메시지. */
export const VALIDATION_MESSAGE = {
  email: "올바른 이메일 형식이 아니에요",
  password: "5~20자 영문·숫자 조합이어야 해요",
  phone: "010-0000-0000 형식으로 입력해주세요",
  birth: "YYYY.MM.DD 형식으로 입력해주세요",
  code: "인증번호 숫자 6자리를 입력해주세요",
} as const;

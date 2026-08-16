import { mockRequest } from "./client";
import { SEED_USER } from "./seed";
import type { AuthUser, LoginRequest, SignupRequest } from "./types";

/**
 * 인증 목 서비스(#37 대응). 실제 API 연동 시 이 파일 구현만 교체한다.
 */

/** 로그인. 목: 자격 검증 없이 시드 유저로 성공 처리(이메일만 반영). */
export function login(dto: LoginRequest): Promise<AuthUser> {
  return mockRequest<AuthUser>({ ...SEED_USER, email: dto.email });
}

/** 회원가입. 목: 입력 정보로 신규 유저 생성(부르미로 시작). */
export function signup(dto: SignupRequest): Promise<AuthUser> {
  return mockRequest<AuthUser>({
    id: "u-new",
    name: dto.name || "새 사용자",
    roles: ["부르미"],
    boormiRating: 0,
    email: dto.email,
  });
}

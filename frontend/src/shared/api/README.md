# `@/shared/api` — API 연동 공통 구조

모든 화면·스토어가 **동일한 방식**으로 백엔드에 붙도록 만든 공통 계층입니다.
백엔드는 모든 응답을 같은 envelope로 내려주고, 오류는 **실제 HTTP 상태코드**로 옵니다.
인증은 **세션 쿠키(JSESSIONID)** 기반이라 모든 요청에 `withCredentials`가 적용됩니다.

```
src/shared/api/
├── generated/        # orval 자동생성 (직접 수정 금지)
│   ├── endpoints.ts  #   모든 API 함수(팩토리)
│   └── model/        #   요청/응답 타입(UserDto, SignUpRequest, ...)
├── http/
│   ├── axiosInstance.ts   # 공유 axios 인스턴스 + 에러 정규화 인터셉터
│   ├── customInstance.ts  # orval mutator (모든 요청이 여기로)
│   ├── ApiError.ts        # ApiError, isApiError, 폴백 메시지
│   └── authEvents.ts      # 401(세션 만료) 후처리 콜백 등록
└── index.ts          # 공개 진입점 (여기서만 import)
```

## 공통 응답 형식

```jsonc
// 성공
{ "isSuccess": true,  "code": "COM200",  "message": "요청에 성공했습니다.", "result": { /* 데이터 */ } }
// 실패 (실제 HTTP 상태코드 401/403/404/409/429/500 …과 함께)
{ "isSuccess": false, "code": "AUTH_006", "message": "아이디 또는 비밀번호가 올바르지 않습니다.", "result": null }
```

envelope는 **언랩하지 않습니다.** 실제 데이터는 `result`에 있습니다.

## 사용법

```ts
import { api, isApiError } from '@/shared/api'

try {
  await api.login({ email, password })   // 세션 쿠키 발급
  const { result } = await api.me()      // result: UserDto
  console.log(result?.name)
} catch (e) {
  if (isApiError(e)) {
    showToast(e.message)   // 백엔드가 준 한글 메시지를 그대로 노출
    if (e.code === 'AUTH_007') { /* 이미 가입된 계정 등 코드별 분기 */ }
  }
}
```

- **UI 메시지**는 `error.message`를 그대로 씁니다(백엔드가 한글로 내려줌).
- **분기 로직**은 `error.code`(예: `AUTH_006`, `USER_005`) 또는 `error.status`로 합니다.
- 세션 만료(`AUTH_001/002/003`)는 인터셉터가 감지해 자동으로 로그인 화면으로 보냅니다
  (`main.tsx`의 `setUnauthorizedHandler`). 각 화면에서 따로 처리할 필요 없습니다.

## 클라이언트 재생성 (백엔드 API가 바뀌면)

```bash
# 1) 백엔드를 localhost:8080에 띄운다
# 2) 프론트에서
pnpm api:gen
```

- 스펙 출처: `http://localhost:8080/v3/api-docs` (`orval.config.ts`).
- `generated/**`는 **직접 수정하지 않습니다**. 항상 재생성하세요.

## 새 도메인/엔드포인트 붙이기

1. 백엔드에 엔드포인트 추가 → `pnpm api:gen` 으로 함수·타입 생성.
2. 스토어/컴포넌트에서 `api.<함수명>(...)` 호출. mutator·세션 쿠키·에러 처리는 자동 적용됩니다.
3. 응답은 `const { result } = await api.xxx()` 로 꺼내 씁니다.

> 참고: 지금은 백엔드 `@Tag`가 한글이라 `orval.config.ts`가 `mode: 'split'`(단일 파일)을 씁니다.
> 백엔드가 영문 태그명을 붙이면 `tags-split`으로 바꿔 도메인별 파일로 분리할 수 있습니다.

## 배포 · 백엔드 URL 주입

- `baseURL`은 `import.meta.env.VITE_API_BASE_URL ?? ''`이며 **빌드 타임에 인라인**됩니다(S3 정적 배포는 런타임 변경 불가 — 바꾸려면 재빌드).
- 생성 요청 URL에 이미 `/api/v1`이 포함되므로 `VITE_API_BASE_URL`에는 **오리진만** 넣습니다.
- **개발** = Vite 프록시(`/api → localhost:8080`, `DEV_API_TARGET`로 변경), **운영** = CloudFront `/api/*` → EC2. 둘 다 상대경로라 `VITE_API_BASE_URL` **빈값 유지**(같은 출처 → 세션 쿠키 동작, CORS 불필요).
- **별도 도메인(cross-origin)** 일 때만 `frontend-deploy.yml` Build 스텝 `env`에 `VITE_API_BASE_URL` secret 주입 + 백엔드 CORS(allowCredentials) + 쿠키 `SameSite=None; Secure` 필요.

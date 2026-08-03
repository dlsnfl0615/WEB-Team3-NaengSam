# CLAUDE.md — 프론트엔드 작업 지침

이 파일은 Claude Code가 `frontend/`에서 작업할 때 따르는 규칙입니다.

## 스택

- **React 19** + **Vite** + **TypeScript**
- **Tailwind CSS v4** (`@tailwindcss/vite`, CSS-first `@theme` 방식)
- **패키지 매니저: pnpm** — 반드시 `pnpm`을 사용하세요. `npm`은 lockfile(`pnpm-lock.yaml`)과 `workspace:` 프로토콜 때문에 실패합니다.
- 폰트: `@fontsource-variable/inter`

```bash
pnpm install --ignore-scripts   # pnpm 11의 빌드 스크립트 승인 오류 회피(esbuild 등은 prebuilt라 불필요)
pnpm dev        # 개발 서버 (진입: / → src/pages/onboarding)
pnpm build      # tsc -b && vite build
pnpm lint       # eslint
```

## 디자인 시스템 규칙 (필수)

디자인 시스템의 전체 레퍼런스는 **[design.md](./design.md)** 이며 단일 진실 소스입니다. 작업 전 참고하세요.

1. **토큰만 사용한다.** 색·반경·그림자는 `src/app/styles/theme.css`의 `@theme` 토큰에서 나온 유틸만 사용합니다.
   - ✅ `bg-navy-900` `text-teal-700` `rounded-md` `shadow-card`
   - ❌ `bg-[#0d1b3d]` `rounded-[20px]` 같은 하드코딩 값
   - 새 색/값이 필요하면 **먼저 `theme.css`에 토큰을 추가**한 뒤 사용합니다.
2. **기존 컴포넌트를 재사용한다.** 화면을 만들 때 `src/shared/ui/`의 컴포넌트 조합을 우선하고, 없을 때만 새로 만듭니다. export는 `src/shared/ui/index.ts` 배럴을 통합니다.
3. **아이콘은 `<Icon name>`으로.** `<img>`나 인라인 `<svg>` 금지. 색은 `text-*`(currentColor)로 제어합니다.
4. **상태 색상**은 `Badge`의 `tone`과 `toneForStatus()` 헬퍼를 사용합니다(배송중→info, 지연→warning, 사고/거절→danger).
5. **모바일 우선** — 기준 폭 390px.

## 파일 구조 · 컨벤션 (Feature-Sliced Design)

FSD 실용형 3계층입니다. **import는 아래 계층으로만**: `app → pages → shared`.

```
src/
├── main.tsx                # 조립 루트(전역 CSS + App 마운트)
├── app/                    # 앱 초기화 계층
│   ├── App.tsx             # 라우터 프로바이더 — 불변
│   ├── routes.ts           # 라우트 자동 집계 + 비공개 라우트 RequireAuth 래핑(페이지 작성자는 미수정)
│   └── styles/*.css        # 전역 스타일 (theme.css = 토큰 SSOT)
├── pages/                  # 페이지 슬라이스 = 폴더 1개 = 담당자 1명
│   └── <name>/
│       ├── ui/<Name>Screen.tsx   # 페이지 컴포넌트 (ScreenShell로 감쌈)
│       ├── route.tsx             # export const route: RouteObject = { path, element }
│       └── index.ts              # 공개 API (export { <Name>Screen })
└── shared/                 # 하위 계층: 재사용 자산(비즈니스 로직 없음)
    ├── ui/                 # 디자인 시스템 컴포넌트 + index.ts 배럴
    │   ├── <Name>/<Name>.tsx     # 컴포넌트 1개 = 폴더 1개
    │   ├── ScreenShell/          # 화면 공통 모바일 셸
    │   └── Icon/icons.ts         # 아이콘 이름→SVG 맵
    ├── lib/cn.ts           # 클래스명 병합 헬퍼
    ├── lib/validation.ts   # 폼 형식·길이 검증(백엔드 제약과 정합)
    ├── lib/auth/RequireAuth.tsx  # 로그인 라우트 가드
    ├── store/*.ts          # zustand 전역 스토어(sessionStore 등)
    ├── api/                # API 연동 계층 (아래 'API 연동' 절 참고)
    │   ├── index.ts        #   진입점: api, isApiError, 생성 타입
    │   ├── http/           #   axios 인스턴스·mutator·ApiError·authEvents
    │   └── generated/      #   orval 자동생성(수정 금지)
    ├── config/routes.ts    # ROUTES + PUBLIC_ROUTES(공개 페이지) — 새 경로만 한 줄 추가
    └── assets/icons/*.svg  # 아이콘 원본 20종
```

> 비즈니스 로직은 이미 `shared/api`(API 연동)·`shared/store`(전역 상태)에 있습니다. entities·features·widgets 계층은 더 복잡한 도메인 로직이 생기면 그때 `shared`와 `pages` 사이에 추가합니다.

- **경로 alias**: `@/` → `src/`. import는 `@/shared/ui`, `@/shared/lib/cn`, `@/shared/config/routes`로 통일합니다.
- **페이지 병렬 작업**: 각자 `src/pages/<name>/` 슬라이스 하나만 소유합니다. 라우트는 각 슬라이스의 `route.tsx`를 `app/routes.ts`가 glob으로 자동 집계하므로 **새 페이지를 추가해도 `App.tsx`/`routes.ts`는 건드리지 않습니다**. 새 경로는 `shared/config/routes.ts`에 한 줄만 추가합니다.
- **공통 셸**: 모든 페이지는 최상위를 `<ScreenShell>`로 감쌉니다(직접 `max-w-[420px]` 마크업 금지).

- 컴포넌트 파일명·함수명: **PascalCase**. props 인터페이스는 `<Name>Props`.
- **한 파일은 컴포넌트만 export** (`react-refresh/only-export-components` 규칙). 상수·헬퍼는 별도 파일로 분리합니다(예: `Icon/icons.ts`, `Badge/toneForStatus.ts`).
- 조건부 클래스는 `cn()` 헬퍼를 사용합니다.

## 새 컴포넌트 추가 절차 (shared/ui)

1. `src/shared/ui/<Name>/<Name>.tsx` 생성. 토큰 유틸만 사용, `<Name>Props` 인터페이스 export.
2. `src/shared/ui/index.ts`에 export 추가.
3. `design.md` 컴포넌트 카탈로그 표에 한 줄 추가.
4. `pnpm lint && pnpm build`로 확인.

## 새 페이지 추가 절차 (pages)

1. `src/shared/config/routes.ts`의 `ROUTES`에 경로 한 줄 추가.
2. `src/pages/<name>/ui/<Name>Screen.tsx` 생성 — `<ScreenShell>`로 감싸고 토큰·`@/shared/ui` 컴포넌트만 사용.
3. `src/pages/<name>/route.tsx` 생성 — `export const route: RouteObject = { path: ROUTES.<name>, element: <…Screen /> }`. (`App.tsx`/`routes.ts`는 수정 불필요, 자동 등록됩니다.)
4. `src/pages/<name>/index.ts`에 페이지 공개 API(`export { <Name>Screen }`) 추가.
5. 페이지 전용 하위 컴포넌트는 `src/pages/<name>/ui/`에 둡니다(공용이 되면 `src/shared/ui/`로 승격).
6. `pnpm lint && pnpm build`로 확인.

> **인증 보호**: 새 페이지는 **기본적으로 로그인 세션이 필요**합니다(`app/routes.ts`가 `RequireAuth`로 자동 래핑). 로그인 없이 접근 가능한 공개 페이지라면 `src/shared/config/routes.ts`의 `PUBLIC_ROUTES`에 경로를 추가하세요.

## API 연동 (필수)

백엔드(Spring Boot, `http://localhost:8080`)와의 통신은 **orval로 생성한 클라이언트 + 공통 처리 계층**(`src/shared/api/`)으로 통일합니다. 상세 레퍼런스는 **[src/shared/api/README.md](./src/shared/api/README.md)** 입니다.

### 계약 (공통 규칙)

- **응답 envelope**: 모든 응답이 `{ isSuccess, code, message, result }` 형태이며 **실제 데이터는 `result`** 에 있습니다.
  - 성공: `{ "isSuccess": true, "code": "COM200", "message": "...", "result": { ... } }`
  - 실패: `{ "isSuccess": false, "code": "AUTH_006", "message": "...", "result": null }`
- **오류는 실제 HTTP 상태코드**(401/403/404/409/429/500…)로 옵니다 → axios가 자동으로 reject.
- **인증 = 세션 쿠키(JSESSIONID)**. 모든 요청 `withCredentials: true`. 개발은 **Vite 프록시**(`/api → localhost:8080`)로 동일 출처 처리(백엔드 CORS 설정 불필요).
- **baseURL 주의**: 생성된 요청 URL에 이미 `/api/v1`이 포함됩니다 → axios `baseURL`은 **오리진만**(개발은 빈값). 운영 오리진은 `.env`의 `VITE_API_BASE_URL`로 주입.

### 클라이언트 생성 (orval)

```bash
# 백엔드를 localhost:8080에 먼저 띄운다
pnpm api:gen        # http://localhost:8080/v3/api-docs → src/shared/api/generated/**
```

- 생성물(`generated/**`)은 **직접 수정 금지**. 백엔드 스펙이 바뀌면 재생성합니다.
- 설정은 `orval.config.ts`(client: axios, mutator: `http/customInstance.ts`, mode: split).

### 사용법

```ts
import { api, isApiError } from '@/shared/api'

try {
  await api.login({ email, password })   // 세션 쿠키 발급(응답 result 없음)
  const { result } = await api.me()      // result: UserDto — 항상 envelope의 result로 접근
} catch (e) {
  if (isApiError(e)) {
    showError(e.message)                 // 백엔드가 준 한글 메시지를 그대로 노출
    if (e.code === 'AUTH_007') { /* 코드별 분기 */ }
  }
}
```

- **UI 메시지 = `error.message`**, **분기 = `error.code` / `error.status`**.
- envelope는 언랩하지 않습니다 → 항상 `.result`로 데이터 접근.
- 세션 만료(`AUTH_001`~`AUTH_003`)는 인터셉터가 자동으로 로그인 화면 이동을 처리 → 화면에서 따로 안 해도 됩니다.

### 상태 · 인증

- 서버 상태는 **`src/shared/store/`의 zustand 스토어**에 담고 화면이 구독합니다(예: `sessionStore`). 백엔드 DTO는 화면용 타입으로 **어댑터 함수**(예: `toAuthUser`)로 변환합니다.
- **라우트 보호는 자동**: `app/routes.ts`가 `PUBLIC_ROUTES`(`shared/config/routes.ts`)에 없는 라우트를 `RequireAuth`로 감쌉니다. 새 페이지는 기본 보호, 공개면 `PUBLIC_ROUTES`에 추가.
- 앱 시작 시 `main.tsx`가 `sessionStore.bootstrap()`으로 `/me`를 호출해 쿠키 세션을 복원합니다(그 401은 `SESSION_PROBE_HEADER`로 리다이렉트 예외 처리).

### 폼 검증 (백엔드 제약 정합)

- 입력폼은 백엔드 DTO의 `@Size`/`@Pattern`에 맞춰 `<input maxLength>`로 상한을 겁니다.
- 형식·길이 검증 헬퍼는 `src/shared/lib/validation.ts`에 모으고 백엔드 제약과 일치시킵니다(예: 비밀번호 5~20자).

### 새 도메인 API 연동 절차

1. 백엔드에 엔드포인트가 있으면 `pnpm api:gen`으로 함수·타입 생성(`api.<operationId>`).
2. 필요하면 `src/shared/store/<domain>Store.ts`에 zustand 스토어 + DTO→화면 타입 어댑터 작성.
3. 화면(`pages/<name>`)에서 `import { api, isApiError } from '@/shared/api'` → `const { result } = await api.xxx()`. 로딩/에러는 로컬 state로, 에러 메시지는 `e.message`.
4. 인증이 필요한 새 페이지는 자동 보호됨(공개면 `PUBLIC_ROUTES`에 추가).
5. 입력폼은 백엔드 제약에 맞춰 `maxLength`·`validation.ts` 정합.
6. `pnpm lint && pnpm build`로 확인.

### 배포 · 백엔드 URL 주입

**운영 배포 방식 = 교차 출처(cross-origin) 직접 호출.** 프론트(S3+CloudFront, https)에서 백엔드 API(EC2, https)를 직접 호출합니다(CloudFront `/api` 프록시 미사용).

- **Vite env는 빌드 타임에 인라인**됩니다. `import.meta.env.VITE_API_BASE_URL`은 `pnpm build` 순간 번들 JS에 문자열로 박히므로, **S3 정적 배포에서는 런타임에 바꿀 수 없습니다**(변경하려면 재빌드).
- **개발** = Vite 프록시(`/api → localhost:8080`, `DEV_API_TARGET`로 변경) → `VITE_API_BASE_URL` **빈값**(동일 출처).
- **운영** = `frontend-deploy.yml` Build 스텝 `env`의 `VITE_API_BASE_URL`(secret)에 **백엔드 오리진**(예: `https://api.example.com`)을 주입. 생성 요청 경로에 이미 `/api/v1`이 포함되므로 **오리진만** 넣습니다.
- 교차 출처 + 세션 쿠키라 **백엔드 설정이 필수**입니다: **CORS(allowCredentials + 정확한 origin)** 와 세션쿠키 **`SameSite=None; Secure`**(백엔드 HTTPS 필요). → 백엔드 이슈 **#141** 참조. (프론트는 이미 `withCredentials: true`.)
- (대안) CloudFront에 `/api/*` behavior(오리진=EC2)를 두면 동일 출처가 되어 CORS 없이 동작하지만, 이번 배포는 교차 출처 방식으로 결정.

## 새 아이콘 추가 절차

1. Figma에서 SVG를 내려받아 `src/shared/assets/icons/<name>.svg`에 저장(단색 라인, 7.5% stroke 비율 권장).
2. `src/shared/ui/Icon/icons.ts`의 `ICONS` 맵에 등록 → `IconName`에 자동 반영.

## 검증

작업을 마치면 항상 다음을 통과시키세요:

```bash
pnpm lint && pnpm build
```

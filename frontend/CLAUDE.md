# CLAUDE.md — 프론트엔드 작업 지침

이 파일은 Claude Code가 `frontend/`에서 작업할 때 따르는 규칙입니다.

## 스택

- **React 19** + **Vite** + **TypeScript**
- **Tailwind CSS v4** (`@tailwindcss/vite`, CSS-first `@theme` 방식)
- **패키지 매니저: pnpm** — 반드시 `pnpm`을 사용하세요. `npm`은 lockfile(`pnpm-lock.yaml`)과 `workspace:` 프로토콜 때문에 실패합니다.
- 폰트: `@fontsource-variable/inter`

```bash
pnpm install
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
│   ├── routes.ts           # import.meta.glob 라우트 자동 집계 — 불변
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
    ├── config/routes.ts    # ROUTES 경로 상수(SSOT) — 새 경로만 한 줄 추가
    └── assets/icons/*.svg  # 아이콘 원본 20종
```

> entities·features·widgets 계층은 비즈니스 로직(로그인 API, user 엔티티 등)이 생기면 그때 `shared`와 `pages` 사이에 추가합니다.

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

## 새 아이콘 추가 절차

1. Figma에서 SVG를 내려받아 `src/shared/assets/icons/<name>.svg`에 저장(단색 라인, 7.5% stroke 비율 권장).
2. `src/shared/ui/Icon/icons.ts`의 `ICONS` 맵에 등록 → `IconName`에 자동 반영.

## 검증

작업을 마치면 항상 다음을 통과시키세요:

```bash
pnpm lint && pnpm build
```

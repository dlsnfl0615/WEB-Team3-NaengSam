# 쉼,부름 · 부르미 디자인 시스템

배달 서비스 **쉼,부름**(역할: 부르미 caller / 드리미 driver)의 프론트엔드 디자인 시스템 레퍼런스입니다.
[Figma 원본](https://www.figma.com/design/iCLxsoJIUFpXVRYkDXQqon/?node-id=1-1581)의 토큰·컴포넌트·아이콘을 코드로 이관했습니다.

- **스택**: React 19 + Vite + TypeScript + Tailwind CSS v4
- **구조**: Feature-Sliced Design 실용형 3계층(`app` / `pages` / `shared`). import는 하위 계층으로만.
- **토큰 정의**: `src/app/styles/theme.css` (`@theme` 블록) — 단일 진실 소스(SSOT)
- **컴포넌트**: `src/shared/ui/*` (배럴: `src/shared/ui/index.ts`)
- **아이콘**: `src/shared/assets/icons/*.svg` + `<Icon>` 컴포넌트
- **페이지**: `src/pages/<name>/` — 인증 플로우(`onboarding` `/` → `login` `/login`, `signup` `/signup` → `verify` `/verify`)와 `home` `/home`. 각 슬라이스의 `route.tsx`를 `src/app/routes.ts`가 glob으로 자동 집계, `pnpm dev`로 확인

> ⚠️ 원본 Figma 노드는 정식 컴포넌트/변수가 없는 **화면 플로우 보드**였습니다. 토큰은 홈 화면(`1:1914`) 등 실제 화면에서 추출한 값입니다.

---

## 1. 색상 토큰

| 토큰     | Tailwind 유틸                 | HEX       | 용도                                |
| -------- | ----------------------------- | --------- | ----------------------------------- |
| navy-900 | `bg-navy-900` `text-navy-900` | `#0d1b3d` | 브랜드 메인, 제목, 히어로 카드 배경 |
| navy-700 | `bg-navy-700`                 | `#39466a` | 히어로 카드 보조 바                 |
| teal-700 | `text-teal-700`               | `#016a61` | 강조 텍스트, 진행바 채움, 활성 탭   |
| teal-500 | `bg-teal-500`                 | `#00b7a7` | 주요 CTA 버튼                       |
| teal-50  | `bg-teal-50`                  | `#e8fbf8` | 뱃지·아이콘칩 연한 배경             |
| ink      | `bg-ink`                      | `#0b0b0f` | 상태바 노치, 폰 프레임              |
| surface  | `bg-surface`                  | `#ffffff` | 카드 표면                           |
| canvas   | `bg-canvas`                   | `#f7f8fa` | 앱 배경                             |
| line     | `border-line`                 | `#eae7eb` | 카드 테두리                         |
| track    | `bg-track`                    | `#e3e6ec` | 진행바 트랙, 구분선                 |
| muted    | `text-muted`                  | `#6c7585` | 보조 텍스트                         |

**상태 색상** (뱃지)

| tone    | 토큰                                                                   | 대상 상태                    |
| ------- | ---------------------------------------------------------------------- | ---------------------------- |
| info    | `text-teal-700` / `bg-teal-50`                                         | 배송중, 픽업중, 매칭중, 완료 |
| warning | `text-status-warning` (`#b26a00`) / `bg-status-warning-50` (`#fff4e5`) | 지연, 경고 알림 카드         |
| danger  | `text-status-danger` (`#c0392b`)                                       | 사고, 매칭 거절              |
| success | `border-status-success` (`#24c36b`)                                    | 새 콜 카드 강조 테두리       |

---

## 2. 타이포그래피

**폰트**: Inter (Variable, `@fontsource-variable/inter`). 한글 폴백: Apple SD Gothic Neo / Malgun Gothic.

| 토큰        | 크기 | 용도                              |
| ----------- | ---- | --------------------------------- |
| `text-xl`   | 19px | 화면 제목, 히어로 타이틀, 큰 수치 |
| `text-lg`   | 16px | 섹션 제목                         |
| `text-md`   | 15px | 본문, 버튼 라벨                   |
| `text-base` | 13px | 카드 제목                         |
| `text-sm`   | 12px | 링크, 보조 라벨                   |
| `text-xs`   | 11px | 부제, 캡션                        |
| `text-2xs`  | 10px | 뱃지, 최소 캡션                   |

- Bold 제목에는 `tracking-[-0.4px]`(letter-spacing)를 적용합니다.
- 가중치: Regular(400) / Semibold(600) / Bold(700).

---

## 3. 반경 · 그림자 · 간격

| 종류   | 토큰              | 값                               | 용도                |
| ------ | ----------------- | -------------------------------- | ------------------- |
| 반경   | `rounded-sm`      | 9px                              | 아이콘칩, 작은 태그 |
| 반경   | `rounded-md`      | 20px                             | 카드                |
| 반경   | `rounded-pill`    | 999px                            | 뱃지, 토글, 버튼    |
| 반경   | `rounded-phone`   | 56px                             | 폰 목업 프레임      |
| 그림자 | `shadow-card`     | `0 2px 5px rgba(13,27,61,.05)`   | 카드 기본           |
| 그림자 | `shadow-elevated` | `0 16px 44px rgba(13,27,61,.18)` | 토스트, 폰 프레임   |

간격은 Tailwind 기본 스페이싱 스케일(4px 단위: `gap-3`=12px, `p-4`=16px)을 사용합니다. 카드 안쪽 여백은 보통 `p-4`(16px), 카드 사이 간격은 `gap-3`(12px)입니다.

---

## 4. 아이콘 (20종)

이름 → SVG 매핑은 `src/shared/ui/Icon/icons.ts`에 있습니다. 모든 아이콘은 **단색 라인 아이콘**이며, `<Icon>`이 SVG를 인라인으로 렌더하고 stroke/fill 을 `currentColor`로 재색(`.ds-icon` CSS)하므로 **`color`(text-\*)로 색을 제어**합니다.

```tsx
import { Icon } from './components'

<Icon name="home" />                              {/* 기본: 부모 color 상속 */}
<Icon name="bell" size={24} className="text-teal-700" />  {/* 티일색, 24px */}
```

| 그룹       | 아이콘                                                    |
| ---------- | --------------------------------------------------------- |
| 네비게이션 | `home` `activity` `point` `profile` `back` `more` `close` |
| 알림/상태  | `bell` `check` `star` `time`                              |
| 배송/물류  | `package` `document` `pin` `transfer` `drink`             |
| 결제       | `card` `bank`                                             |
| 기타       | `search` `camera`                                         |

> 새 아이콘 추가: Figma에서 단색 라인 SVG를 내려받아 `src/shared/assets/icons/`에 저장 → `icons.ts`에서 `?raw`로 import 후 `ICONS` 맵에 등록. stroke/fill 은 `.ds-icon` CSS가 currentColor 로 재색하므로 색상은 지정하지 않아도 됩니다.

---

## 5. 컴포넌트 카탈로그

모든 컴포넌트는 `src/shared/ui/index.ts`에서 export됩니다.

### 프리미티브

| 컴포넌트                    | 주요 props                       | 설명                                                   |
| --------------------------- | -------------------------------- | ------------------------------------------------------ |
| `Button`                    | `variant` `size` `block` `arrow` | `primary`(teal) / `navy` / `outline`. `arrow`로 → 표시 |
| `Badge`                     | `tone`                           | `info` / `warning` / `danger` / `neutral` 상태 뱃지    |
| `Card`                      | `variant`                        | `surface`(흰색) / `hero`(네이비) / `accent`(연한 티일) |
| `IconChip`                  | `name` `tone` `size`             | 라운드 사각 아이콘 컨테이너                            |
| `ProgressBar`               | `value`(0~100)                   | 배달 진행률 바                                         |
| `SegmentedToggle`           | `options` `value` `onChange`     | 2-세그먼트 토글(부르미/드리미)                         |
| `RadioOption`               | `label` `selected` `onSelect`    | 상호 배제 단일 선택 카드(사유 선택)                    |
| `TextField` / `SearchField` | `label` `leadingIcon` `disabled` … | 입력 필드. Search는 search 아이콘 포함. `disabled`면 배경이 `track`으로 바뀌고 글자가 `muted` |

### 복합 · 레이아웃

| 컴포넌트            | 주요 props                                 | 설명                                                                      |
| ------------------- | ------------------------------------------ | ------------------------------------------------------------------------- |
| `DeliveryCard`      | `icon` `title` `route` `status` `progress` | 진행 중 배송 리스트 아이템                                                |
| `StatCard`          | `label` `value` `variant`                  | 통계 카드(총 이용, 절감 금액)                                             |
| `SectionHeader`     | `title` `count` `action`                   | 섹션 제목 + 카운트 + 링크                                                 |
| `LocationBar`       | `location` `status`                        | 상단 위치 바                                                              |
| `TopBar`            | `title` `onBack` `actions`                 | 화면 헤더                                                                 |
| `BottomNav`         | `items`                                    | 하단 탭 바(현재 경로가 활성=teal, 누르면 이동)                            |
| `Toast`             | `icon` `title` `description` `action`      | 알림 토스트(네이비)                                                       |
| `ToastViewport`     | —                                          | 전역 토스트를 화면 상단에 최대 3개까지 쌓아 표시                          |
| `RouteCard`         | `origin` `destination`                     | 출발지 → 도착지 경로 카드                                                 |
| `InfoRow`           | `label` `children`                         | 상세 정보 카드의 라벨-값 한 줄                                            |
| `StarRating`        | `value` `onChange`                         | 별 5개 평점 입력(radiogroup)                                              |
| `MapCard`           | `overlay` `height` `flat` `children`       | 지도 화면 래퍼(`flat`=풀블리드용 반경·테두리 제거)                        |
| `ArrivalBadge`      | `arrivalTime`                              | 지도 위 배송 완료 예상 시각 배지(네이비 불투명)                           |
| `DeliveryRouteMap`  | `pickup` `dropoff` `driver` `driverLabel` `height` `flat` | 출발지·도착지·드리미 3핀(라벨 포함) 좌표 기반 추적 지도(`driverLabel`로 드리미 핀 라벨 변경, 지오코딩 없음, 드리미 핀만 이동, 최초 1회 뷰포트 fit, 키/좌표 없으면 텍스트 폴백) |
| `BarChart`          | `data` `highlightLast`                     | 절감 리포트용 막대 그래프                                                 |
| `BottomSheet`       | `open` `label` `onClose` `children`        | 하단에서 올라오는 모달 시트(배경 어둡게·흐리게, 배경 클릭 시 닫힘)        |
| `Modal`             | `open` `label` `onClose` `children`        | 가운데에 뜨는 모달(배경 회색조 처리, `onClose` 없으면 배경 클릭 무시)     |
| `PhotoLightboxModal` | `open` `label` `photoUrl` `emptyMessage` `onClose` | `Modal` 위에 사진(또는 없음 안내)과 X 닫기 버튼을 얹은 사진 라이트박스 |
| `DestinationPicker` | `onSubmit`                                 | 도착지 선택 본문(검색·빠른 선택 칩·최근/추천 목록·확인 버튼)              |
| `PlaceItem`         | `name` `detail` `icon` `selected` `onSelect` | 장소/저장 주소 목록 아이템(단일 선택 카드)                              |
| `ScreenShell`       | `children` `className`                     | 화면 공통 모바일 셸(가운데 정렬 `max-w-[420px]`). 모든 화면의 최상위 래퍼 |

### 사용 예 (홈 화면)

`src/screens/home/HomeScreen.tsx`가 위 프리미티브만으로 Figma 홈 화면(`1:1914`)을 재구성한 실제 화면입니다. 폰 목업 없이 `ScreenShell`(모바일 폭 `max-w-[420px]`, 가운데 정렬)로 렌더합니다.

```tsx
<Card variant="hero" className="flex flex-col gap-3">
  <p className="text-xl font-bold tracking-[-0.4px]">물품 보내기</p>
  <div className="h-[9px] w-3/4 rounded-[5px] bg-navy-700" />
  <Button variant="primary" arrow>물품 보내기</Button>
</Card>

<DeliveryCard icon="document" title="서류 배송#123"
  route="Zone A → Zone C" status="배송중" progress={55} />
```

---

## 6. 원칙

1. **토큰만 사용** — `bg-navy-900`, `text-teal-700`처럼 토큰 유틸을 쓰고 하드코딩 hex(`bg-[#0d1b3d]`)는 피합니다. 새 값이 필요하면 `theme.css`에 토큰을 먼저 추가하세요.
2. **컴포넌트 우선** — 화면을 만들 때 새 마크업보다 기존 컴포넌트 조합을 우선합니다.
3. **아이콘은 `<Icon>`으로** — `<img>`나 인라인 `<svg>` 대신 `<Icon name>`을 사용해 색 제어와 일관성을 확보합니다.
4. **모바일 우선** — 기준 화면은 390px 폭입니다.

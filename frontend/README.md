# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is enabled on this template. See [this documentation](https://react.dev/learn/react-compiler) for more information.

Note: This will impact Vite dev & build performances.

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```


## 페이지 설명

`src/pages` 아래에 등록된 화면과 현재 구현된 기능은 다음과 같습니다. `공통`은 부르미와 드리미가 함께 사용하는 화면입니다.

| 페이지                | 경로 | 사용자 | 기능                                                                                                                 |
|--------------------| --- | --- |--------------------------------------------------------------------------------------------------------------------|
| `onboarding`       | `/` | 공통·비회원 | 서비스를 소개하고 부르미·드리미 역할을 안내합니다. 회원가입과 로그인 화면으로 이동할 수 있습니다.                                                            |
| `login`            | `/login` | 비회원 | 이메일과 비밀번호로 로그인하고 세션을 생성합니다. 회원가입 화면으로 이동할 수 있습니다.                                                                  |
| `signup`           | `/signup` | 비회원 | 이름, 생년월일, 전화번호, 이메일, 비밀번호와 약관 동의를 입력받습니다. 전화번호 인증 후 회원가입을 처리합니다.                                                   |
| `verify`           | `/verify` | 공통 | 신분증·사원증을 이용한 본인인증과 드리미 등록을 안내합니다. 등록을 미루고 부르미로 시작할 수도 있습니다.                                                        |
| `home`             | `/home` | 공통 | 역할 토글로 부르미 홈과 드리미 홈을 전환합니다. 부르미는 부름 등록과 진행 중인 주문 조회, 드리미는 콜 탐색과 진행 중인 배송 진입 기능을 사용합니다.                             |
| `request-create`   | `/request-create` | 부르미 | **(부름 등록)** 위치 → 물품 → 사진·요청사항 → 결제의 4단계로 부름을 등록합니다. 예상 요금 조회, 이미지 업로드, 주문 생성 API를 연동합니다.                           |
| `destination-search` | `/destination-search` | 부르미 | 지도와 검색 필드, 빠른 선택, 최근·추천 목록으로 도착지를 선택하는 보조 화면입니다. 현재 검색 UI만 구현되어 있습니다.                                              |
| `matching`         | `/matching` | 공통 | 주변 부르미 또는 드리미를 찾는 대기 상태를 지도 위에 표시합니다. 실제 콜·오퍼 수락 팝업은 전역 `MatchingPopup`에서 처리합니다.                                   |
| `reject-reason`    | `/reject-reason` | 부르미 | 제안한 드리미를 거절할 때 사유를 선택하고 기타 사유를 입력합니다. 현재 제출 동작은 UI 흐름만 구현되어 있습니다.                                                  |
| `delivery-track`   | `/delivery-track` | 드리미 | **(드리미가 지도 확인)**픽업중·배송중 상태, 목적지, 예상 시간과 남은 거리를 표시합니다. 픽업·전달 사진 인증으로 이동하고 드리미의 픽업 전 취소 및 상대방 취소 SSE 알림을 처리합니다.      |
| `delivery-detail` | `/delivery-detail` | 부르미 | **(부르미가 지도 확인)**배정된 드리미의 위치와 픽업·배송 상태를 실시간으로 추적합니다. `orderId`가 있으면 SSE 기반 실제 추적과 부르미 취소를 사용하고, 없으면 mock 상세를 표시합니다. |
| `delivery-proof`   | `/delivery-proof` | 드리미 | 픽업 또는 배송 완료를 사진·서명으로 인증합니다. 실제 모드에서는 presigned URL 업로드 후 `pickup-finish` 또는 `finish` 상태 전이 API를 호출합니다.             |
| `delivery-complete` | `/delivery-complete` | 배송 완료 흐름 | 완료 사진과 물품·담당 드리미·소요 시간·결제 금액을 보여주고 드리미 평가를 받습니다. 현재 드리미의 실제 완료 플로우에서도 이 화면으로 진입합니다.                                |
| `driver-reason`    | `/driver-reason` | 드리미 | 배송 중 사고, 지연, 분실 등의 사유를 제출하고 활성 배달을 사고·취소 처리합니다.                                                                    |
| `activity`         | `/activity` | 공통 | 역할별 활동 내역을 전체·진행중·완료·취소로 필터링합니다. 부르미 내역은 실제 주문 API와 페이지네이션을 사용하고 드리미 내역은 현재 mock 데이터를 사용합니다.                       |
| `activity-detail`  | `/activity-detail` | 공통 | 진행 중 내역은 경로·지도·도착 정보·연락 수단을, 부르미의 완료 내역은 배송 요약·결제 정보·평가 UI를 보여줍니다. `status`와 `id` 쿼리로 표시 내용을 결정합니다.                |
| `activity-detail-driver` | `/activity-detail-driver` | 드리미 | 드리미가 완료한 배송의 인증 사진, 이동 경로, 정산 내역과 부르미 평가 UI를 보여줍니다.                                                                |
| `earnings`         | `/earnings` | 공통 | 역할 토글에 따라 부르미의 절감 리포트 또는 드리미의 수익·월간 추이 리포트를 보여줍니다.                                                                 |
| `wallet`           | `/wallet` | 공통 | 배송 결제용 포인트와 드리미 수익 머니 잔액, 최근 입출금 내역을 보여줍니다. 포인트 충전·전환 화면으로 이동할 수 있습니다.                                             |
| `point-charge`     | `/point-charge` | 공통 | 기본 모드에서는 카드 결제로 포인트를 충전하고, `mode=convert`에서는 드리미 머니를 포인트로 전환합니다.                                                   |
| `mypage`           | `/mypage` | 공통 | 프로필과 역할·평점, 드리미 정산 계좌, 계정·지원 메뉴를 표시하고 로그아웃을 처리합니다.                                                                 |
| `delivery-test`    | `/delivery-test` | 개발용 | 부르미·드리미 ID로 테스트 주문과 배달을 강제로 시작한 뒤 실제 드리미 배송 추적 화면으로 넘기는 백엔드 연동 테스트 화면입니다.                                          |

### 배송 진행 화면 구분

- 부르미는 `delivery-detail`에서 드리미 위치와 배송 상태를 추적합니다.
- 드리미는 `delivery-track`에서 픽업·배송을 진행하고 `delivery-proof`에서 인증합니다.
- `delivery-complete`는 현재 완료 결과와 드리미 평가 UI를 보여주지만, 드리미의 실제 배송 완료 흐름에서도 진입하고 있어 역할 관점 정리가 추가로 필요합니다.

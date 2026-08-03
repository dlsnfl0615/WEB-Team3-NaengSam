import { defineConfig } from 'orval'

/**
 * 백엔드 OpenAPI(SpringDoc) 스펙에서 axios 기반 API 클라이언트를 생성한다.
 * 실행: 백엔드를 `http://localhost:8080`에 띄운 뒤 `pnpm api:gen`.
 *
 * - `client: 'axios'` — 순수 async 함수 생성(react-query 미사용, 기존 Zustand 스토어에서 호출).
 * - `mutator` — 모든 요청을 공통 axios 인스턴스로 보내 세션 쿠키·에러 정규화를 일괄 적용.
 * - `mode: 'split'` — 단일 endpoints 파일 + model 디렉터리. (백엔드 @Tag가 한글이라
 *   지금은 split을 쓰고, 영문 태그명이 붙으면 'tags-split'으로 도메인별 파일 분리 가능.)
 * 생성물(`src/shared/api/generated/**`)은 직접 수정하지 않는다.
 */
export default defineConfig({
  naengsam: {
    input: {
      target: 'http://localhost:8080/v3/api-docs',
    },
    output: {
      mode: 'split',
      target: 'src/shared/api/generated/endpoints.ts',
      schemas: 'src/shared/api/generated/model',
      client: 'axios',
      clean: true,
      // servers[0].url(http://localhost:8080)을 요청 URL에 붙이지 않고 상대경로(/api/v1/...)로 둔다.
      baseUrl: '',
      override: {
        mutator: {
          path: 'src/shared/api/http/customInstance.ts',
          name: 'customInstance',
        },
      },
    },
  },
})

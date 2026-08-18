<div align="center">

![home.png](docs/img/home.png)

# 쉼, 부름

**근거리 서류 · 샘플 · 비품을 오가는 사람의 손으로 전하는 P2P 배송 플랫폼**

동선 중 유휴 시간을 가진 직장인이 배송을 수행하고, 필요한 사람은 빠르고 저렴하게 물품을 전달받습니다.
</div>

<div align="center">

### 기획 · 명세

[![기능명세서](https://img.shields.io/badge/기능명세서-Google%20Sheets-34A853?style=for-the-badge&logo=googlesheets&logoColor=white)](https://docs.google.com/spreadsheets/d/1OPN7gDw0_ZbZzS4ikvkhgRhP_cMBv9GLgR7UGgOX0Yo/edit?gid=510946227#gid=510946227)
[![API 명세서](https://img.shields.io/badge/API%20명세서-Notion-000000?style=for-the-badge&logo=notion&logoColor=white)](https://app.notion.com/p/API-3a419935b8ae80239efad93d09355588?source=copy_link)

[![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)](https://pretty-cheque-c57.notion.site/39f19935b8ae809f9890c82e7577c01d?source=copy_link)

### 설계

[![ERD](https://img.shields.io/badge/ERD-ERDCloud-1E6FEB?style=for-the-badge)](https://www.erdcloud.com/d/yRGsks3sXTGLktNJF)
[![Figma](https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white)](https://www.figma.com/design/ERZUfKLiF6VynCX0FmrdhZ/%EC%89%BC-%EB%B6%80%EB%A6%84?node-id=165-1590&t=d5Wr8icAVuTwSpo6-1)
[![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://symboorm.duckdns.org/swagger-ui/index.html)


### 협업 · 문서

[![Wiki](https://img.shields.io/badge/Wiki-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki)
[![Discussions](https://img.shields.io/badge/Discussions-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/discussions)
[![Ground Rule](https://img.shields.io/badge/Ground%20Rule-그라운드%20룰-2088FF?style=for-the-badge&logo=readthedocs&logoColor=white)](docs/ground_rule.md)
[![Git 컨벤션](https://img.shields.io/badge/Git%20컨벤션-깃%20컨벤션-2088FF?style=for-the-badge&logo=git&logoColor=white)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EA%B9%83-%EC%BB%A8%EB%B2%A4%EC%85%98)


</div>

---

## 목차

- [👥 팀원](#-팀원)
- [👋 어떤 서비스인가요](#-어떤-서비스인가요)
- [🧭 핵심 흐름](#-핵심-흐름)
- [서비스 아키텍쳐](#서비스-아키텍쳐)
- [🧠 기술 결정](#-기술-결정)
- [🔧 트러블슈팅](#-트러블슈팅)
- [🧪 부하테스트](#-부하테스트)

---

## 👥 팀원

|                         [서석희](https://github.com/seoki180)                         |                          [이동혁](https://github.com/hyeok2044)                           |                           [임현성](https://github.com/hwhyeons)                           |                          [정현서](https://github.com/dlsnfl0615)                           |
|:----------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------:|
| <img src="docs/img/seoki.JPG" width="120" height="140" style="object-fit: cover;"> | <img src="docs/img/hyeok2044.png" width="120" height="140" style="object-fit: cover;"> | <img src="docs/img/hwhyeons.jpeg" width="120" height="140" style="object-fit: cover;"> | <img src="docs/img/dlsnfl0615.png" width="120" height="140" style="object-fit: cover;"> |

## 👋 어떤 서비스인가요

한 계정으로 **요청자와 수행자 역할을 스와이프로 전환**하며 겸용할 수 있습니다.
어떤 날은 물건을 보내는 **부르미**, 어떤 날은 오가는 길에 배송하고 보수를 받는 **드리미**가 됩니다.

|                    캐릭터                     |   역할    |     코드명      | 하는 일                            |
|:------------------------------------------:|:-------:|:------------:|---------------------------------|
| <img src="docs/img/boormi.png" width="90"> | 🙋 요청자  | **부르미** (P1) | 근거리 서류·샘플·비품 전달이 필요한 사무직 실무자    |
| <img src="docs/img/dreami.png" width="90"> | 🛵 수행자  | **드리미** (P2) | 동선 중 유휴 시간에 배송을 수행하고 보수를 받는 직장인 |
|                     —                      | 🛠️ 운영자 |    Admin     | 인증 심사, 신고 처리, 요청 모니터링           |

---

## 🧭 핵심 흐름

```text
부르미 퀵 등록  →  주변 드리미 매칭  →  픽업 인증  →  전달 인증  →  정산 · 평점
                     └──────── 실시간 추적 ────────┘
```

---

## 서비스 아키텍쳐

<img width="1504" height="1656" alt="image" src="https://github.com/user-attachments/assets/1c066699-0073-4cd7-8d71-dfd2b49a5c9a" />


### ERD
<img width="4650" height="2342" alt="image" src="https://github.com/user-attachments/assets/d53e9b18-66ec-49f7-9a71-cff1821025e5" />



### Infra · DevOps

![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Docker Hub](https://img.shields.io/badge/Docker%20Hub-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white)
![AWS S3](https://img.shields.io/badge/AWS%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white)
![CloudFront](https://img.shields.io/badge/CloudFront-232F3E?style=for-the-badge&logo=amazon&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus%203.1-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana%2011.5-F46800?style=for-the-badge&logo=grafana&logoColor=white)

### Backend

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%204.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Session Auth](https://img.shields.io/badge/세션%20기반%20인증-000000?style=for-the-badge)
![SSE](https://img.shields.io/badge/SSE-FF6B6B?style=for-the-badge)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![AWS S3](https://img.shields.io/badge/AWS%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white)
![Kakao API](https://img.shields.io/badge/Kakao%20Local%20API-FFCD00?style=for-the-badge&logo=kakao&logoColor=black)
![Solapi](https://img.shields.io/badge/Solapi-00C4B4?style=for-the-badge)
![Web Push](https://img.shields.io/badge/Web%20Push%20(VAPID)-5A0FC8?style=for-the-badge)


### ️ Frontend

![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![React](https://img.shields.io/badge/React%2019-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite%208-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![pnpm](https://img.shields.io/badge/pnpm%2011-F69220?style=for-the-badge&logo=pnpm&logoColor=white)
![Zustand](https://img.shields.io/badge/Zustand%205-443E38?style=for-the-badge)
![React Router](https://img.shields.io/badge/React%20Router%207-CA4245?style=for-the-badge&logo=reactrouter&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS%204-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)
![Axios](https://img.shields.io/badge/Axios-5A29E4?style=for-the-badge&logo=axios&logoColor=white)
![Orval](https://img.shields.io/badge/Orval%208-F09000?style=for-the-badge)
![PWA](https://img.shields.io/badge/PWA-5A0FC8?style=for-the-badge&logo=pwa&logoColor=white)
![ESLint](https://img.shields.io/badge/ESLint-4B32C3?style=for-the-badge&logo=eslint&logoColor=white)

### Test

![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=for-the-badge&logo=junit5&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-C5D9C8?style=for-the-badge)
![AssertJ](https://img.shields.io/badge/AssertJ-2B2D42?style=for-the-badge)
![Vitest](https://img.shields.io/badge/Vitest%204-6E9F18?style=for-the-badge&logo=vitest&logoColor=white)
![Testing Library](https://img.shields.io/badge/Testing%20Library-E33332?style=for-the-badge&logo=testinglibrary&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=for-the-badge&logo=k6&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-339933?style=for-the-badge&logo=nodedotjs&logoColor=white)
![Playwright](https://img.shields.io/badge/Playwright-2EAD33?style=for-the-badge&logo=playwright&logoColor=white)


## 🧠 기술 결정

- [세션 vs 토큰](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%84%B8%EC%85%98%28Sessions%29-VS-%ED%86%A0%ED%81%B0%28Token%29)
- [매칭 시스템 설계](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD_%EC%8B%9C%EC%8A%A4%ED%85%9C_%EC%84%A4%EA%B3%84)
- [세션 및 다중 탭 처리 정책](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%84%B8%EC%85%98-%EB%B0%8F-%EB%8B%A4%EC%A4%91-%ED%83%AD-%EC%B2%98%EB%A6%AC-%EC%A0%95%EC%B1%85)
- [드리미 GPS 끊김 감지 정책](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%93%9C%EB%A6%AC%EB%AF%B8-GPS-%EB%81%8A%EA%B9%80-%EA%B0%90%EC%A7%80-%EC%A0%95%EC%B1%85)
- [Redis 기반 로그인 대기열 도입기](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/Redis-%EA%B8%B0%EB%B0%98-%EB%A1%9C%EA%B7%B8%EC%9D%B8-%EB%8C%80%EA%B8%B0%EC%97%B4-%EB%8F%84%EC%9E%85%EA%B8%B0)
- [활동 내역 조회를 커서 기반 페이지네이션으로 진행](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%ED%99%9C%EB%8F%99-%EB%82%B4%EC%97%AD-%EC%A1%B0%ED%9A%8C%EB%A5%BC-%EC%BB%A4%EC%84%9C-%EA%B8%B0%EB%B0%98-%ED%8E%98%EC%9D%B4%EC%A7%80%EB%84%A4%EC%9D%B4%EC%85%98%EC%9C%BC%EB%A1%9C-%EC%A7%84%ED%96%89)

## 🔧 트러블슈팅

- [UploadSession - 옛 키 재사용 공격 방어 설계](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/UploadSession-%E2%80%90-%EC%98%9B-%ED%82%A4-%EC%9E%AC%EC%82%AC%EC%9A%A9-%EA%B3%B5%EA%B2%A9-%EB%B0%A9%EC%96%B4-%EC%84%A4%EA%B3%84)
- [8/6 · EC2 SSH 접속 불가](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/8_6_%EB%B0%B0%ED%8F%AC%EC%A4%91_%EC%9E%A5%EC%95%A0)
- [8/7 · 매칭 확정 후 Delivery가 생성되지 않는 고아 Order 문제](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%ED%99%95%EC%A0%95-%ED%9B%84-Delivery%EA%B0%80-%EC%83%9D%EC%84%B1%EB%90%98%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B3%A0%EC%95%84-Order-%EB%AC%B8%EC%A0%9C)
- [8/8 · 영속성 컨텍스트 detach로 dirty checking 미반영 문제 (@Modifying(clearAutomatically = true))](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%98%81%EC%86%8D%EC%84%B1-%EC%BB%A8%ED%85%8D%EC%8A%A4%ED%8A%B8-detach%EB%A1%9C-dirty-checking-%EB%AF%B8%EB%B0%98%EC%98%81-%EB%AC%B8%EC%A0%9C-%28%40Modifying%28clearAutomatically-%3D-true%29%29)
- [매칭 확정 후 Delivery 생성 실패로 Orders가 `IN_PROGRESS`에 고착되는 문제](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%ED%99%95%EC%A0%95-%ED%9B%84-Delivery-%EC%83%9D%EC%84%B1-%EC%8B%A4%ED%8C%A8%EB%A1%9C-Orders%EA%B0%80-%60IN_PROGRESS%60%EC%97%90-%EA%B3%A0%EC%B0%A9%EB%90%98%EB%8A%94-%EB%AC%B8%EC%A0%9C)
- [매칭이 성공하지 않았음에도 Orders 테이블에 PENDING으로 상태 변경하는 문제](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD%EC%9D%B4-%EC%84%B1%EA%B3%B5%ED%95%98%EC%A7%80-%EC%95%8A%EC%95%98%EC%9D%8C%EC%97%90%EB%8F%84-Orders-%ED%85%8C%EC%9D%B4%EB%B8%94%EC%97%90-PENDING%EC%9C%BC%EB%A1%9C-%EC%83%81%ED%83%9C-%EB%B3%80%EA%B2%BD%ED%95%98%EB%8A%94-%EB%AC%B8%EC%A0%9C)
- [부르미 확인 타임아웃 시 주문 DB·매칭 메모리 불일치](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%B6%80%EB%A5%B4%EB%AF%B8-%ED%99%95%EC%9D%B8-%ED%83%80%EC%9E%84%EC%95%84%EC%9B%83-%EC%8B%9C-%EC%A3%BC%EB%AC%B8-DB%C2%B7%EB%A7%A4%EC%B9%AD-%EB%A9%94%EB%AA%A8%EB%A6%AC-%EB%B6%88%EC%9D%BC%EC%B9%98)
- [주변 콜 조회 수동 N+1 개선](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%A3%BC%EB%B3%80-%EC%BD%9C-%EC%A1%B0%ED%9A%8C-%EC%88%98%EB%8F%99-N-1-%EA%B0%9C%EC%84%A0)
- [카카오 API 응답 Redis 캐시 설계](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%B9%B4%EC%B9%B4%EC%98%A4-API-%EC%9D%91%EB%8B%B5-Redis-%EC%BA%90%EC%8B%9C-%EC%84%A4%EA%B3%84)
- [드리미 오프라인 조회에 필요한 복합 인덱스 추가](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%93%9C%EB%A6%AC%EB%AF%B8-%EC%98%A4%ED%94%84%EB%9D%BC%EC%9D%B8-%EC%A1%B0%ED%9A%8C%EC%97%90-%ED%95%84%EC%9A%94%ED%95%9C-%EB%B3%B5%ED%95%A9-%EC%9D%B8%EB%8D%B1%EC%8A%A4-%EC%B6%94%EA%B0%80)
- [매칭 단계 취소시 포인트 이중 환불 문제](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%8B%A8%EA%B3%84-%EC%B7%A8%EC%86%8C%EC%8B%9C-%ED%8F%AC%EC%9D%B8%ED%8A%B8-%EC%9D%B4%EC%A4%91-%ED%99%98%EB%B6%88-%EB%AC%B8%EC%A0%9C)

## 🧪 부하테스트

- [매칭 부하테스트](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%B6%80%ED%95%98%ED%85%8C%EC%8A%A4%ED%8A%B8)
- [매칭 부하테스트 보고서 (8.12)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%B6%80%ED%95%98%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B3%B4%EA%B3%A0%EC%84%9C-8.12)
- [매칭 부하테스트 보고서 (8.13)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%B6%80%ED%95%98%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B3%B4%EA%B3%A0%EC%84%9C-8.13)
- [매칭 부하테스트 보고서 (8.13 · 외부 API 트랜잭션)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%B6%80%ED%95%98%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B3%B4%EA%B3%A0%EC%84%9C-8.13-%EC%99%B8%EB%B6%80-API-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98)
- [매칭 부하테스트 보고서 (8.16 · 동시 접속 용량)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EB%A7%A4%EC%B9%AD-%EB%B6%80%ED%95%98%ED%85%8C%EC%8A%A4%ED%8A%B8-%EB%B3%B4%EA%B3%A0%EC%84%9C-8.16-%EB%8F%99%EC%8B%9C-%EC%A0%91%EC%86%8D-%EC%9A%A9%EB%9F%89)

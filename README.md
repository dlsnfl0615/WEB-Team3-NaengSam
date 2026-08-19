<div align="center">

<img width="2334" height="1317" alt="home" src="https://github.com/user-attachments/assets/196843de-e45c-4a15-83ea-c6ed92dfed00" />


# 쉼, 부름

**근거리 서류 · 샘플 · 비품을 오가는 사람의 손으로 전하는 P2P 배송 플랫폼**

동선 중 유휴 시간을 가진 직장인이 배송을 수행하고, 필요한 사람은 빠르고 저렴하게 물품을 전달받습니다.
</div>

<div align="center">
  
### 서비스 링크

[![서비스 링크](https://img.shields.io/badge/쉼,%20부름-FF9900?style=for-the-badge&logo=amazon&logoColor=white)](https://d3cev4xst074qp.cloudfront.net/)

### 기획 · 명세

[![기능명세서](https://img.shields.io/badge/기능명세서-Google%20Sheets-34A853?style=for-the-badge&logo=googlesheets&logoColor=white)](https://docs.google.com/spreadsheets/d/1OPN7gDw0_ZbZzS4ikvkhgRhP_cMBv9GLgR7UGgOX0Yo/edit?gid=510946227#gid=510946227)
[![API 명세서](https://img.shields.io/badge/API%20명세서-Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://symboorm.duckdns.org/swagger-ui/index.html)

[![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)](https://pretty-cheque-c57.notion.site/39f19935b8ae809f9890c82e7577c01d?source=copy_link)

### 설계

[![ERD](https://img.shields.io/badge/ERD-ERDCloud-1E6FEB?style=for-the-badge)](https://www.erdcloud.com/d/yRGsks3sXTGLktNJF)
[![Figma](https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white)](https://www.figma.com/design/ERZUfKLiF6VynCX0FmrdhZ/%EC%89%BC-%EB%B6%80%EB%A6%84?node-id=165-1590&t=d5Wr8icAVuTwSpo6-1)



### 협업 · 문서

[![Wiki](https://img.shields.io/badge/Wiki-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki)
[![Discussions](https://img.shields.io/badge/Discussions-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/discussions?discussions_q=)
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
- [🧪 부하테스트](#-부하테스트)
- [🔧 트러블슈팅](#-트러블슈팅)
- [🗓️ 데일리 스크럼](https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/%EC%95%84%EC%B9%A8-%EC%8A%A4%ED%81%AC%EB%9F%BC)

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


## 사용 방법

### 1. **퀵 등록** — 부르미가 물품 정보·출발지/도착지를 입력하면, 서버가 실제 도보 경로로 배달비·예상 시간을 계산해 주문을 만듭니다.

https://github.com/user-attachments/assets/57ced127-9002-46dc-a1ee-c4c4b11a90db

### 2. **주변 드리미 매칭** — 주변에 대기 중인 드리미에게 실시간으로 제안을 보내고, 가장 먼저 수락한 드리미로 배정합니다.
### 3. **부르미 확인** — 드리미가 수락하면 부르미에게 확인 알림이 가고, 부르미가 승인하면 매칭이 완료됩니다.

https://github.com/user-attachments/assets/0073e522-5d08-407e-b580-e9a19436e5d4

### 4. **픽업 인증** — 드리미가 물품을 픽업하면서 사진으로 인증하면 배달이 시작됩니다.

https://github.com/user-attachments/assets/93de7eec-5791-4928-8c26-4c58e0882168

### 5. **실시간 추적** — 픽업부터 전달까지 부르미는 드리미의 실시간 위치를 지도에서 확인할 수 있습니다.

https://github.com/user-attachments/assets/6821df7b-c08f-4e9f-bb28-a2c54caed6cc

### 6. **전달 인증** — 드리미가 도착지에서 전달 완료를 사진으로 인증하면 주문이 완료 처리됩니다.

https://github.com/user-attachments/assets/dafdf5b5-3258-4575-abc0-3c9333ebda79

### 7. **정산 · 평점** — 배달비가 드리미에게 정산되고, 부르미와 드리미가 서로에게 별점을 남깁니다.

드리미는 마이페이지에서 본인인증(신분증·범죄이력조회서)을 마치면 전환되고, 홈 화면 상단 토글로 한 계정 안에서 부르미·드리미 역할을 그때그때 바꿔가며 쓸 수 있습니다.


---

## 🧭 핵심 흐름

```text
부르미 퀵 등록  →  주변 드리미 매칭  →  픽업 인증  →  전달 인증  →  정산 · 평점
                     └──────── 실시간 추적 ────────┘
```

<img width="1867" height="677" alt="image" src="https://github.com/user-attachments/assets/71e65630-ce26-493a-8fa7-09d60237c447" />


---

## 서비스 아키텍쳐
<img width="1504" height="1656" alt="image" src="https://github.com/user-attachments/assets/b016bcd7-afc1-44ef-afa1-63b127493370" />



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



**🧠 기술 결정 기록 (ADR)**

- [[세션 vs 토큰|세션(Sessions)-VS-토큰(Token)]]
- [[매칭 시스템 설계|매칭_시스템_설계]]
- [[매칭 시스템 구현|매칭-시스템-구현]]
- [[세션 및 다중 탭 처리 정책|세션 및 다중 탭 처리 정책]]
- [[실시간 배달 상태 전달에 SSE를 선택한 이유]]
- [[SSE 연결은 왜 1개였다가 5개가 되고, 다시 1개가 되었을까|SSE-연결은-왜-1개였다가-5개가-되고,-다시-1개가-되었을까]]
- [[드리미 GPS 끊김 감지 정책]]
- [[Redis 기반 로그인 대기열 도입기]]
- [[활동 내역 조회를 커서 기반 페이지네이션으로 진행]]
- [[알림 - WebPush 도입 이유와 SSE의 한계]]
- [[S3 Presigned URL 도입 결정|S3-Presigned-URL-도입-결정]]
- [[포인트, 머니 시스템 설계]]

**🧪 부하 테스트**

- [[매칭 부하테스트|매칭-부하테스트]]
- [[매칭 부하테스트 보고서 (8.12)|매칭-부하테스트-보고서-8.12]]
- [[매칭 부하테스트 보고서 (8.13)|매칭-부하테스트-보고서-8.13]]
- [[매칭 부하테스트 보고서 (8.13 · 외부 API 트랜잭션)|매칭-부하테스트-보고서-8.13-외부-API-트랜잭션]]
- [[매칭 부하테스트 보고서 (8.16 · 동시 접속 용량)|매칭-부하테스트-보고서-8.16-동시-접속-용량]]
- [[매칭 부하테스트 보고서 (8.18 · 배포환경 720명)|매칭-부하테스트-보고서-8.18-배포환경-720명]]

**🔧 트러블슈팅**

- [[ UploadSession - 옛 키 재사용 공격 방어 설계|UploadSession ‐ 옛 키 재사용 공격 방어 설계]]
- [[8/6 · EC2 SSH 접속 불가|8_6_배포중_장애]]
- [[8/7 · 매칭 확정 후 Delivery가 생성되지 않는 고아 Order 문제|매칭 확정 후 Delivery가 생성되지 않는 고아 Order 문제]]
- [[8/8 · 영속성 컨텍스트 detach로 dirty checking 미반영 문제 (@Modifying(clearAutomatically = true))|영속성 컨텍스트 detach로 dirty checking 미반영 문제 (@Modifying(clearAutomatically = true))]]
- [[매칭 확정 후 Delivery 생성 실패로 Orders가 IN_PROGRESS에 고착되는 문제|https://github.com/softeerbootcamp-8th/WEB-Team3-NaengSam/wiki/매칭-확정-후-Delivery-생성-실패로-Orders가-%60IN_PROGRESS%60에-고착되는-문제]]
- [[매칭이 성공하지 않았음에도 Orders 테이블에 PENDING으로 상태 변경하는 문제]]
- [[부르미 확인 타임아웃 시 주문 DB·매칭 메모리 불일치]]
- [[주변 콜 조회 시 반복되던 주문 조회 개선]]
- [[카카오 API 응답 Redis 캐시 설계]]
- [[드리미 오프라인 조회에 필요한 복합 인덱스 추가]]
- [[매칭 단계 취소시 포인트 이중 환불 문제]]
- [[로컬 DevStorage 사용시 로그인 상태에서도 401 UNAUTHORIZED 뜨던 문제]]


# Dreami 도메인

드리미(배달을 수행하는 역할)의 인증, 온라인/오프라인 전환, 오퍼(제안) 수락/거절, 주변 콜 조회, 대시보드/활동내역/프로필을 담당한다.
한 사람이 부르미(주문자)이자 드리미일 수 있어서, `Dreami`의 PK는 `Boormi`와 **동일한 UUID**를 그대로 쓴다.

## 1. `Dreami` 엔티티와 상태

```
dreamiId          UUID (PK, boormiId와 동일)
requestCd         REQUESTED | REVIEWING | APPROVED | REJECTED
idCardKey / criminalRecordKey   S3 key (업로드 도메인 참고)
rejectDetail      최신 반려 사유
requestDtm / reviewDtm
dreamiAvgScore    DECIMAL(3,2), 기본 0
```

- `REVIEWING`은 enum에 정의만 있고 실제로 세팅하는 코드는 없다. 실사용은 `REQUESTED`(신청 직후) → `APPROVED`/`REJECTED`(관리자 처리) 두 단계뿐이다.
- "인증 승인 여부"(`Dreami.requestCd`)와 "드리미 기능 활성화 여부"(`Boormi.isDreamiActivate`)는 별개 필드다. 관리자가 승인할 때 **둘 다 같이 바뀌어야** 드리미 기능이 실제로 켜진다.
- `DreamiRequestDeniedDetails`는 반려할 때마다 row를 쌓는 이력 테이블이다. `Dreami.rejectDetail`은 "최신 사유" 하나만 갖고, 누적 반려 횟수는 이 테이블 count로 구한다(프로필 조회에서 사용).

## 2. 드리미 인증 흐름 (제출 → 검수 → 승인/반려)

**제출** — `POST /api/v1/dreami/verification`

1. 이미 승인된 드리미면 `ALREADY_APPROVED`로 먼저 걸러낸다.
2. 신분증/범죄이력조회서 각각의 업로드 key를 [Upload 도메인](../upload/overview.md)의 `checkUpload`로 검증한다(실제 업로드됐는지 + 소유자/용도 일치).
3. 둘 중 하나라도 새로 검증에 성공하면 저장한다. 재제출로 하나만 다시 올린 경우(예: 범죄이력조회서만 재업로드)에도 저장은 일어나며, **둘 다 이미 처리된 재시도일 때만** 저장을 건너뛴다.
4. 저장은 `PESSIMISTIC_WRITE` 락으로 다시 조회한 뒤 승인 여부를 한 번 더 확인하고 진행한다. "승인 여부 확인"과 "저장" 사이에 관리자가 승인을 확정해버리는 레이스(TOCTOU)가 실제로 있었고, 이 락 재확인이 그 안전장치다.
5. 재제출은 `Dreami.create(dreamiId, ...)`로 새로 만든 엔티티를 그대로 `save()`하는데, PK(`dreamiId` = `boormiId`)가 이미 존재하는 값이라 JPA가 `persist`가 아니라 **`merge`로 처리해 같은 row를 덮어쓴다**(새 row가 생기는 게 아니라 같은 PK의 UPDATE다). 그 결과 상태가 `REQUESTED`로, 평점도 0으로 리셋된다. 이미 승인된 드리미의 재신청을 막는 이유가 여기 있다(리셋을 막기 위해).

**검수(관리자)** — `DreamiReviewDebugController` (`/api/v1/debug/dreami-review`)

- `GET /pending` — 심사 대기(`REQUESTED`) 목록. 신분증/범죄이력조회서를 다운로드 URL로 변환해 함께 내려준다.
- `POST /{dreamiId}/approve` — `Dreami.approve()` + `Boormi.approve()`(`isDreamiActivate = true`)를 **함께** 처리한다. 과거엔 `Boormi` 쪽을 빼먹어서 "승인해도 활성화가 안 되는" 버그가 있었다.
- `POST /{dreamiId}/reject` — `Dreami.reject(reason)` + 반려 이력 저장.

> **주의 — 이 워크트리 기준 현재 상태**: `DreamiReviewDebugController`는 `@PublicApi`로 선언되어 있어 **로그인 검사를 건너뛴다**(완전 공개). 클래스 Javadoc에도 "임시 관리자 페이지, 운영 배포 전 제거/비활성화 필요"라고 명시돼 있다. 관리자 인증(`@AdminUser`)을 적용하는 변경은 별도 브랜치(`feat/455`)에만 있고 이 브랜치엔 아직 병합되지 않았다.

`DreamiActivationChecker`는 "지금 드리미로 활동 가능한가?"(`Boormi.isDreamiActivate && Dreami.requestCd == APPROVED`)만 판정하는 순수 조회 컴포넌트다. 서비스 계층을 참조하지 않고 리포지토리에만 의존하도록 만들었는데, 매칭↔배달↔유저 사이 순환 참조를 만들지 않기 위해서다.

## 3. 온라인/오프라인 전환

`POST /api/v1/dreami/status/online`

1. 드리미 미존재 → `NOT_FOUND`
2. 승인되지 않음(`requestCd != APPROVED`) → `NOT_APPROVED`
3. 본인이 부르미든 드리미든 **수행 중인 주문이 이미 있음** → `ALREADY_HAS_ACTIVE_ORDER`
4. 통과하면 매칭 엔진에 위치와 함께 등록

이미 온라인(매칭 대기 중)이어도 이 API 자체는 거부하지 않는다 — 클라이언트의 온라인 상태는 새로고침하면 사라지는 메모리 상태라, 여기서 거부하면 화면이 "오프라인"으로 굳어버려 복구가 안 되기 때문이다. 중복 등록 자체는 매칭 엔진 내부에서 무시된다.

`POST /api/v1/dreami/status/offline`은 별도 검증 없이 매칭 엔진에서 제거만 한다.

## 4. 오퍼(제안) 수락/거절

`POST /api/v1/dreami/offers/{offerId}/accept` · `POST /api/v1/dreami/offers/{offerId}/reject`

여러 드리미가 같은 주문에 동시에 수락 버튼을 누르는 상황에서 발생한 동시성 버그를 3단계에 걸쳐 고쳤다.

1. **레이스 자체 수정** — 원래는 락 없이 주문을 조회해 무조건 상태를 전이시켰다. 부하테스트에서 200건 중 4건꼴로, 나중에 커밋된(패배한) 드리미의 트랜잭션이 이미 확정된 주문을 되돌려버리는 문제가 있었고 실제 장애로도 이어졌다. 주문 조회에 `PESSIMISTIC_WRITE` 락을 걸고, 락을 잡은 뒤 상태가 `MATCHING`이 아니면 예외를 던지도록 고쳤다.
2. **에러 세분화** — "이미 다른 드리미가 선점"과 "그 외 수락 불가 상태(취소·완료 등)"를 구분하지 않고 뭉뚱그려 안내하고 있었다. 원인별로 다른 에러 코드로 분리했다.
3. **멱등 처리** — 더블클릭이나 재시도로 **같은 드리미가 같은 오퍼를 두 번 수락**하면, 이미 자기가 성공시킨 요청인데도 "다른 드리미가 선점했다"는 잘못된 에러가 나갔다. 수락 시 주문에 드리미 id를 기록해두고, 재시도 시 본인 요청이면 예외 없이 조용히 통과하도록 고쳤다.

거절은 소유권만 확인하고 매칭 엔진에서 거절 처리만 한다(DB 상태 변경 없음, 주문은 계속 매칭 대기).

오퍼 물품 사진(`GET /api/v1/dreami/offers/{offerId}/item-photo`)은 수락 전이라 주문에 드리미가 아직 배정되지 않은 상태라, 매칭 엔진의 "이 오퍼의 대상 드리미인지" 판정으로 접근 권한을 확인한다. 매칭 엔진의 SSE 발송 경로(단일 스레드)에 S3 조회를 얹지 않으려고 별도 API로 분리했다.

## 5. 주변 콜 리스트

`POST /api/v1/dreami/calls/nearby` — 매칭 도메인에서 위치 기준으로 가까운 주문 id/거리만 받아온 뒤, 각 주문을 다시 조회해 품목·주소·예상수익·예상 도착시간을 채워 응답한다(매칭 도메인은 주문 상세를 갖고 있지 않기 때문).

## 6. 현재 수행 중인 배달

`GET /api/v1/dreami/deliveries/current/card` — 진행 중인 주문이 없으면 예외 대신 **`null`을 반환**한다(정상적인 상태로 취급).

드리미의 "현재 배달 취소" 기능은 이 도메인에 없다. 예전에는 dreami 도메인에 별도 취소 API가 있었지만, delivery 도메인의 취소 API와 기능이 겹쳐서 통합·제거됐다. 지금은 `DeliveryController`의 취소 API가 이 역할을 한다.

## 7. 대시보드 / 오늘 통계

`GET /api/v1/dreami/dashboard`

- 완료 건수는 전체 기간 누적.
- 최근 6개월치 정산 데이터를 **쿼리 한 번**으로 가져와 메모리에서 이번 달/전월/월별 리스트로 집계한다(월마다 따로 쿼리하던 것을 이 방식으로 줄였다).
- 증감률은 전월 수익이 0이면 0%로 처리(0으로 나누기 방지).
- "이번 달 완료 건수"는 원래 "시장 평균 초과 수익"이었던 자리를 대체한 값이다.

`GET /api/v1/dreami/dashboard/today` — 오늘 하루(자정 기준)의 정산 합계와 완료 건수만 별도로 조회한다.

## 8. 활동 내역 / 프로필

- `GET /api/v1/dreami/deliveries` — 부르미/드리미 조회 로직을 `role` 파라미터 하나로 공용화한 주문 조회를 그대로 위임한다. 페이지네이션 파라미터는 제거되어 현재는 전체를 최신순으로 반환한다.
- `GET /api/v1/dreami/deliveries/{orderId}` — 단건 조회. 목록에 페이지네이션이 없어도 딥링크/새로고침으로 상세에 바로 들어갈 수 있게 한다.
- `GET /api/v1/dreami/deliveries/count` — 상태 무관 전체 건수(목록이 일부만 노출될 때 "총 N건" 표시용).
- `GET /api/v1/dreami/{dreamiId}` — 다른 사람(부르미)이 드리미 프로필을 조회할 때 쓴다. 이름·평점·누적 반려 횟수를 함께 내려준다.

## 9. 에러 코드 (`DreamiErrorCode`)

| 코드 | HTTP | 상황 |
|---|---|---|
| `NOT_FOUND` | 404 | 드리미/부르미 미존재 |
| `ALREADY_APPROVED` | 409 | 이미 승인된 드리미가 재신청 |
| `NOT_APPROVED` | 403 | 미승인 드리미가 온라인 전환 시도 |
| `ALREADY_HAS_ACTIVE_ORDER` | 409 | 수행 중인 주문이 있는 채로 온라인 전환 시도 |

오퍼 관련 에러(`ALREADY_ACCEPTED_BY_OTHER`, `NOT_ACCEPTABLE_STATUS`, `NOT_OFFER_OWNER` 등)는 매칭 도메인의 에러 코드다.

## 10. 참고

- 이 도메인은 [Upload 도메인](../upload/overview.md)(인증 파일 검증)과 [Address 도메인](../address/overview.md)(주변 콜의 주소·거리)에 의존한다.
- 드리미 GPS 끊김 감지처럼 이 도메인과 맞닿아 있지만 이번에 다루지 않은 기능은 [docs/hyeonseong/dreami-offline-detection.md](../../hyeonseong/dreami-offline-detection.md)에 별도로 정리되어 있다.
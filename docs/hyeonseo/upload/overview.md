# Upload 도메인

파일(사진) 업로드를 서버가 대신 받지 않고, **presigned URL**로 클라이언트가 S3에 직접 올리게 하는 도메인이다.
서버는 "업로드해도 되는 URL을 발급"하고, 나중에 "그 URL로 실제로 업로드가 됐는지"를 검증하는 역할만 한다.

## 1. 전체 흐름

1. 클라이언트가 `GET /api/v1/upload/url?fileName=...&purpose=...&resourceId=...` 호출 → 서버가 S3 presigned PUT URL과 `key`를 발급, DB에 `UploadSession`을 `ISSUED` 상태로 저장한다.
2. 클라이언트는 그 URL로 파일을 **직접 S3에 PUT**한다. 파일 바이트는 서버를 거치지 않는다.
3. 클라이언트는 그 `key`를 실제 기능 API(드리미 인증 제출, 픽업/배달 완료 인증 등)에 함께 제출한다. 해당 API는 `UploadSessionService.checkUpload(purpose, boormiId, resourceId, key)`를 호출해
    - key가 발급 당시 기록한 `purpose`/`boormiId`/`resourceId`와 일치하는지 확인하고,
    - S3에 실제로 파일이 존재하는지 HEAD 요청으로 확인한 뒤,
    - 세션을 `ISSUED → CONSUMED`로 바꿔 "한 번 소비됐다"고 표시한다.

이 구조가 필요한 이유: presigned URL만 발급하고 실제로는 업로드하지 않은 채, 아무 key나 추측해서 기능 API에 제출하는 것을 막기 위해서다. `UploadSession` row가 없거나, S3에 실제 파일이 없거나, 소유자/용도/대상이 다르면 전부 거부된다.

## 2. API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/upload/url` | presigned URL 발급 (로그인 필요) |
| PUT/GET | `/api/v1/upload/dev-storage?key=...` | 로컬 개발용 — S3 대신 인메모리 저장소에 업로드/다운로드 (`DevStorageController`, `upload.s3-enabled=false`일 때만 활성화) |

`fileName`은 `/`, `\`, `..`가 들어가면 `INVALID_FILE_NAME`으로 거부한다(경로 조작으로 엉뚱한 S3 key 경로를 만드는 것 방지).

## 3. `UploadSession`

```
uploadSessionId  UUID (PK)
boormiId         UUID   — 발급 요청자
purpose          UploadPurpose
resourceId       UUID?  — 이 업로드가 속한 대상(주문 등), purpose에 따라 필수/불필요
s3Key            String (unique)
status           ISSUED | CONSUMED
issuedDtm / consumedDtm
```

`purpose`별로 `resourceScopeRequired` 여부가 다르다.

| Purpose | resourceId 필요 | 용도 |
|---|---|---|
| `ORDER_ITEM_IMAGE` | 아니오 | 주문 생성 중 물품 사진 (주문 전이라 아직 orderId가 없음) |
| `DREAMI_ID_CARD` | 아니오 | 드리미 인증 신분증 사진 |
| `DREAMI_CRIMINAL_RECORD` | 아니오 | 드리미 인증 범죄이력조회서 |
| `PICKUP_CERTIFICATION_IMAGE` | 예 | 픽업 완료 인증 사진 (주문에 귀속) |
| `DELIVERY_CERTIFICATION_IMAGE` | 예 | 배달 완료 인증 사진 (주문에 귀속) |

resourceId가 필요한 purpose인데 발급 요청에 안 넘기면 `MISSING_RESOURCE_ID`로 거부한다.

## 4. 소비(consume) 동시성 처리

`markConsumedIfIssued`는 "조회 후 저장" 방식이 아니라 `UPDATE ... WHERE s3Key=:key AND status=ISSUED` 조건부 UPDATE 하나로 원자적으로 처리한다. 같은 key로 동시에 두 요청이 들어와도 DB가 한쪽만 `1`(영향받은 row 수)을 돌려주고 나머지는 `0`을 받아, 별도 락 없이도 중복 소비를 막는다. 이 부분의 상세한 배경(도입 이유, 부작용으로 발견된 버그, `clearAutomatically` 제거 이유)은 [upload-session-consume-concurrency.md](../../upload/upload-session-consume-concurrency.md)에 이미 정리되어 있다.

## 5. 소유자 바인딩 / key 재사용 방지

- presigned URL의 `key`는 발급 시점에 `boormiId`/`purpose`/`resourceId`와 함께 `UploadSession`에 저장된다. 검증 시 이 세 가지가 모두 일치해야 한다 — 하나라도 다르면 `KEY_OWNER_MISMATCH`(403).
- 이게 막는 것: 다른 사람이 발급받은 key를 알아내(추측/유출) 자기 요청에 붙여 제출하는 것, 한 용도로 발급받은 key를 다른 용도나 다른 대상(다른 주문)에 재사용하는 것.
- 처음에는 key 문자열 자체에 `boormiId`를 접두어로 넣고 `startsWith` 검사만 했는데, 이러면 purpose나 resourceId까지는 구분할 수 없었다. 지금은 `UploadSession` row 전체를 비교하는 방식으로 강화되어 있다.

## 6. 로컬/운영 분리

- `Uploader` 인터페이스: `generateUploadUrl`, `generateDownloadUrl`, `exists`.
- **`S3Uploader`**(운영, `upload.s3-enabled=true`) — AWS S3Presigner로 실제 presigned URL을 발급한다(업로드 URL 10분, 다운로드 URL 5분 만료).
- **`DevUploader`**(로컬, 기본값) — AWS 자격증명 없이 동작한다. "presigned URL"이 실제로는 이 앱 자신의 `/api/v1/upload/dev-storage` 엔드포인트를 가리키고, 파일은 `InMemoryFileStore`(메모리 맵, 재시작하면 사라짐)에 저장된다.

`upload.s3-enabled` 환경변수 하나로 전체가 전환되므로, 로컬 개발/테스트는 AWS 키 없이 그대로 돌아간다.

## 7. 다운로드 URL 실패 시 null로 degrade

`S3PresignService.resolveDownloadUrl`은 다운로드 URL 생성에 실패하면 예외를 던지는 대신 `null`을 반환한다(경고 로그만 남김). 배달 완료 내역이나 관리자 검수 화면처럼 "사진을 보여줄 수 있으면 보여주는" 화면에서, 오래돼서 지워졌거나 S3 장애로 잠깐 조회가 안 되는 사진 하나 때문에 전체 페이지가 깨지지 않게 하려는 목적이다. 반대로 업로드 완료를 검증하는 `checkUpload` 경로는 그대로 예외를 던진다 — 이쪽은 "실제로 업로드됐는지"가 중요한 검증이라 조용히 넘어가면 안 되기 때문이다.

## 8. `UploadErrorCode`

| 코드 | HTTP | 상황 |
|---|---|---|
| `NO_FILE_ATTACHED` | 400 | 발급 요청에 파일명이 비어있음 |
| `FILE_SIZE_EXCEEDED` | 413 | (선언만 돼 있고 아직 실제로 검사하는 곳은 없음 — 파일 크기 자체를 발급 요청에서 안 받음) |
| `UNSUPPORTED_FILE_TYPE` | 415 | 확장자가 png/jpg/jpeg/webp가 아님 |
| `STORAGE_UPLOAD_FAILED` | 500 | S3 HEAD 조회 자체가 실패(권한/장애 등) |
| `FILE_NOT_FOUND` | 404 | 세션이 없거나, 세션은 있는데 실제 파일이 없음 |
| `INVALID_FILE_NAME` | 404 | 파일명에 `/`, `\`, `..` 포함 |
| `KEY_OWNER_MISMATCH` | 403 | key의 purpose/boormiId/resourceId 불일치 |
| `MISSING_RESOURCE_ID` | 400 | resource-scoped purpose인데 resourceId 없음 |
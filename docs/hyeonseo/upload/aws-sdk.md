# Upload 도메인 — AWS SDK v2 레퍼런스

upload 도메인에서 사용하는 AWS SDK for Java v2 객체를 정리한다.

---

## 전체 구조 — S3Client vs S3Presigner

두 클라이언트는 역할이 다르다.

| | `S3Client` | `S3Presigner` |
|---|---|---|
| 역할 | 서버가 직접 S3와 통신 | URL에 서명만 만들어줌. 실제 통신은 클라이언트가 |
| 인증 필요 | 서버에 자격증명 필요 | 서버에 자격증명 필요(서명을 만들어야 하니까) |
| 파일 전송 | 서버 메모리를 거침 | 클라이언트가 발급받은 URL로 S3에 직접 PUT/GET |
| 이 프로젝트에서 사용 | `headObject` (존재 확인) | `presignPutObject`, `presignGetObject` (URL 발급) |

```
[클라이언트] ─── GET /upload/url ──► [서버 : S3Presigner] ──► presigned URL 반환
[클라이언트] ─── PUT presigned URL ──────────────────────────────────► [S3]
[서버 : S3Client] ─── headObject(key) ──────────────────────────────► [S3] (존재 확인)
```

---

## 빌더 패턴 (Builder Pattern)

AWS SDK v2의 모든 요청 객체는 빌더 패턴으로 생성한다.

```java
PutObjectRequest objectRequest = PutObjectRequest.builder()
        .bucket("my-bucket")
        .key("uploads/foo.png")
        .contentType("image/png")
        .build();  // build() 호출 시점에 불변 객체 완성
```

### 왜 빌더인가

- 인수가 많은 생성자(`new PutObjectRequest("bucket", "key", "type", ...)`)는 순서를 잊기 쉽고 가독성이 떨어진다.
- 빌더는 필요한 속성만 이름으로 지정하고, `build()` 시점에 필수 속성 누락을 검증한다.
- 모든 SDK 객체가 불변(immutable)이라, 한번 `build()`되면 수정할 수 없다.

---

## `HeadObjectRequest` / `s3Client.headObject()`

**역할**: S3 객체의 메타데이터(크기, Content-Type, ETag 등)만 가져온다. 파일 바디(byte[])는 전송하지 않는다.

HTTP의 `HEAD` 메서드에 대응한다(GET과 응답 헤더는 동일하지만 body가 없다).

```java
HeadObjectRequest headRequest = HeadObjectRequest.builder()
        .bucket(uploadProperties.bucketName())
        .key(key)
        .build();

s3Client.headObject(headRequest);  // 객체가 존재하면 정상 반환, 없으면 S3Exception(404) 던짐
```

### 왜 HEAD를 쓰는가

파일이 실제로 업로드됐는지만 확인하면 되므로, 파일 전체를 다운로드하는 `GetObjectRequest` 대신 메타데이터만 조회하는 HEAD를 사용한다. 네트워크 트래픽과 비용을 줄일 수 있다.

---

## `PutObjectRequest` / `PutObjectPresignRequest` / `PresignedPutObjectRequest`

클라이언트가 S3에 **업로드(PUT)**하는 데 필요한 presigned URL을 발급하는 흐름에서 사용하는 세 객체.

```
PutObjectRequest          : "어떤 버킷/키에, 어떤 Content-Type으로 PUT할 것인가" 명세
PutObjectPresignRequest   : PutObjectRequest + 서명 유효 기간
PresignedPutObjectRequest : 최종 결과. 서명이 박힌 URL을 담고 있음
```

```java
// 1. PUT 대상 명세
PutObjectRequest objectRequest = PutObjectRequest.builder()
        .bucket("my-bucket")
        .key("uploads/photo.png")
        .contentType("image/png")
        .build();

// 2. presign 요청(유효 기간 포함)
PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(10))  // 10분 내에 클라이언트가 PUT해야 함
        .putObjectRequest(objectRequest)
        .build();

// 3. 서명된 URL 생성
PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
String url = presignedRequest.url().toString();  // 클라이언트에게 반환할 URL
```

---

## `GetObjectRequest` / `GetObjectPresignRequest` / `PresignedGetObjectRequest`

클라이언트가 S3에서 **다운로드(GET)**하는 데 필요한 presigned URL을 발급하는 흐름. 구조는 PUT과 동일.

```java
GetObjectRequest objectRequest = GetObjectRequest.builder()
        .bucket("my-bucket")
        .key("uploads/photo.png")
        .build();

GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(5))  // 5분 내에 클라이언트가 GET해야 함
        .getObjectRequest(objectRequest)
        .build();

PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
String url = presignedRequest.url().toString();
```

---

## `S3Exception`

**역할**: AWS SDK가 S3 관련 오류를 표현하는 예외. `AwsServiceException` → `SdkServiceException` → `RuntimeException` 계층.

```java
} catch (S3Exception e) {
    if (e.statusCode() == 404) {
        return false;  // 객체 없음
    }
    throw new BusinessException(UploadErrorCode.STORAGE_UPLOAD_FAILED);
}
```

### 주요 메서드

| 메서드 | 설명 |
|---|---|
| `e.statusCode()` | HTTP 상태코드 (404, 403, 500 등) |
| `e.awsErrorDetails().errorCode()` | AWS 내부 에러코드 문자열 (예: `"NoSuchKey"`) |
| `e.getMessage()` | 에러 메시지 |
| `e.requestId()` | AWS 요청 추적 ID |

### `statusCode() == 404` 로 존재 여부를 판단하는 이유

`headObject`는 객체가 없으면 `S3Exception`을 던지고, 그 안에 HTTP 404가 들어있다. 404면 "업로드 안 됨"이고, 403이면 권한 문제, 500이면 S3 내부 오류다. 403/500은 다시 `BusinessException`으로 변환해 클라이언트에게 "업로드 실패" 에러를 반환한다.

---

## Provider Chain — 자격증명·리전 자동 탐색

`S3Client.create()`, `S3Presigner.create()` 는 명시적으로 자격증명을 받지 않는다. 대신 SDK가 아래 순서로 자동 탐색한다.

1. **환경변수** — `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`
2. **시스템 프로퍼티** — `aws.accessKeyId` 등
3. **AWS 자격증명 파일** — `~/.aws/credentials`
4. **ECS 컨테이너 자격증명** — ECS Task에 Role이 붙어 있을 때
5. **EC2 인스턴스 메타데이터(IMDS)** — EC2에 IAM Role이 붙어 있을 때 ← 운영 서버에서 사용

이 프로젝트에서 운영 서버(EC2)는 IAM Role을 인스턴스에 붙여 5번 경로로 자동 인증된다. 별도로 AWS 키를 코드나 환경변수에 넣을 필요가 없고, IAM Role이 만료 없이 자격증명을 자동 갱신해 준다.

로컬에서는 `upload.s3-enabled=false`(기본값)이므로 이 Provider Chain 자체가 작동하지 않는다 — `S3Config`가 `@ConditionalOnProperty`로 비활성화돼 `S3Client`/`S3Presigner` 빈이 아예 만들어지지 않기 때문이다.

---

## `Duration.ofMinutes(n)`

> `java.time.Duration` — Java 8+

AWS SDK에서 presigned URL의 유효 기간을 지정할 때 사용한다.

```java
Duration.ofMinutes(10)  // 10분
Duration.ofSeconds(300) // 300초 = 5분
Duration.ofHours(1)     // 1시간
```

- `Duration`은 시간의 양(간격)을 나타내는 불변 객체. `LocalDateTime`처럼 특정 시점이 아니라 "얼마 동안"을 표현한다.
- `ofMinutes`, `ofSeconds`, `ofHours`, `ofDays` 등 팩토리 메서드로 생성. `new Duration()`은 없다.
- 두 Duration 더하기: `duration1.plus(duration2)`, 비교: `duration1.compareTo(duration2)`

# Presigned URL 보안 전략 비교

presigned URL로 클라이언트가 S3에 직접 업로드하게 하면, 서버가 파일을 받지 않기 때문에 **"누가, 어떤 목적으로, 어떤 파일을 올렸는지"를 어떻게 검증하는가** 라는 문제가 생긴다. 이를 해결하는 대표적인 세 가지 방식을 비교한다.

---

## 1. 클라이언트 믿는 방식 (Simple Presigned URL)

### 동작 흐름

```
① 클라이언트 → 서버: presigned URL 발급 요청
② 서버 → 클라이언트: presigned URL + key 반환
③ 클라이언트 → S3: PUT (파일 업로드)
④ 클라이언트 → 서버: 기능 API 호출 (key 포함)
⑤ 서버 → S3: HEAD 요청 (파일 존재 여부만 확인)
⑥ 서버: 존재하면 통과
```

### 장점

- 구현이 단순하다. 별도 DB 테이블이나 인프라가 필요 없다.
- 빠르다. 비즈니스 API 처리 시 S3 HEAD 한 번만 추가된다.

### 단점

- **key 재사용 공격에 취약하다.** 과거에 다른 목적으로 올린 파일의 key를 그대로 다른 API에 제출해도 "S3에 파일이 있으니 통과"가 된다.
  - 예: 신분증 사진 key → 픽업 완료 검증에 제출 → 통과
- **다른 사용자의 key를 도용할 수 있다.** 타인이 업로드한 파일의 key를 알면 내 검증에 사용 가능하다.
- **중복 소비를 막을 수 없다.** 같은 key로 여러 번 기능 API를 호출해도 매번 통과된다.
- **파일 크기·내용 검증 불가.** 서버가 바이트를 받지 않으므로 크기나 악성 여부를 알 수 없다.

---

## 2. 업로드 세션 방식 (이 프로젝트)

### 동작 흐름

```
① 클라이언트 → 서버: presigned URL 발급 요청 (purpose, resourceId 포함)
② 서버: UploadSession row 생성 (status=ISSUED, purpose/boormiId/resourceId/key 저장)
③ 서버 → 클라이언트: presigned URL + key 반환
④ 클라이언트 → S3: PUT (파일 업로드)
⑤ 클라이언트 → 서버: 기능 API 호출 (key 포함)
⑥ 서버: validateScope() — 세션 row의 purpose/boormiId/resourceId 대조
⑦ 서버 → S3: HEAD 요청 (실제 파일 존재 확인)
⑧ 서버: ISSUED → CONSUMED 전이 (조건부 UPDATE)
```

### 장점

- **key 재사용 공격 차단.** purpose/boormiId/resourceId 세 가지가 모두 일치해야 통과한다.
  - 신분증 key → 픽업 완료 검증: purpose 불일치로 거부
  - 남의 key: boormiId 불일치로 거부
  - 다른 주문 key: resourceId 불일치로 거부
- **중복 소비 방지.** 조건부 UPDATE 하나로 원자적 처리. 동시 요청이 와도 딱 하나만 성공한다.
- **재시도 구분.** CONSUMED 상태가 남아있어 "이미 처리됐다"를 파악할 수 있다.
- **감사 이력.** issuedDtm / consumedDtm으로 언제 발급됐고 언제 사용됐는지 추적 가능하다.
- **추가 인프라 없음.** 순수 Spring Boot + DB만으로 구현된다. Lambda·SQS·SNS 없음.

### 단점

- **파일 크기 검증 불가.** 서버가 바이트를 받지 않으므로 크기를 알 수 없다. `FILE_SIZE_EXCEEDED` 에러코드가 선언돼 있지만 실제로 검사하는 코드가 없다.
- **파일 내용 검증 불가.** 악성 파일 여부, 실제 이미지인지 등을 확인할 수 없다.
- **UPLOAD_SESSION 테이블이 무한히 쌓인다.** 삭제 경로가 없다.
  - ISSUED 상태로 방치된 세션: presigned URL을 발급받고 업로드를 안 한 경우
  - CONSUMED 상태 세션: 처리 완료됐지만 지우지 않음
- **비즈니스 API마다 DB 조회가 추가된다.** 기능 API 호출 시마다 UploadSession 조회 + UPDATE가 발생한다.

### 개선 방향

**① 파일 크기 검증**

발급 요청 시 `fileSize`를 함께 받아 presigned URL에 크기 조건을 내장한다. 조건을 벗어난 PUT은 S3가 직접 거부한다.

```java
// 발급 요청에 fileSize 추가
public PresignedUrlResponseDto issue(..., long fileSize) {
    if (fileSize > MAX_FILE_SIZE) {
        throw new BusinessException(UploadErrorCode.FILE_SIZE_EXCEEDED);
    }
    PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(...)
            .key(key)
            .contentLength(fileSize)  // 이 크기가 아닌 PUT은 S3가 거부
            .build();
}
```

클라이언트 측에서도 파일 선택 시 1차로 체크하면 UX가 좋아진다.

**② 만료 세션 정리 배치**

`@Scheduled`로 주기적으로 오래된 세션을 정리한다. 보존 기간은 분쟁 대비 이력 요건에 따라 결정한다.

```java
@Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
@Transactional
public void cleanupExpiredSessions() {
    // presigned URL 만료(10분) 후 충분한 시간이 지난 ISSUED → 업로드 포기로 간주
    uploadSessionRepository.deleteIssuedOlderThan(LocalDateTime.now().minusHours(1));
    // 처리 완료 후 오래된 CONSUMED → 감사 이력 보존 기간 이후 삭제
    uploadSessionRepository.deleteConsumedOlderThan(LocalDateTime.now().minusDays(30));
}
```

---

## 3. S3 이벤트 + Lambda 방식

### 동작 흐름

```
① 클라이언트 → 서버: presigned URL 발급 요청
② 서버 → 클라이언트: presigned URL + key 반환
③ 클라이언트 → S3: PUT (파일 업로드)
④ S3 → Lambda: ObjectCreated 이벤트 자동 트리거
⑤ Lambda: 파일 크기·MIME 타입 검증, 악성 파일 스캔(Rekognition/GuardDuty)
⑥ Lambda: 검증 완료 → SQS/SNS로 앱 서버에 알림 OR "검증완료" 버킷으로 이동
⑦ 클라이언트 → 서버: 기능 API 호출 (key 포함)
⑦ 서버: 해당 key가 "검증완료" 상태인지 확인 후 처리
```

```
[업로드 시점]                    [검증 시점]
클라이언트 → S3 → Lambda        서버 → "검증완료 여부" 확인
                    ↓
               파일 크기 체크
               MIME 타입 확인
               악성 파일 스캔
               → 검증완료 버킷 이동 or DB 상태 기록
```

### 장점

- **파일 크기 검증 가능.** S3 이벤트에 `ContentLength`가 포함돼 Lambda가 읽을 수 있다.
- **파일 내용 검증 가능.** AWS Rekognition(이미지 분석), GuardDuty Malware Protection 등을 Lambda에서 바로 호출할 수 있다.
- **메인 서버와 분리.** 파일 검증 로직이 Lambda에 있어 서버 부하에 영향을 주지 않는다.
- **업로드 완료를 서버가 직접 감지.** 클라이언트의 "완료 신고"를 믿지 않아도 된다.

### 단점

- **인프라가 복잡해진다.** Lambda 작성·배포, S3 이벤트 설정, SQS/SNS 연결, IAM 권한 설정이 모두 필요하다.
- **비동기 처리.** 업로드 후 Lambda 실행까지 시간이 걸린다. 클라이언트가 "검증이 됐는지"를 알려면 폴링이나 SSE/WebSocket이 필요하다.
- **Lambda 콜드 스타트.** 첫 호출 시 지연이 발생할 수 있다.
- **key 재사용 공격을 자체적으로 막지 못한다.** "이 파일이 유효한가"는 검증하지만 "이 key를 이 사람이 이 목적으로 쓸 수 있는가"는 별도로 처리해야 한다. 업로드 세션 방식의 scope 검증과 결합하지 않으면 허점이 남는다.
- **비용.** Lambda 호출, S3 이벤트, SQS/SNS 모두 추가 비용이 발생한다.

---

## 세 방식 비교표

| | 클라이언트 믿는 방식 | 업로드 세션 방식 | S3 이벤트 + Lambda |
|---|---|---|---|
| key 재사용 공격 차단 | ❌ | ✅ | ❌ (별도 처리 필요) |
| 파일 크기 검증 | ❌ | △ (개선 가능) | ✅ |
| 파일 내용·악성 검증 | ❌ | ❌ | ✅ |
| 중복 소비 방지 | ❌ | ✅ | △ |
| 감사 이력 | ❌ | ✅ | △ |
| 구현 복잡도 | 낮음 | 중간 | 높음 |
| 추가 인프라 | 없음 | 없음 | Lambda·SQS·SNS |
| 동기 처리 | ✅ | ✅ | ❌ (비동기) |

---

## 이 프로젝트가 업로드 세션 방식을 선택한 이유

- 순수 Spring Boot + MySQL 스택만으로 구현 가능하다. Lambda·SQS 등 추가 인프라가 불필요하다.
- 클라이언트 믿는 방식의 핵심 취약점(key 재사용, 타인 key 도용)을 막는 데 충분하다.
- 이 서비스에서 업로드되는 파일은 드라이버 신분증·픽업 인증 사진 등 소량의 고신뢰 파일이라 악성 파일 스캔까지는 현재 요구사항에 없다.
- 파일 크기 검증(`FILE_SIZE_EXCEEDED`)은 정책이 확정되면 `contentLength` 조건으로 추가할 수 있다.

규모가 커지거나 업로드 파일 검증 요건이 강해지면(악성 파일 차단, 이미지 분석 등) Lambda 방식으로 전환하거나 두 방식을 결합하는 것이 자연스러운 다음 단계다.

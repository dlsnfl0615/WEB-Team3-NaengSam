# Upload 도메인 — 어노테이션 레퍼런스

upload 도메인 코드에 등장하는 비표준/비직관적 어노테이션을 정리한다.

---

## `@ConditionalOnProperty`

> `S3Config`, `S3Uploader`, `DevUploader`, `InMemoryFileStore`, `DevStorageController`

**역할**: 지정한 프로퍼티 키의 값이 조건과 일치할 때만 해당 빈(클래스 전체 또는 @Bean 메서드)을 컨테이너에 등록한다.

### 속성 목록

| 속성 | 타입 | 설명 |
|---|---|---|
| `name` / `value` | `String[]` | 검사할 프로퍼티 키. 둘은 동의어. 배열이므로 여러 키를 동시에 지정할 수 있음 |
| `prefix` | `String` | 모든 `name`에 공통으로 붙는 접두어. `prefix="upload", name="s3-enabled"` 는 `upload.s3-enabled`와 동일 |
| `havingValue` | `String` | 이 값과 프로퍼티가 일치할 때만 활성화. 기본값 `""` (비어있음) → 프로퍼티가 존재하고 `"false"` 가 아니면 활성화 |
| `matchIfMissing` | `boolean` | 프로퍼티 키 자체가 없을 때의 동작. `true` → 없어도 활성화(기본값으로 간주). `false`(기본값) → 없으면 비활성화 |

### 이 프로젝트에서 채용한 조합

```java
// S3Uploader, S3Config — AWS 실제 사용 시만 활성화
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "true")

// DevUploader, InMemoryFileStore, DevStorageController — 로컬 기본값
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)
```

- `matchIfMissing = true`를 `DevUploader` 쪽에 붙인 이유: application.properties에 `upload.s3-enabled` 키가 아예 없는 상태(팀원 온보딩 초기, 설정 미완성)에서도 로컬 개발이 그대로 돌아야 하기 때문. 설정 한 줄 없이도 AWS 자격증명 없이 업로드 기능 전체를 테스트할 수 있게 한다.

- `prefix` 를 쓰지 않고 `name`에 전체 키를 쓴 이유: 이 어노테이션이 붙는 클래스마다 조건 키가 `upload.s3-enabled` 하나뿐이라, prefix를 따로 빼는 것보다 `name`에 전체 경로를 적는 편이 더 읽기 쉽다.

---

## `@Bean(destroyMethod = "close")`

> `S3Config.s3Client()`, `S3Config.s3Presigner()`

**역할**: 메서드 반환 객체를 Spring 컨테이너에 싱글턴 빈으로 등록한다.

### `destroyMethod` 속성

Spring 컨테이너가 종료될 때(앱 셧다운) 해당 빈의 어떤 메서드를 자동으로 호출할지 지정한다.

```java
@Bean(destroyMethod = "close")
public S3Client s3Client() { return S3Client.create(); }
```

- `S3Client`와 `S3Presigner` 모두 `SdkAutoCloseable`(→ `Closeable` → `AutoCloseable`)을 구현하고 있어, `close()` 를 호출해야 내부 HTTP 커넥션 풀·스레드 등 리소스가 정리된다.
- 지정하지 않으면(`destroyMethod = ""`), Spring이 이름으로 메서드를 자동 추론(추론 규칙: `close` 또는 `shutdown` 메서드가 있으면 자동 호출). 명시하면 추론 과정 없이 확실하게 지정된 이름을 호출한다.

### 기타 `@Bean` 주요 속성

| 속성 | 설명 |
|---|---|
| `name` / `value` | 빈 이름 지정. 기본값은 메서드명 |
| `initMethod` | 빈 초기화 직후 호출할 메서드명 |
| `destroyMethod` | 컨테이너 종료 시 호출할 메서드명 |

---

## `@ConfigurationProperties(prefix = "...")`

> `UploadProperties`

**역할**: `application.properties`(또는 `application.yml`)의 특정 접두어 아래에 있는 키들을 이 클래스의 필드로 자동 바인딩한다.

```properties
# application.properties
upload.bucket-name=my-bucket
```

```java
@ConfigurationProperties(prefix = "upload")
public record UploadProperties(String bucketName) {}
// → bucketName 필드에 "my-bucket" 자동 바인딩
```

- 프로퍼티 키는 `kebab-case`(`bucket-name`)이고 Java 필드는 `camelCase`(`bucketName`)여도 Spring이 자동으로 맞춰준다(Relaxed Binding).
- `@Value("${upload.bucket-name}")` 과의 차이: `@Value`는 필드 하나에 프로퍼티 하나를 주입하지만, `@ConfigurationProperties`는 접두어 아래 키들을 클래스 전체로 묶어 타입 안전하게 바인딩한다. 설정 항목이 여러 개일 때 유리하다.
- `record`와 함께 쓰면 불변 설정 객체가 된다(record 컴포넌트가 생성자 파라미터로 바인딩됨).
- `@EnableConfigurationProperties(UploadProperties.class)` 또는 메인 클래스에 `@ConfigurationPropertiesScan`이 있어야 실제로 스캔된다(Spring Boot는 보통 자동 처리).

---

## `@Transactional` / `@Transactional(readOnly = true)`

> `UploadSessionService`

**역할**: 메서드 실행을 DB 트랜잭션 안에서 처리한다.

```java
@Transactional           // 읽기/쓰기 트랜잭션
public boolean consume(String key) { ... }

@Transactional(readOnly = true)  // 읽기 전용 트랜잭션
public void validateScope(...) { ... }
```

### `@Transactional` 주요 속성

| 속성 | 기본값 | 설명 |
|---|---|---|
| `readOnly` | `false` | `true`로 설정하면 Hibernate가 flush를 생략 → 더티 체킹·쓰기 잠금 없음. 성능 최적화 |
| `propagation` | `REQUIRED` | 트랜잭션 전파 방식. `REQUIRED`: 이미 트랜잭션이 있으면 참여, 없으면 새로 시작 |
| `isolation` | DB 기본값 | 트랜잭션 격리 수준(READ_COMMITTED, REPEATABLE_READ 등) |
| `rollbackFor` | `RuntimeException` + `Error` | 이 예외 발생 시 롤백. `BusinessException`은 RuntimeException이라 별도 지정 불필요 |

- `@Transactional`이 없는 메서드에서 JPA 저장/수정을 시도하면 `TransactionRequiredException` 발생.
- `readOnly = true`를 조회 메서드에 붙이는 이유: Hibernate 더티 체킹(변경 감지)을 비활성화해 스냅샷 비교 오버헤드를 줄이고, DB 커넥션 풀 등 여러 레이어에서 최적화 힌트로 사용된다.

---

## `@Modifying`

> `UploadSessionRepository.markConsumedIfIssued`

**역할**: `@Query`로 작성한 JPQL/네이티브 쿼리가 SELECT가 아닌 UPDATE/DELETE/INSERT임을 Spring Data에 알린다.

```java
@Modifying
@Query("UPDATE UploadSession s SET s.status = ... WHERE ...")
int markConsumedIfIssued(@Param("s3Key") String s3Key);
```

- 없으면 Spring Data가 SELECT 쿼리로 간주해 `InvalidDataAccessApiUsageException` 또는 예상과 다른 동작이 발생.
- 반환 타입 `int`: 영향받은 row 수. 이 값으로 실제로 상태가 전이됐는지 판단.

### `clearAutomatically` 속성 — 이 프로젝트에서 쓰지 않는 이유

`@Modifying(clearAutomatically = true)`를 붙이면 벌크 UPDATE 직후 영속성 컨텍스트 **전체**를 비운다(`entityManager.clear()`). 이는 업데이트된 엔티티의 stale 캐시를 막으려는 의도지만, **"이 도메인의 엔티티만"이 아니라 그 시점에 managed 상태인 모든 엔티티를 detach**시킨다는 부작용이 있다.

실제로 `clearAutomatically = true`를 붙였다가 `Delivery` 엔티티 상태 전이가 DB에 반영되지 않는 버그가 발생했다. `consume()` 은 `DeliveryService` 트랜잭션 안에서도 호출되기 때문에, `UploadSession` 벌크 UPDATE 직후 `clear()`가 완전히 무관한 `Delivery` 엔티티를 detach시켜 dirty checking에서 빠져버린 것이다. 자세한 사고 경위는 [overview.md](./overview.md) 4절 참고.

---

## `@JdbcTypeCode(SqlTypes.BINARY)`

> `UploadSession.uploadSessionId`, `boormiId`, `resourceId`

**역할**: UUID 필드를 DB에 `BINARY(16)` 타입으로 저장하도록 Hibernate에 지시한다.

```java
@Id
@JdbcTypeCode(SqlTypes.BINARY)
@Column(name = "upload_session_id", columnDefinition = "BINARY(16)")
private UUID uploadSessionId;
```

- UUID를 문자열(`VARCHAR(36)`, 예: `"550e8400-e29b-41d4-a716-446655440000"`)로 저장하면 36바이트지만, 바이너리(`BINARY(16)`)로 저장하면 16바이트. 인덱스 크기·비교 속도에서 유리하다.
- `@Column(columnDefinition = "BINARY(16)")`: DDL 생성 시 컬럼 타입을 직접 지정. `@JdbcTypeCode` 만으로는 기존 DDL을 바꾸지 않으므로 함께 쓴다.

---

## `@Enumerated(EnumType.STRING)`

> `UploadSession.purpose`, `status`

**역할**: JPA가 enum 필드를 DB에 저장할 때 사용할 방식을 지정한다.

```java
@Enumerated(EnumType.STRING)  // "ISSUED", "CONSUMED" 등 문자열로 저장
@Enumerated(EnumType.ORDINAL) // 0, 1, 2 ... 순서(정수)로 저장
```

| 방식 | DB 저장값 | 문제점 |
|---|---|---|
| `STRING` | `"ISSUED"`, `"CONSUMED"` | 문자열이라 컬럼 길이 필요. 이름 변경 시 DB 마이그레이션 필요 |
| `ORDINAL` | `0`, `1` | enum 상수 순서를 바꾸거나 중간에 추가하면 기존 데이터 의미가 깨짐 |

`STRING`을 권장하는 이유: `ORDINAL`은 enum 파일의 상수 선언 순서에 의존하기 때문에, 코드 리팩토링(상수 추가·순서 변경)이 DB 데이터를 조용히 망가뜨릴 수 있다.

---

## `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

> `UploadSession`

**역할**: Lombok이 접근 제한자가 `protected`인 기본 생성자를 자동 생성한다.

- JPA는 리플렉션으로 엔티티 객체를 만들기 위해 기본 생성자(인수 없는 생성자)가 **반드시** 필요하다.
- `public`으로 열면 코드 어디서든 `new UploadSession()`으로 빈 객체를 만들 수 있어, 정적 팩토리(`create()`)만 통해야 한다는 의도가 깨진다.
- `protected`로 제한하면 JPA 내부(같은 패키지·상속)는 허용되고, 외부에서의 `new UploadSession()` 직접 호출은 컴파일 오류가 된다.

---

## `@RequestParam(required = false)`

> `UploadController.getPresignedUrl`의 `resourceId`

**역할**: HTTP 쿼리 파라미터가 요청에 없어도 허용한다. 없으면 `null`(또는 지정한 `defaultValue`)이 주입된다.

```java
@RequestParam(required = false) UUID resourceId
```

기본값(`required = true`)이면 파라미터가 없을 때 `MissingServletRequestParameterException`(400)이 발생한다. `resourceId`는 `UploadPurpose`에 따라 있을 수도 없을 수도 있으므로 `required = false`로 선언했다.

### 관련 속성

| 속성 | 기본값 | 설명 |
|---|---|---|
| `value` / `name` | 파라미터명 | 파라미터 이름. 생략 시 메서드 파라미터명과 동일 |
| `required` | `true` | 필수 여부 |
| `defaultValue` | 없음 | 파라미터가 없을 때 사용할 기본값 문자열 |

---

## `@RequestHeader`

> `DevStorageController.put`

**역할**: HTTP 요청의 특정 헤더값을 메서드 파라미터로 받는다.

```java
@RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType
```

- `HttpHeaders.CONTENT_TYPE`: `"Content-Type"` 문자열 상수. 하드코딩 대신 상수를 쓰면 오타를 컴파일 시점에 잡을 수 있다.
- `required = false`: `Content-Type` 헤더가 없는 요청도 허용. 없으면 `null`이 들어와 `MediaType.APPLICATION_OCTET_STREAM_VALUE`로 대체한다.

---

## `@ApiErrorCodes` (커스텀)

> `UploadController.getPresignedUrl`

**역할**: Swagger 문서에 이 엔드포인트가 반환할 수 있는 에러 케이스를 열거한다. `@Repeatable` 어노테이션이므로 같은 메서드에 여러 번 붙일 수 있다.

```java
@ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
@ApiErrorCodes(enumClass = UploadErrorCode.class, codes = {"NO_FILE_ATTACHED", "UNSUPPORTED_FILE_TYPE"})
```

- `enumClass`: 에러코드 enum 타입 지정.
- `codes`: 이 enum에서 실제로 발생 가능한 상수 이름 목록. enum 전체가 아니라 관련 있는 것만 명시한다.

### Lombok의 `@Getter` / `@RequiredArgsConstructor` on enum

`UploadPurpose`, `UploadErrorCode`처럼 enum에도 Lombok을 적용할 수 있다.

```java
@Getter
@RequiredArgsConstructor
public enum UploadPurpose {
    ORDER_ITEM_IMAGE(false);
    private final boolean resourceScopeRequired;
}
```

- Java enum은 일반 클래스처럼 **생성자**와 **필드**를 가질 수 있다. 상수 뒤 괄호(`ORDER_ITEM_IMAGE(false)`)는 그 생성자를 호출하는 것이다.
- `@RequiredArgsConstructor`: `final` 필드를 받는 생성자를 자동 생성. enum 생성자는 항상 `private`이므로 접근 제한자 옵션이 의미 없다.
- `@Getter`: 필드의 getter(`isResourceScopeRequired()`)를 자동 생성.

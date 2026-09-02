# Upload 도메인 — Java 표준 패턴 레퍼런스

upload 도메인 코드에 등장하는 Java 표준 라이브러리·언어 패턴을 정리한다.

---

## `Optional`

> `S3PresignService.resolveContentType`, `InMemoryFileStore.find`, `UploadSessionService.findByKey`

**역할**: null이 될 수 있는 값을 감싸는 컨테이너. null을 직접 반환하거나 null 체크(`if (x == null)`)를 하는 대신, 비어있을 수 있다는 의도를 타입으로 표현한다.

### 주요 팩토리 메서드

```java
Optional.of(value)          // value가 null이면 NullPointerException
Optional.ofNullable(value)  // value가 null이면 빈 Optional
Optional.empty()            // 빈 Optional
```

### 주요 단말 연산

```java
optional.orElseThrow(() -> new BusinessException(...))  // 비어있으면 람다로 예외 생성
optional.orElse(defaultValue)                           // 비어있으면 기본값 반환
optional.orElseGet(() -> computeDefault())              // 비어있으면 람다 실행 결과 반환(지연 평가)
optional.isPresent()                                    // 값이 있으면 true
optional.isEmpty()                                      // 비어있으면 true (Java 11+)
```

### 이 프로젝트 예시

```java
// CONTENT_TYPES_BY_EXTENSION.get(extension)은 없는 확장자면 null 반환
return Optional.ofNullable(CONTENT_TYPES_BY_EXTENSION.get(extension))
        .orElseThrow(() -> new BusinessException(UploadErrorCode.UNSUPPORTED_FILE_TYPE));
```

`() -> new BusinessException(...)` 부분이 람다다. `Optional`이 비어있을 때만 실행된다(`Supplier<T>` 함수형 인터페이스).

---

## `Map.of(...)` — 불변 Map

> `S3PresignService.CONTENT_TYPES_BY_EXTENSION`

**역할**: Java 9+에서 도입된 불변 Map 생성 팩토리.

```java
private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "webp", "image/webp"
);
```

### 특징

- **불변**: 생성 이후 `put()`, `remove()`, `clear()` 등 수정 메서드를 호출하면 `UnsupportedOperationException`.
- **null 금지**: key나 value에 null을 넣으면 `NullPointerException`.
- **최대 10쌍**: `Map.of()`는 최대 10개의 key-value 쌍을 받는다. 11개 이상이면 `Map.ofEntries(Map.entry("k", "v"), ...)` 사용.
- **순서 미보장**: 삽입 순서를 보장하지 않는다(HashMap과 동일).

### `HashMap` 과의 차이

| | `Map.of()` | `new HashMap<>()` |
|---|---|---|
| 수정 | 불가 | 가능 |
| null key/value | 불가 | 가능 |
| 용도 | 상수, 설정값 | 런타임에 동적으로 내용이 바뀌는 경우 |

`CONTENT_TYPES_BY_EXTENSION` 처럼 초기화 후 절대 바뀌지 않는 매핑은 `Map.of()`로 불변으로 만들면 실수로 수정되는 것을 컴파일/런타임에 방지할 수 있다.

---

## `ConcurrentHashMap`

> `InMemoryFileStore.filesByKey`

**역할**: 멀티스레드 환경에서 안전한 HashMap.

```java
private final Map<String, StoredFile> filesByKey = new ConcurrentHashMap<>();
```

### 왜 `HashMap` 대신 `ConcurrentHashMap`인가

Spring은 기본적으로 하나의 빈 인스턴스를 여러 스레드가 공유한다(싱글턴 스코프). `InMemoryFileStore`는 빈이므로 동시에 여러 요청이 `filesByKey.put()` / `filesByKey.get()` 을 호출할 수 있다.

`HashMap`은 스레드 안전하지 않아 동시 수정 시 내부 구조가 깨지거나 무한 루프가 발생할 수 있다. `ConcurrentHashMap`은 내부적으로 세그먼트 락(segment locking) 또는 CAS(Compare-And-Swap)를 사용해 동시 접근을 안전하게 처리한다.

### `Hashtable`, `Collections.synchronizedMap()` 과의 차이

- `Hashtable`: 모든 메서드에 `synchronized` → 전체 맵 락. 성능 저하.
- `synchronizedMap()`: 래퍼. 마찬가지로 전체 락.
- `ConcurrentHashMap`: 부분 락(쓰기 충돌 구간만 잠금) → 읽기/다른 영역 쓰기 동시 가능. **현대 Java에서 권장**.

---

## `List.of(...)` — 불변 List

> `InMemoryFileStore.inMemoryStructures`

**역할**: Java 9+에서 도입된 불변 List 생성 팩토리. `Map.of()` 와 동일한 성격.

```java
return List.of(InMemoryStructureDto.ofMap(...));
```

- 수정 불가(`add()`, `remove()` → `UnsupportedOperationException`).
- null 요소 금지.
- `new ArrayList<>()`는 수정 가능한 빈 리스트 생성. 내용을 동적으로 추가/삭제해야 할 때 사용.

---

## Stream — `mapToLong().sum()`

> `InMemoryFileStore.inMemoryStructures`

**역할**: 컬렉션의 각 요소를 변환(map)하고 합산(sum)한다.

```java
long totalBytes = filesByKey.values().stream()    // Collection → Stream<StoredFile>
        .mapToLong(file -> file.bytes().length)   // Stream<StoredFile> → LongStream
        .sum();                                   // LongStream → long (합산)
```

### `mapToLong` vs `map`

- `map(f)`: `Stream<T> → Stream<R>`. 참조 타입 스트림.
- `mapToLong(f)`: `Stream<T> → LongStream`. 기본형(primitive) 스트림. 박싱/언박싱 오버헤드 없이 `sum()`, `average()`, `max()`, `min()` 단말 연산 제공.
- `mapToInt` / `mapToDouble` 도 동일 구조.

### 람다 `file -> file.bytes().length`

- `file`: 스트림의 각 요소(`StoredFile`). 이름은 임의로 지정.
- `file.bytes()`: `StoredFile` record의 컴포넌트 접근자. `getBytes()` 가 아니라 `bytes()`.
- `.length`: byte 배열의 길이 필드(메서드 아님).

---

## `record` — 불변 데이터 클래스

> `PresignedUrlResponseDto`, `UploadProperties`, `InMemoryFileStore.StoredFile`

**역할**: Java 14+(preview), 16+(정식). 불변 데이터를 담는 클래스를 선언할 때 생성자·getter·`equals`·`hashCode`·`toString`을 자동 생성한다.

```java
public record PresignedUrlResponseDto(String url, String key) {}
```

위 한 줄이 아래와 동일하다:

```java
public final class PresignedUrlResponseDto {
    private final String url;
    private final String key;
    public PresignedUrlResponseDto(String url, String key) { this.url = url; this.key = key; }
    public String url() { return url; }    // getter: get 없이 필드명()
    public String key() { return key; }
    // equals, hashCode, toString 자동 생성
}
```

### 주의사항

- **불변**: 필드를 바꿀 setter가 없다. 생성 후 수정 불가.
- **getter 이름**: 일반 클래스의 `getUrl()` 대신 `url()`. 접근자 스타일이 다름.
- **상속 불가**: `record`는 암묵적으로 `final`. 다른 클래스를 `extends` 할 수 없음.
- **중첩 record**: 클래스 안에 선언할 수 있다(`InMemoryFileStore.StoredFile`). 내부적으로 static으로 취급.

---

## `Objects.equals(a, b)` — null-safe equals

> `UploadSession.matches`

**역할**: 두 객체를 비교하되, null을 안전하게 처리한다.

```java
Objects.equals(resourceId, expectedResourceId)
```

동작:

| `a` | `b` | 결과 |
|---|---|---|
| `null` | `null` | `true` |
| `null` | `"foo"` | `false` |
| `"foo"` | `null` | `false` |
| `"foo"` | `"foo"` | `true` (내부적으로 `a.equals(b)`) |

`resourceId`는 nullable이므로 `resourceId.equals(expectedResourceId)` 대신 `Objects.equals`를 쓰지 않으면 `resourceId`가 `null`일 때 `NullPointerException`이 발생한다.

---

## `String.isBlank()` vs `isEmpty()`

> `UploadController.validateFileName`

```java
fileName.isBlank()   // Java 11+. " " (공백만) → true, "" → true
fileName.isEmpty()   // Java 1+. "" → true, " " → false
```

- `isBlank()`: 문자열이 비어있거나 공백 문자(스페이스, 탭, 개행 등)만 있으면 `true`.
- `isEmpty()`: 길이가 0일 때만 `true`. `" ".isEmpty()` → `false`.

입력 검증에서는 보통 `isBlank()`를 쓴다. "   " 같이 공백만 입력해도 빈 입력으로 처리해야 하는 경우가 대부분이기 때문이다.

---

## 텍스트 블록 (`"""..."""`)

> `UploadSessionRepository.markConsumedIfIssued`

**역할**: Java 15+에서 정식 도입된 여러 줄 문자열 리터럴. 들여쓰기 제거와 개행을 자동으로 처리한다.

```java
@Query("""
        UPDATE UploadSession s
        SET s.status = ...
        WHERE s.s3Key = :s3Key
        """)
```

- 여는 `"""` 뒤에는 반드시 개행이 와야 한다.
- 닫는 `"""`의 들여쓰기 위치가 문자열 내용의 기준 들여쓰기를 결정한다. 위 예에서는 닫는 `"""`가 8칸 들여쓰여 있으므로 모든 줄의 앞 8칸이 제거된다.
- 기존 방식(`"UPDATE ...\n" + "SET ...\n"`) 대비 가독성이 크게 좋아진다.

---

## `URLEncoder.encode(key, StandardCharsets.UTF_8)`

> `DevUploader.devStorageUrl`

**역할**: 문자열을 URL 쿼리 파라미터로 안전하게 인코딩한다. 슬래시(`/`), 공백, 한글 등 URL에서 특수한 의미를 가지는 문자를 `%XX` 형식으로 변환한다.

```java
URLEncoder.encode("uploads/ORDER/uuid-file.png", StandardCharsets.UTF_8)
// → "uploads%2FORDER%2Fuuid-file.png"
```

- `key`에는 `/`가 포함돼 있어(예: `"uploads/ORDER_ITEM_IMAGE/uuid-filename.png"`), 인코딩하지 않으면 URL의 경로 구분자로 잘못 해석될 수 있다.
- `StandardCharsets.UTF_8`: 문자셋 상수. 문자열(`"UTF-8"`)을 직접 쓰는 것보다 타입 안전하고 인코딩 이름 오타를 방지한다.

---

## `ServletUriComponentsBuilder.fromCurrentContextPath()`

> `DevUploader.devStorageUrl`

**역할**: 현재 HTTP 요청의 scheme + host + port + context path를 추출해 기반 URL을 만든다.

```java
String baseUrl = ServletUriComponentsBuilder
        .fromCurrentContextPath()  // 현재 요청에서 컨텍스트 정보 추출
        .build()
        .toUriString();
// 결과 예: "http://localhost:8080"
```

- `fromCurrentContextPath()`: `RequestContextHolder`(현재 스레드의 요청 컨텍스트)에서 호스트·포트·컨텍스트 경로를 읽는다. 요청 처리 스레드 밖에서 호출하면 `IllegalStateException`.
- 하드코딩(`"http://localhost:8080"`) 대신 이 방식을 쓰는 이유: 개발자마다 포트가 다를 수 있고, 배포 환경에서도 동적으로 올바른 origin을 사용하기 위해.

---

## `@Param` + JPQL 이름 바인딩

> `UploadSessionRepository.markConsumedIfIssued`

**역할**: JPQL 쿼리의 `:s3Key` 플레이스홀더와 메서드 파라미터 `String s3Key`를 이름으로 연결한다.

```java
@Query("... WHERE s.s3Key = :s3Key ...")
int markConsumedIfIssued(@Param("s3Key") String s3Key);
```

- `:s3Key`: JPQL 이름 기반 파라미터. `?1`, `?2`처럼 위치 기반 대신 이름으로 지정해 가독성이 좋다.
- `@Param("s3Key")`: 메서드 파라미터 이름을 `:s3Key`에 매핑. 컴파일 최적화에 의해 파라미터 이름 정보가 제거되는 경우를 대비해 명시한다.

### JpaRepository 파생 쿼리

```java
Optional<UploadSession> findByS3Key(String s3Key);
```

`@Query` 없이 메서드 이름만으로 SQL이 자동 생성된다. `findBy[필드명]`이 기본 형식이다.

| 메서드명 패턴 | 생성 SQL |
|---|---|
| `findByS3Key(k)` | `WHERE s3_key = :k` |
| `findByBoormiIdAndStatus(id, st)` | `WHERE boormi_id = :id AND status = :st` |
| `existsByEmail(e)` | `SELECT COUNT(*) > 0 WHERE email = :e` |
| `deleteByStatus(st)` | `DELETE WHERE status = :st` |

`JpaRepository<UploadSession, UUID>`: 제네릭 `<엔티티 타입, PK 타입>`. PK 타입이 맞지 않으면 `findById()`에서 컴파일 오류.

---

## `ResponseEntity<T>`

> `DevStorageController.get`

**역할**: HTTP 응답의 상태코드·헤더·바디를 직접 제어할 때 사용하는 Spring 타입.

```java
return ResponseEntity.ok()                            // 200 OK 상태코드 빌더 시작
        .header(HttpHeaders.CONTENT_TYPE, "image/png")  // 응답 헤더 추가
        .body(storedFile.bytes());                       // 바디 설정 + ResponseEntity 완성
```

- 일반적으로 컨트롤러는 순수 DTO를 반환하면 `CommonResponseAdvice`가 자동으로 `{ isSuccess, code, message, result }` 형태로 래핑해준다.
- `DevStorageController.get`은 바이너리 파일 바이트를 그대로 내려야 하므로(S3처럼 동작해야 하므로) `ResponseEntity<byte[]>`로 직접 응답을 구성한다. `CommonResponseAdvice`는 `ResponseEntity`를 반환하는 메서드는 래핑하지 않는다.

### 자주 쓰는 팩토리

```java
ResponseEntity.ok(body)                 // 200 + 바디
ResponseEntity.noContent().build()      // 204 No Content
ResponseEntity.notFound().build()       // 404
ResponseEntity.status(HttpStatus.CREATED).body(dto)  // 201 + 바디
```

---

## `Locale.ROOT`

> `S3PresignService.resolveContentType`

**역할**: 언어·지역 독립적인 기본 로케일. 대소문자 변환에서 터키어 등 특수 로케일의 오동작을 방지한다.

```java
String extension = key.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
```

**문제**: 터키어에서 `"I".toLowerCase()` → `"ı"` (점 없는 소문자 i). 시스템 기본 로케일이 터키어로 설정된 환경에서 `"PNG".toLowerCase()` 가 `"png"` 가 아닌 다른 값이 될 수 있다.

**해결**: `Locale.ROOT`를 명시하면 언어 규칙 없이 단순 ASCII 변환만 수행해 어떤 환경에서도 동일한 결과가 보장된다. 파일 확장자처럼 언어와 무관한 식별자를 다룰 때 `Locale.ROOT`를 쓰는 것이 관례다.

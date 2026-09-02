# Dreami 도메인 — Java 패턴 레퍼런스

dreami 도메인 코드([overview.md](overview.md)가 비즈니스 로직을 다룬다면, 이 문서는 **문법/언어 패턴**을 다룹니다)에 등장하는,
다른 언어는 써봤지만 자바는 처음인 사람이 "이게 뭐지?" 할 만한 부분을 모아 정리합니다. 실제 소스에도 짧은 한 줄 주석으로 표시해 뒀습니다.

---

## 1. `Optional` — null이 될 수도 있는 값

> `DreamiService`, `DreamiActivationChecker`, `DreamiRepository`

리포지토리 조회는 결과가 없을 수 있는데, 이때 `null`을 직접 돌려주는 대신 `Optional<T>`로 감싸서 돌려줍니다. "없을 수도 있다"는 사실이 타입에 드러나므로, 호출하는 쪽이 null 체크를 깜빡할 수가 없습니다.

```java
// DreamiRepository
Optional<Dreami> findByDreamiId(UUID dreamiId);
```

### 자주 쓰는 형태

```java
// 값이 없으면 예외를 던진다 — () -> new BusinessException(...) 은 "인자 없이 예외 객체를 만드는" 람다
Dreami dreami = dreamiRepository.findById(dreamiId)
        .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));

// 값이 있을 때만 그 값으로 어떤 동작을 실행한다(없으면 아무 일도 안 일어남)
dreamiRepository.findByDreamiId(dreamiId).ifPresent(this::throwIfApproved);

// 값이 있으면 변환하고, 없으면 기본값을 쓴다
boolean activated = boormiRepository.findById(userId)
        .map(boormi -> boormi.isDreamiActivate())
        .orElse(false);

// 값이 있으면 변환하고, 없으면 null(원래도 "없음"이 정상 상태인 API 응답용)
return orderRepository.findByDreamiIdAndOrderCd(dreamiId, OrderCd.IN_PROGRESS)
        .map(OrderSummaryDto::from)
        .orElse(null);
```

| 메서드 | 값이 있을 때 | 값이 없을 때 |
|---|---|---|
| `orElseThrow(supplier)` | 값을 꺼냄 | supplier 람다 실행 결과(예외)를 던짐 |
| `ifPresent(consumer)` | consumer 람다에 값을 넘겨 실행 | 아무 것도 안 함 |
| `map(function)` | function으로 변환한 새 `Optional` | 빈 `Optional` 그대로 |
| `orElse(default)` | 값을 꺼냄 | default 반환 |

---

## 2. 메서드 참조(Method Reference) — 람다를 더 줄여 쓰는 문법

> `DreamiService` 전반

람다 `x -> 어떤메서드(x)`가 "인자를 그대로 다른 메서드에 넘기기만" 하는 모양일 때, `클래스명::메서드명` 또는 `객체::메서드명`으로 더 짧게 쓸 수 있습니다. 의미는 완전히 같습니다.

```java
dreamiRepository.findByDreamiId(dreamiId).ifPresent(this::throwIfApproved);
// 풀어쓰면: .ifPresent(dreami -> this.throwIfApproved(dreami));

.map(OrderSummaryDto::from)
// 풀어쓰면: .map(order -> OrderSummaryDto.from(order));

.map(NearbyOrderDto::orderId)
// 풀어쓰면: .map(nearby -> nearby.orderId());

IntStream.rangeClosed(0, 5).mapToObj(thisMonth::minusMonths)
// 풀어쓰면: .mapToObj(n -> thisMonth.minusMonths(n));
```

| 문법 | 의미 |
|---|---|
| `타입::정적메서드` | `x -> 타입.정적메서드(x)` |
| `타입::인스턴스메서드` | `x -> x.인스턴스메서드()` |
| `객체::인스턴스메서드` | `x -> 객체.인스턴스메서드(x)` |

---

## 3. Stream API — 컬렉션을 파이프라인으로 가공

> `DreamiService.findNearbyCalls`, `getDashboard`, `getTodayStats`, `listPendingReviews`

리스트를 `for`문으로 순회하며 새 리스트를 만드는 대신, `.stream()`으로 시작해 `filter`(거르기) / `map`(변환) / `collect`(모으기) 를 체이닝합니다.

```java
// findNearbyCalls: 결과가 없는 주문은 걸러내고(filter), 나머지는 DTO로 변환한다(map)
return nearbyOrders.stream()
        .filter(nearby -> ordersById.containsKey(nearby.orderId()))
        .map(nearby -> NearbyCallDto.from(nearby, ordersById.get(nearby.orderId())))
        .toList(); // 최종적으로 List<NearbyCallDto>로 모음(Java 16+)
```

### `Collectors.toMap` — 리스트를 Map으로

id로 빠르게 찾기 위해, 리스트를 "id → 객체" Map으로 바꾸는 패턴이 여러 곳에 등장합니다.

```java
// findNearbyCalls: orderId를 키로, 주문 객체 자체를 값으로 쓰는 Map을 만든다
Map<UUID, NearbyCallOrderDto> ordersById = orderRepository
        .findNearbyCallOrders(...)
        .stream()
        .collect(Collectors.toMap(NearbyCallOrderDto::orderId, order -> order));
```

`Collectors.toMap(키를 뽑는 함수, 값을 뽑는 함수)` — 여기서는 값을 뽑는 함수가 `order -> order`(원소 그대로)입니다.

### 기본형 스트림 — `IntStream`, `mapToLong`

박싱(`Integer`, `Long` 객체로 감싸기) 오버헤드 없이 숫자를 다루는 전용 스트림입니다.

```java
// getDashboard: 0,1,2,3,4,5 를 만들고, 각각을 "이번 달에서 n개월 전"으로 바꾼 뒤 정렬
List<MonthlyRevenueDto> recentSixMonths = IntStream.rangeClosed(0, 5)
        .mapToObj(thisMonth::minusMonths) // int -> 객체로 바꾸는 것이므로 mapToObj
        .sorted()
        .map(month -> new MonthlyRevenueDto(month, amountOf(byMonth, month)))
        .toList();

// getTodayStats: 각 집계 객체에서 금액(long)만 뽑아 전부 더한다
long todayRevenue = moneyTxRepository.aggregateByBoormiIdAndTypeBetween(...)
        .stream()
        .mapToLong(MonthlyMoneyAggregate::totalAmount)
        .sum();
```

| 메서드 | 용도 |
|---|---|
| `IntStream.rangeClosed(a, b)` | a부터 b까지(양끝 포함) 정수 스트림 생성 |
| `.mapToObj(f)` | 정수 → 객체로 변환(`IntStream` → `Stream<T>`) |
| `.mapToLong(f)` | 객체 → long으로 변환(`Stream<T>` → `LongStream`), `.sum()` 등을 바로 쓸 수 있음 |

---

## 4. `record` — 불변 데이터 클래스

> `DreamiAuthRequestDto`, `DreamiOnlineRequest`, `DreamiProfileDto`, `NearbyCallDto` 등 `dto` 패키지 전부

이 도메인의 모든 DTO는 `record`로 선언돼 있습니다. 필드 목록만 적으면 생성자·getter(`필드명()` 형태)·`equals`/`hashCode`/`toString`이 자동으로 생깁니다. 한 번 만들면 값을 못 바꾸는(불변) 클래스라, "요청/응답처럼 값을 옮겨 담기만 하는 객체"에 잘 맞습니다.

```java
public record DreamiTodayStatsDto(long todayRevenue, long todayCompletedCount) {
    public static DreamiTodayStatsDto of(long todayRevenue, long todayCompletedCount) {
        return new DreamiTodayStatsDto(todayRevenue, todayCompletedCount);
    }
}
```

- getter가 `getTodayRevenue()`가 아니라 `todayRevenue()`입니다(레코드 접근자 문법).
- `of(...)` / `from(...)` 은 이 프로젝트의 컨벤션으로 붙인 **정적 팩토리 메서드**일 뿐, `record` 자체 기능은 아닙니다(가독성을 위해 `new DreamiTodayStatsDto(...)` 대신 씁니다).
- setter가 없으므로 필드를 바꾸려면 새 레코드를 다시 만들어야 합니다.

---

## 5. Lombok — 반복 코드를 컴파일 시점에 자동 생성

> 거의 모든 엔티티/서비스/컨트롤러 클래스

Lombok은 JDK가 아니라 별도 라이브러리입니다. `@애노테이션`을 클래스에 붙이면, 컴파일할 때 생성자·getter 같은 반복 코드를 자동으로 만들어 넣어줍니다(직접 타이핑한 코드는 없지만 실제로는 존재합니다).

| Annotation | 하는 일 | 쓰인 곳 |
|---|---|---|
| `@Getter` | 모든 필드의 `getXxx()` 자동 생성 | `Dreami`, `DreamiRequestDeniedDetails` |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | 파라미터 없는 생성자를 **protected**로 생성 | 엔티티들 — JPA는 기본 생성자가 필요하지만, 외부에서 `new Dreami()`로 막 만드는 걸 막기 위해 `protected`로 잠그고 정적 팩토리(`create(...)`)만 쓰게 강제 |
| `@RequiredArgsConstructor` | `final` 필드를 매개변수로 받는 생성자 자동 생성 | 서비스/컨트롤러 — Spring이 그 생성자로 의존성을 주입(생성자 주입) |
| `@Slf4j` | `log.info(...)` 등을 쓸 수 있는 로거 필드 자동 생성 | `DreamiAuthController` |

---

## 6. JPA/Hibernate annotation — 엔티티를 DB 테이블에 매핑

> `Dreami`, `DreamiRequestDeniedDetails`

JPA(자바 표준)와 Hibernate(그 구현체) annotation으로 클래스 필드를 DB 컬럼에 연결합니다.

```java
@Entity                          // 이 클래스가 DB 테이블과 매핑됨
@Table(name = "DREAMI")          // 매핑될 테이블 이름
public class Dreami {
    @Id                                              // 기본키(PK)
    @JdbcTypeCode(SqlTypes.BINARY)                   // UUID를 BINARY(16)로 저장(Hibernate 전용 지시)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)")
    private UUID dreamiId;

    @Enumerated(EnumType.STRING)   // enum을 숫자(ordinal)가 아니라 "REQUESTED" 같은 문자열로 저장
    @Column(name = "request_cd", nullable = false)
    private DreamiCd requestCd;
}
```

`UUID`를 `@JdbcTypeCode(SqlTypes.BINARY)` 없이 그냥 저장하면 DB 드라이버/방언에 따라 문자열(36자)로 저장될 수도 있는데, 이 프로젝트는 명시적으로 16바이트 이진값으로 저장하도록 고정해 둔 것입니다.

### 락(`@Lock`)

```java
// DreamiRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Dreami> findByDreamiId(UUID dreamiId);
```

이 메서드로 조회한 행에는 DB 차원의 잠금(row lock)이 걸려서, 같은 트랜잭션이 끝날 때까지 다른 트랜잭션이 같은 행을 건드리지 못합니다. "조회 → 조건 확인 → 저장"(check-then-act) 사이에 다른 요청이 끼어드는 경쟁 상태(race condition)를 막기 위한 것으로, `overview.md` 2절에 실제로 이 락이 막아준 동시성 버그가 정리돼 있습니다.

---

## 7. Spring 트랜잭션/이벤트

> `DreamiService` 전 메서드

```java
@Transactional              // 메서드 전체를 하나의 DB 트랜잭션으로 묶음(중간 실패 시 전부 롤백)
@Transactional(readOnly = true)  // 조회 전용 — Hibernate가 변경 감지(dirty checking)를 생략해 더 가볍게 동작
```

`ApplicationEventPublisher`는 Spring이 제공하는 이벤트 발행기입니다. `eventPublisher.publishEvent(new DreamiAcceptedEvent(offerId))`처럼 이벤트 객체를 던지면, 이 이벤트를 구독(`@EventListener`)하는 다른 코드가 (설정에 따라 트랜잭션 커밋 후) 실행됩니다. `acceptOffer`가 커밋 전에는 절대 매칭 엔진에 제출하면 안 되는 이유(`overview.md` 4절)가 이 "커밋 후 리스너 실행" 특성과 맞물립니다.

---

## 8. Bean Validation — `@NotBlank`, `@NotNull`, `@Valid`

> `DreamiAuthRequestDto`, `DreamiOnlineRequest`, 컨트롤러 메서드 파라미터

Jakarta Bean Validation(JDK 표준이 아닌 별도 스펙) annotation을 DTO 필드에 붙이면, 컨트롤러 파라미터에 `@Valid`가 함께 있을 때 Spring이 그 규칙을 자동으로 검사합니다. 위반 시 컨트롤러 코드가 실행되기도 전에 400 에러로 걸러집니다.

```java
public record DreamiAuthRequestDto(
        @NotBlank String idCardKey,       // null이거나 공백만 있으면 검증 실패
        @NotBlank String criminalRecordKey
) {}

public void verifyUploadedDocuments(@Valid @RequestBody DreamiAuthRequestDto requestDto, ...) {
    // @Valid가 없으면 @NotBlank가 붙어 있어도 검사가 실행되지 않는다
}
```

---

## 9. Swagger(OpenAPI) annotation — API 문서 자동 생성용

> 모든 컨트롤러

`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`는 로직에 전혀 영향을 주지 않고, `springdoc-openapi`가 이 annotation들을 읽어 Swagger UI 문서를 자동으로 만들어줍니다.

```java
@Tag(name = "드리미 컨트롤러", description = "...")   // 클래스 단위: 문서에서 API를 묶을 그룹 이름
@Operation(summary = "...", description = "...")   // 메서드 단위: 이 API가 하는 일
@ApiResponse(responseCode = "200", description = "...")  // 성공 응답 설명
@Schema(description = "...", nullable = true)       // DTO 필드 단위: 필드 설명/예시/nullable 여부
```

`@ApiErrorCodes(enumClass = DreamiErrorCode.class, codes = {"NOT_FOUND"})`는 Swagger 표준이 아니라 **이 프로젝트가 직접 만든** annotation입니다. "이 API가 던질 수 있는 에러코드"를 문서에 나열하기 위한 것으로, 실제 예외 처리 로직과는 무관합니다(문서용).

---

## 10. 이 프로젝트만의 커스텀 파라미터 annotation

> 모든 컨트롤러

Spring MVC가 기본 제공하는 게 아니라, 이 프로젝트가 `HandlerMethodArgumentResolver`로 직접 구현한 annotation들입니다.

| Annotation | 하는 일 |
|---|---|
| `@LoginUser UUID boormiId` | 로그인 세션에서 현재 로그인한 사용자의 id를 꺼내 파라미터에 자동으로 넣어줌 |
| `@AdminUser UUID adminId` | 로그인 세션에서 관리자 id를 꺼내 넣어줌. 세션이 없으면 `UNAUTHORIZED`, 로그인했지만 관리자가 아니면 `FORBIDDEN_ROLE` |
| `@PublicApi` | (이 도메인에는 없지만 컨벤션상 자주 등장) 로그인 세션 검사 자체를 건너뛰는 공개 API 표시 |

컨트롤러 메서드 시그니처에 이 annotation이 붙은 파라미터가 있으면, 요청 바디나 쿼리 파라미터가 아니라 **세션에서** 값을 가져온다는 뜻입니다.

---

## 관련 문서

- [overview.md](overview.md) — 이 도메인이 실제로 무엇을 하는지(비즈니스 로직, 동시성 이슈, API 목록)
- [../upload/java-patterns.md](../upload/java-patterns.md) — `Optional`, `record`, 텍스트 블록 등 겹치는 패턴을 더 자세히 다룸

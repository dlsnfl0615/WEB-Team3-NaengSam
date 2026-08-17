# 주변 콜 조회 수동 N+1 개선

`findNearbyCalls`가 주변 주문마다 `findById`를 반복하던 문제와, 이를 주문 ID 일괄 projection 조회로 바꾸기까지의 결정 기록. (이슈 #460, PR #465)

## 문제 상황

드리미가 주변 콜 목록을 볼 때 호출하는 `POST /api/v1/dreami/calls/nearby`는 **콜 1건마다 주문 조회 SQL을 1번씩 더 실행**하고 있었다.

```java
// 개선 전 — DreamiService
@Transactional(readOnly = true)
public List<NearbyCallDto> findNearbyCalls(NearbyOrderRequest request) {
    return nearbyOrderFinder.find(request).stream()
            .map(this::toNearbyCallDto)
            .toList();
}

private NearbyCallDto toNearbyCallDto(NearbyOrderDto nearby) {
    Orders order = orderRepository.findById(nearby.orderId())   // ← 콜마다 1회
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    return NearbyCallDto.from(nearby, order);
}
```

`NearbyOrderFinder`의 반환 상한이 10건(`MAX_NEARBY_ORDER_COUNT`)이므로 한 요청의 N은 최대 10이다. 실제로 로컬 MySQL에 주문 1만 건을 넣고 Hibernate SQL 로그로 세어본 결과가 아래다.

| 요청 `count` | ORDERS 조회 statement |
| ---: | ---: |
| 1 | 1회 |
| 5 | 5회 |
| 10 | 10회 |

요청당 SQL이 화면에 뿌리는 카드 수에 그대로 비례했다. 게다가 매번 `Orders` 엔티티 전체를 읽어서, 목록에 쓰지도 않는 `route_path`(경로 좌표 JSON, TEXT), `image_key`, `delivery_request` 같은 컬럼까지 10번 실어 날랐다.

## 원인

### 매칭 엔진은 주문 상세를 모른다

`findNearbyCalls`는 두 도메인을 합쳐서 응답을 만든다.

| 출처 | 주는 것 |
| --- | --- |
| matching (인메모리) | `orderId`, 좌표, 거리 |
| order (DB) | 품목, 주소, 예상수익, ETA |

`MatchingService.waitingOrders()`가 돌려주는 `WaitingOrder`는 `record WaitingOrder(UUID orderId, GeoPoint location)`가 전부다. 화면에 필요한 나머지를 order 도메인에서 다시 읽어야 하는데, 그 "다시 읽기"가 스트림 `map` 안에 들어가 있었던 것이 문제의 전부다.

### 흔한 N+1과는 다르다

연관관계 지연 로딩에서 생기는 전형적인 N+1이 아니다. **이 프로젝트는 JPA 연관관계 매핑이 하나도 없다.**

```
$ grep -rE "@ManyToOne|@OneToOne|@OneToMany|@JoinColumn|FetchType" src/main/java
(결과 없음)
```

모든 FK가 raw `UUID` 컬럼(`Orders.boormiId`, `Orders.dreamiId`, `Delivery.orderId`)이고, 조인이 필요하면 JPQL 세타 조인으로 쓴다. 그래서:

- **`@EntityGraph` / `join fetch`를 쓸 수 없다.** 페치할 연관이 애초에 없다.
- **`hibernate.default_batch_fetch_size=100`도 무력하다.** 이 설정은 연관 컬렉션/프록시를 모아 읽는 옵션이라, 연관이 없으면 아무것도 하지 않는다.

즉 Hibernate가 만들어낸 N+1이 아니라 **서비스 코드가 루프에서 직접 만든 수동 N+1**이고, ORM 설정으로는 풀 수 없다. 호출부를 고쳐야 한다.

## 해결 과정과 그 근거

### 1. 일괄 조회 + projection

id를 모아 `IN` 한 번으로 읽고, 필요한 컬럼만 record로 받는다.

```java
// OrderRepository
@Query("SELECT new com.naengsam.quick.domain.order.dto.NearbyCallOrderDto("
        + "o.orderId, o.itemName, o.itemCd, o.orderCd, o.deliveryAmount, o.deliveryEta, "
        + "o.originAddressLine1, o.originAddressLine2, "
        + "o.destinationAddressLine1, o.destinationAddressLine2) "
        + "FROM Orders o WHERE o.orderId IN :orderIds")
List<NearbyCallOrderDto> findNearbyCallOrders(@Param("orderIds") List<UUID> orderIds);
```

**왜 인터페이스 projection이 아니라 `SELECT new` 생성자 표현식인가** — 팀에 이미 자리잡은 관용구다. `OrderRepository.aggregateCompletedSavingByBoormi`, `MoneyLedgerRepository.findRecentByWalletId` 등 5곳이 전부 이 방식이고, 인터페이스 projection은 코드베이스에 한 건도 없다. 새 방식을 들일 이유가 없었다.

**왜 컬럼을 골라 받는가** — `NearbyCallDto`가 실제로 쓰는 건 `Orders`의 약 20개 컬럼 중 9개뿐이다. `route_path`는 TEXT다.

**왜 청크 분할이 없는가** — 상한이 10건으로 고정돼 있다. 지금 없는 확장성을 미리 만들지 않는다.

**왜 `order/dto`에 두는가** — `@Query` 생성자 표현식은 FQCN을 `OrderRepository` 안에 적어야 한다. `NearbyCallOrderDto`를 `dreami/dto`에 두면 order → dreami 역방향 의존이 생겨 "도메인끼리 독립 유지" 규칙이 깨진다. 반대 방향(dreami → order/dto)은 `NearbyCallDto`가 이미 `OrderCd`를 import하고 있어 새 결합이 아니다.

**메서드 이름을 `findByOrderIdIn`이 아니라 `findNearbyCallOrders`로 둔 이유** — 같은 파일에 `@Lock(PESSIMISTIC_WRITE)`가 붙은 `findByOrderId`가 있다. 락 애노테이션은 메서드 단위라 새 메서드가 물려받지는 않지만, `readOnly` 트랜잭션에서 도는 조회이므로 이름으로도 락 경로와 구분되게 했다.

### 2. 정렬은 호출부가 다시 맞춘다

`IN` 결과의 행 순서는 보장되지 않는다. 거리순은 매칭 엔진이 이미 정렬해 돌려준 목록을 순회하는 것으로 유지한다.

```java
List<NearbyOrderDto> nearbyOrders = nearbyOrderFinder.find(request);
if (nearbyOrders.isEmpty()) {
    return List.of();
}

Map<UUID, NearbyCallOrderDto> ordersById = orderRepository
        .findNearbyCallOrders(nearbyOrders.stream().map(NearbyOrderDto::orderId).toList())
        .stream()
        .collect(Collectors.toMap(NearbyCallOrderDto::orderId, order -> order));

return nearbyOrders.stream()                                  // ← 이미 거리순
        .filter(nearby -> ordersById.containsKey(nearby.orderId()))
        .map(nearby -> NearbyCallDto.from(nearby, ordersById.get(nearby.orderId())))
        .toList();
```

빈 목록 조기 반환은 방어 코드가 아니다. 반경 안에 콜이 없는 건 드리미 폴링의 흔한 경우이고, 가드가 없으면 매 폴링마다 의미 없는 `IN ()` 쿼리가 나간다.

### 3. 누락 행은 예외 대신 제외 — 동작 변경

DB에 행이 없는 주문을 만났을 때, 기존에는 `ORDER_NOT_FOUND`를 던졌다. 이를 **목록에서 조용히 빼는 것**으로 바꿨다.

근거는 세 가지다.

1. **어긋남은 버그가 아니라 정상 구간이다.** `MatchingService.waitingOrders()`는 DB와 트랜잭션 결합이 없는 인메모리 스냅샷이다. 주문이 취소·완료됐는데 엔진 그룹이 아직 비활성화되지 않은 순간이 존재한다.
2. **기존 동작은 피해 범위가 잘못됐다.** stale 1건 때문에 **유효한 9건까지 포함해 목록 전체가 실패**했고, 클라이언트가 재시도해도 엔진이 스스로 정리될 때까지 같은 에러가 반복됐다. 제외하면 "카드 한 장 덜 보임"으로 끝나고 다음 폴링에서 자연 복구된다.
3. **목록 API에는 단건 404가 어울리지 않는다.** `find`는 이미 반경 필터와 10건 상한을 걸어 best-effort로 동작한다. "DB에 행이 없다"는 콜이 안 보이는 또 하나의 이유일 뿐이다.

이에 맞춰 `DreamiController.findNearbyCalls`의 `@ApiErrorCodes(ORDER_NOT_FOUND)`를 제거했다. 낼 수 없는 에러를 문서에 남겨두지 않는다. `OrderErrorCode.ORDER_NOT_FOUND` enum 자체는 `acceptOffer`·`getOfferItemPhoto`·`getMyDelivery`가 계속 쓴다.

### 4. `NearbyCallDto.from`은 오버로드가 아니라 교체

`from(NearbyOrderDto, Orders)`를 `from(NearbyOrderDto, NearbyCallOrderDto)`로 **바꿨다.** 엔티티를 받는 오버로드를 남겨두면 N+1을 다시 불러들이는 입구가 된다. 호출부는 이번에 고친 한 곳뿐이었다.

### 5. 통합 테스트를 붙인 이유

단위 테스트만으로는 부족한 지점이 있어서 `@DataJpaTest`(H2 MySQL 모드 + 실제 `sql/sym-boorm-ddl.sql`)를 하나 추가했다.

기동 시점 문법 검증은 이미 무료다 — `@DataJpaTest`가 모든 리포지토리를 부트스트랩하므로 생성자 인자 개수/타입이 틀리면 기존 통합 테스트에서 터진다. 통합 테스트가 잡는 건 그게 아니라:

- **슬롯 순서.** `originAddressLine1`/`originAddressLine2`(및 destination 쌍)는 넷 다 `String`이다. 순서가 뒤바뀌어도 컴파일과 기동이 모두 통과하고, 잘못된 주소가 그대로 사용자에게 나간다. 실제 행을 왕복해 값으로 자리를 고정해야만 잡힌다.
- **`BINARY(16)` UUID에 대한 `IN` 바인딩.** 이 쿼리를 만든 이유 그 자체다.
- **부분집합 반환.** 위 3번의 "제외" 결정을 DB 레벨에서 고정한다.

단위 테스트 쪽에는 회귀 방지용으로 `주변콜조회_주문은_한_번에_조회하고_거리순_정렬을_유지한다`를 넣었다. 프로젝션을 일부러 섞어서 돌려주고 결과가 거리순인지 확인하면서, `findNearbyCallOrders` 1회 호출과 `findById` 미호출을 함께 검증한다.

## 개선 전후 결과

`index-test/` 하니스로 측정했다. 로컬 macOS + Docker MySQL 8(포트 3307), 주문 1만 건 seed, 그중 고정 ID 10건만 인메모리 매칭 엔진에 등록. k6로 워밍업 20회 후 2분간 측정.

### 요청당 ORDERS 조회 SQL

| 요청 `count` | 개선 전 | 개선 후 |
| ---: | ---: | ---: |
| 1 | PK 조회 1회 | `IN` 조회 1회 |
| 5 | PK 조회 5회 | `IN` 조회 1회 |
| 10 | PK 조회 10회 | `IN` 조회 1회 |

개선 후 SQL은 아래와 같다. `route_path`·`image_key`·`delivery_request_dtm`이 SELECT 목록에 없는 것으로 엔티티 로드가 아니라 프로젝션임을 확인할 수 있다.

```sql
select o1_0.order_id, o1_0.item_name, o1_0.item_cd, o1_0.order_cd, o1_0.delivery_amount,
       o1_0.delivery_eta, o1_0.origin_address_line_1, o1_0.origin_address_line_2,
       o1_0.destination_address_line_1, o1_0.destination_address_line_2
from ORDERS o1_0 where o1_0.order_id in (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

### 응답시간

조건당 2분씩 3회 반복하고, avg 기준 중앙값 런을 대표값으로 썼다.

| 버전 | VUs | avg | med | p95 | p99 | iterations/s | 실패율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 개선 전 | 1 | 7.22ms | 6.15ms | 12.41ms | 19.33ms | 134.9 | 0% |
| 개선 후 | 1 | 1.74ms | 1.58ms | 2.73ms | 3.44ms | 546.6 | 0% |
| 개선 전 | 10 | 13.23ms | 12.75ms | 17.24ms | 21.04ms | 744.6 | 0% |
| 개선 후 | 10 | 4.58ms | 4.38ms | 6.27ms | 8.47ms | 2120.8 | 0% |

**VU=1은 약 4배, VU=10은 처리량 기준 약 2.8배**(745 → 2,121 it/s) 개선됐다. 로컬 MySQL은 네트워크 왕복이 거의 없어 지연 차이가 작게 나올 것으로 예상했지만, 왕복 횟수 자체가 10회에서 1회로 줄어드니 그대로 드러났다. 커넥션 점유 시간이 짧아진 효과는 동시 사용자 10명 구간에서 처리량으로 나타난다.

### 측정하면서 알게 된 것 — 첫 런은 못 믿는다

3회 원본은 아래와 같다.

| 버전 | VUs | 1회 | 2회 | 3회 |
| --- | ---: | ---: | ---: | ---: |
| 개선 전 | 1 | **13.40ms** | 6.30ms | 7.22ms |
| 개선 후 | 1 | 1.84ms | 1.71ms | 1.73ms |
| 개선 전 | 10 | 12.86ms | 13.23ms | 14.44ms |
| 개선 후 | 10 | 4.57ms | 4.52ms | 4.96ms |

개선 전 VU=1의 1회차만 나머지 두 회차의 두 배 가까이 느리다. JVM JIT과 MySQL 버퍼 풀이 덥혀지기 전의 첫 런이었다. **워밍업 20회로는 1만 건 테이블의 첫 런 편차를 다 흡수하지 못한다.**

만약 1회차만 측정하고 끝냈다면 "13.40ms → 1.84ms, 7.3배 개선"이라고 적었을 것이다. 실제 값은 4배다. 개선 효과가 충분히 큰 경우에도 반복 측정을 생략하면 안 되는 이유가 여기 있다.

## 남은 과제

- **`orderCd` staleness는 이번 개선으로 달라지지 않았다.** DB 상태가 이미 `MATCHING`을 지난 주문도 예전처럼 그대로 목록에 뜬다. 위 3번의 skip 필터는 "행이 없으면 뺀다"이지 상태 필터가 아니다. 쿼리에 `AND o.orderCd IN (...)`을 넣으면 DB를 staleness의 기준으로 삼게 되는데, 이건 제품 동작 변경이라 성능 개선과 분리했다.
- `Collectors.toMap`은 키 중복 시 예외를 던진다. `orderOfferGroupsByOrderId`가 orderId로 키를 잡아 현재는 중복이 나올 수 없어서 방어적 merge 함수 대신 주석으로 남겼다. 매칭 엔진의 키 구조가 바뀌면 같이 봐야 한다.

## 참고

- 재현·측정 절차: `backend/index-test/README.md`
- 측정 원본: `backend/index-test/{before,after}-vu{1,10}.json`

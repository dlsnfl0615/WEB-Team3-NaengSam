# findStaleLocationDeliveries 복합인덱스 전/후 측정 환경

`DreamiOfflineDetector.detectOfflineDreamis()`는 `delivery.dreami-offline-scan-interval=5s` 주기로
`DeliveryRepository.findStaleLocationDeliveries()`를 호출한다.

```java
@Query("SELECT d FROM Delivery d WHERE d.deliveryCd IN :statuses "
        + "AND d.lastLocationDtm IS NOT NULL AND d.lastLocationDtm < :threshold")
```

그런데 `sql/sym-boorm-ddl.sql`의 DELIVERY 테이블에 걸린 인덱스는 `PK_DELIVERY(delivery_id)`와
`IX_DELIVERY_ORDER_ID(order_id)` 둘뿐이다. `delivery_cd`, `last_location_dtm` 어디에도 인덱스가 없으므로
이 쿼리는 **5초마다 DELIVERY 풀스캔**을 돈다. 진행중 배달 건수는 일정한데 배달 내역은 계속 쌓이기만 하므로,
테이블이 커질수록 스캔 비용은 선형으로 늘어나는데 실제로 필요한 행은 진행중인 소수뿐이다.

이 디렉터리는 아래 복합인덱스를 실제로 적용하기 전에 근거 숫자를 만들기 위한 것이다.

```sql
CREATE INDEX IX_DELIVERY_STATUS_LAST_LOCATION ON DELIVERY (delivery_cd, last_location_dtm);
```

`delivery_cd`를 선두에 두는 이유는 종료·취소된 과거 배달을 먼저 제외하기 위해서다.
그 배달들은 `last_location_dtm`이 계속 과거로 남아 있어 `last_location_dtm < threshold`를 그대로 통과한다.
`last_location_dtm` 선두 인덱스는 이들을 범위 안에 그대로 끌고 들어오지만, `delivery_cd` 선두 인덱스는
두 상태값에 대한 range 두 개로 진행중 배달만 먼저 잘라낸다.

동시에 위치가 수신될 때마다 `last_location_dtm`이 갱신되므로 이 인덱스는 쓰기 경로에서 매번 유지비를 낸다.
그래서 **읽기 이득과 쓰기 비용을 같이 측정**한다.

`n-plus-1-nearby-test/`는 서버를 띄우고 k6로 부하를 거는 방식이지만, 여기서는 서버도 k6도 쓰지 않고
**MySQL 클라이언트만으로 SELECT / UPDATE 성능을 직접 잰다.**

## 구성

| 파일 | 역할 |
| --- | --- |
| `docker-compose.yml` | 이 실험 전용 MySQL 8.0 (포트 3308). `n-plus-1-nearby-test`의 3307 컨테이너와 동시에 떠 있어도 충돌하지 않는다 |
| `seed.sql` | ORDERS 20,000건 + DELIVERY 20,000건 목 데이터. 재실행 가능 |
| `bench.sql` | 워밍업 + 4종 측정(스캔량 / 실행계획 / SELECT 반복 / UPDATE 반복). 전·후에 똑같이 실행한다 |
| `add-index.sql` | 복합인덱스 생성 + `ANALYZE TABLE` |
| `drop-index.sql` | 다음 회차를 위한 롤백 |

측정 결과 원본은 `results/Round<N>/` 아래에 회차별로 담겨 있다.
정리된 수치는 아래 [측정 결과](#측정-결과)에 있다.

## 목 데이터 구성

동시 진행중 배달 1,000건, DELIVERY 테이블 전체 20,000건을 가정한다.

| n 범위 | 건수 | `delivery_cd` | `last_location_dtm` | 쿼리 매칭 |
| --- | ---: | --- | --- | --- |
| 1 ~ 500 | 500 | `PICKUP_NORMAL` / `DELIVERING` 교대 | 기준시각 60~299초 전 | ✅ **결과 행** |
| 501 ~ 950 | 450 | 위 2개 교대 | 기준시각 0~24초 전 | ❌ 아직 살아있음 |
| 951 ~ 1000 | 50 | 위 2개 교대 | `NULL` | ❌ 첫 위치 미수신 |
| 1001 ~ 20000 | 19,000 | `DELIVERED` / `TERMINATED` / `RETURNED` / `PICKUP_CANCELLED_BY_BOORMI` | 기준시각 1601초~ 전 | ❌ 종료 배달 |

- 진행중 1,000건 중 절반인 500건을 stale 로 잡았다. 앱이 아니라 웹브라우저로 위치를 보내므로
  GPS 가 끊길 확률이 앱보다 높다고 보고 잡은 값이다.
- 951~1000의 `NULL`은 `IS NOT NULL` 분기를 실제로 태우기 위한 것이다.
- 마지막 19,000건의 `last_location_dtm`을 **전부 threshold 보다 과거로** 둔 것이 이 실험의 핵심이다.
  `last_location_dtm` 선두 인덱스라면 이 19,000건이 범위에 그대로 들어온다는 사실을 숫자로 보여준다.
- `PICKUP_DELAYED`는 사용 예정이 없어 시드에도 측정 쿼리에도 넣지 않았다.

## 고정 픽스처

- 부르미 ID `47000000-0000-0000-0000-000000000001`
- 주문 ID `47000000-0000-0000-0001-000000000001` ~ `...000000004e20` (n = 1..20000)
- 배달 ID `47000000-0000-0000-0002-000000000001` ~ `...000000004e20` (같은 n 으로 주문과 1:1 대응)
- 기준시각 `@base_dtm = '2026-08-16 12:00:00'`, threshold = `2026-08-16 11:59:30` (= 기준시각 - 30초)

시각을 `CURRENT_TIMESTAMP`가 아니라 고정 기준점으로 잡은 이유는, 벤치 도중 실시간이 흐르면
"아직 살아있는 450건"이 하나씩 stale 로 넘어가 결과 행 수가 달라지기 때문이다.
고정 기준점 + 고정 threshold 리터럴이어야 전/후 측정이 항상 정확히 500행으로 재현된다.
`seed.sql`의 `@base_dtm`을 바꾸면 `bench.sql`의 threshold 리터럴도 같이 바꿔야 한다.

`seed.sql`은 이 고정 부르미의 데이터만 지우고 다시 넣는다. 다른 데이터는 건드리지 않는다.

## 준비 — 접속 명령 단축

아래 모든 명령은 **백엔드 루트(`backend/`)** 에서 실행한다.
`FLUSH STATUS`(RELOAD 권한)와 프로시저 생성이 필요하므로 `root`로 접속한다.

```bash
MYSQL='docker exec -i symboorm-delivery-index-test-mysql mysql --table -uroot -pdelivery-index-test-root symboorm_delivery_index_test'
RESULTS=index-test/delivery-stale-location-index-test/results

ROUND=1                        # 회차. 4 ~ 7 을 한 바퀴 돌 때마다 8번에서 올린다.
OUT=$RESULTS/Round$ROUND
mkdir -p $OUT
```

회차마다 `results/Round1/`, `results/Round2/` … 로 폴더가 나뉜다.
`ROUND` 를 올리지 않고 4번을 다시 실행하면 **직전 회차 파일을 덮어쓰므로** 주의한다.
쉘을 새로 열면 `MYSQL` / `RESULTS` / `ROUND` / `OUT` 이 사라지니 이 블록을 다시 실행한다.

## 1. MySQL 컨테이너 기동

```bash
docker compose -f index-test/delivery-stale-location-index-test/docker-compose.yml up -d
docker compose -f index-test/delivery-stale-location-index-test/docker-compose.yml ps
```

`healthy` 로 바뀔 때까지 20초 정도 기다린다.

## 2. 프로젝트 DDL 적재 (최초 1회)

```bash
eval $MYSQL < sql/sym-boorm-ddl.sql
```

DDL 은 재실행 가능한 마이그레이션이 아니다. 이미 적재된 상태에서 다시 실행하면
`Table already exists` 로 실패하므로 최초 1회만 실행한다.
스키마를 처음부터 다시 만들려면 컨테이너와 볼륨을 지우고(`down -v`) 1번부터 다시 한다.

## 3. 목 데이터 적재

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/seed.sql
```

마지막에 나오는 검증 SELECT 세 개가 각각 아래와 같아야 한다. 다르면 다음 단계로 넘어가지 않는다.

| 항목 | 기대값 |
| --- | ---: |
| `total_delivery_count` | 20000 |
| `active_delivery_count` | 1000 |
| `stale_match_count` | 500 |

## 4. 개선 전 측정

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/bench.sql \
  | tee $OUT/before-$ROUND.txt
```

## 5. 인덱스 추가

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/add-index.sql
```

## 6. 데이터 원복

4번의 `(D)` 구간이 진행중 1,000건의 `last_location_dtm`을 바꿔놓았다.
그대로 두면 결과 행 수가 500이 아니게 되어 비교가 성립하지 않으므로 반드시 다시 적재한다.

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/seed.sql
```

여기서도 검증 SELECT 가 20000 / 1000 / 500 인지 다시 확인한다.

## 7. 개선 후 측정

4번과 **완전히 같은** 명령을 돌린다. 출력 파일명만 다르다.

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/bench.sql \
  | tee $OUT/after-$ROUND.txt
```

## 8. 다음 회차 준비 후 4 ~ 7 반복

7번까지 끝나면 인덱스를 되돌리고, 데이터를 다시 깔고, **`ROUND` 를 올린 뒤** 4번으로 돌아간다.
`ROUND` 를 올려야 이전 회차의 `before-1.txt` / `after-1.txt` 를 덮어쓰지 않는다.

```bash
eval $MYSQL < index-test/delivery-stale-location-index-test/drop-index.sql
eval $MYSQL < index-test/delivery-stale-location-index-test/seed.sql

ROUND=$((ROUND + 1))
OUT=$RESULTS/Round$ROUND
mkdir -p $OUT
echo "다음 회차: $ROUND -> $OUT"
# 이제 4번으로 돌아간다
```

총 3회차를 돌리면 아래처럼 남는다.

```
results/Round1/before-1.txt  results/Round1/after-1.txt
results/Round2/before-2.txt  results/Round2/after-2.txt
results/Round3/before-3.txt  results/Round3/after-3.txt
```

한 조건당 3회씩 돌리고 **avg 기준 중앙값 회차**를 아래 결과 표에 싣는다.
첫 회차는 JVM 이 아니라 InnoDB 버퍼 풀과 OS 페이지 캐시가 아직 덜 데워진 상태라
이상치가 나오기 쉽다. `n-plus-1-nearby-test/README.md`가 첫 회차 이상치 때문에
중앙값 회차를 채택한 것과 같은 이유다.

## 측정 결과

측정 환경:

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-08-16 |
| OS | macOS (Darwin 24.6.0) |
| DB | Docker MySQL 8.0 (포트 3308) |
| DELIVERY 행 수 | 20,000 (진행중 1,000 / 매칭 500) |
| 반복 | 조건당 3회, `select_avg_us` 기준 **중앙값 회차** 채택 (개선 전 = Round2, 개선 후 = Round1) |

| 지표 | 개선 전 | 개선 후 | 차이 |
| --- | --- | --- | --- |
| `EXPLAIN` type | `ALL` | `range` | 풀스캔 → 범위 스캔 |
| `EXPLAIN` key | `NULL` | `IX_DELIVERY_STATUS_LAST_LOCATION` | — |
| `EXPLAIN` rows / filtered | 19,328 / 6.00% | 500 / 100.00% | 추정 정확도까지 개선 |
| `EXPLAIN` Extra | `Using where` | `Using index condition` | 인덱스에서 조건 처리 |
| `Handler_read_rnd_next` | 20,001 | 0 | 풀스캔 소멸 |
| `Handler_read_key` | 1 | 2 | 상태값 2개 = range 2개 |
| `Handler_read_next` | 0 | 500 | 필요한 500행만 순차 접근 |
| **총 접근 행 수** | **20,001** | **502** | **약 40배 감소** |
| `matched_rows` | 500 | 500 | 결과 동일 — 비교 성립 |
| EXPLAIN ANALYZE actual time | 5.98 ms | 0.468 ms | **약 12.8배** |
| SELECT 200회 평균 | 5,856.5 µs | 720.0 µs | **약 8.1배 (87.7% 감소)** |
| UPDATE 5,000회 평균 | 11.75 µs | 13.98 µs | **+18.9% (쓰기 비용)** |
| UPDATE 처리량 | 85,076 TPS | 71,551 TPS | −15.9% |

회차별 원본:

| 회차 | 개선 전 `select_avg_us` | 개선 후 `select_avg_us` | 개선 전 `update_avg_us` | 개선 후 `update_avg_us` |
| --- | ---: | ---: | ---: | ---: |
| Round1 | 5,822.4 | **720.0** | 11.46 | **13.98** |
| Round2 | **5,856.5** | 738.8 | **11.75** | 15.37 |
| Round3 | 8,572.7 | 702.1 | 12.03 | 13.90 |

굵게 표시한 것이 채택한 중앙값 회차다. 개선 전 Round3(8,572.7 µs)가 튀는데,
같은 회차의 EXPLAIN ANALYZE 5회도 6.35~10.4 ms 로 흔들렸다. 다른 프로세스와 CPU 를 나눠 쓴
영향으로 보이며, 스캔량(20,001)은 세 회차 모두 동일했다. 이런 이상치 때문에 평균이 아니라
중앙값 회차를 채택했다 — `n-plus-1-nearby-test/README.md` 와 같은 방식이다.

## 해석

**읽기는 명확히 개선됐다.** 가장 확실한 근거는 (A) 스캔량이다.
개선 전에는 500행을 얻으려고 20,001행을 전부 훑었고(`Handler_read_rnd_next = 20001`),
개선 후에는 `PICKUP_NORMAL` / `DELIVERING` 두 개의 range 로 진입해(`Handler_read_key = 2`)
필요한 500행만 순차로 읽었다(`Handler_read_next = 500`). **약 40배**다.
이 값은 실행 시간과 달리 머신 상태에 흔들리지 않아서 3회 모두 정확히 같은 숫자가 나왔다.

**`delivery_cd` 를 선두에 둔 판단이 맞았다.** 개선 후 EXPLAIN ANALYZE 를 보면
`(delivery_cd = 'PICKUP_NORMAL' AND ... < last_location_dtm < ...) OR (delivery_cd = 'DELIVERING' AND ...)`
형태로 range 두 개를 탄다. 종료·취소된 19,000건은 `last_location_dtm` 이 전부 threshold 보다
과거라 범위 조건만으로는 걸러지지 않는데, 선두 컬럼이 상태값이라 아예 접근조차 하지 않았다.
`filtered` 가 6.00% → 100.00% 로 바뀐 것이 그 증거다 — 읽은 행이 곧 결과 행이라는 뜻이다.

**절대 시간 이득은 지금 규모에선 크지 않다.** 스캔 1회당 5.86ms → 0.72ms, 5초 주기로 환산하면
분당 약 61ms 절약이다. 20,000행은 기본 128MB 버퍼 풀에 전부 올라가므로 디스크 I/O 가 아니라
CPU 바운드 스캔을 잰 것이다. 다만 개선 전 비용은 **테이블 크기에 비례해 늘어나고**
개선 후 비용은 진행중 배달 수에만 비례한다. 배달 내역이 200,000건이 되면
개선 전은 10배 느려지지만 개선 후는 그대로 502행이다. "지금 이득"이 아니라
"쌓였을 때의 이득"으로 읽어야 한다.

참고로 (C) 의 8.1배는 보수적인 수치다. `CONCAT_WS` 로 500행 전 컬럼을 훑는 오버헤드(약 250 µs)가
전·후에 똑같이 실려 있어서, 그걸 빼면 쿼리 자체는 (B) 가 보여주는 12.8배에 가깝다.

**쓰기 비용은 감당 가능한 수준이다.** UPDATE 1건당 11.75 µs → 13.98 µs 로 **약 19% 늘었다.**
인덱스가 하나 늘었으니 당연한 결과다. 하지만 실제로 필요한 처리량은
진행중 1,000건 × 5초당 1핑 = **약 200 UPDATE/s** 인데, 개선 후에도 71,551 TPS 가 나온다.
필요량의 **358배** 여유다. 읽기에서 40배를 벌고 쓰기에서 19%를 내주는 거래이므로 채택할 만하다.

단, 이 수치에는 커밋 fsync 가 빠져 있다. `bench.sql` 의 UPDATE 루프는 하나의 트랜잭션으로
묶여 있는데, 커밋 fsync 는 인덱스 유무와 무관한 고정비라 포함시키면 인덱스 유지비 차이가
그 안에 묻히기 때문이다. 실제 서비스에서는 핑 1건당 트랜잭션 커밋 비용이 더해지는데,
그 고정비는 인덱스 추가와 무관하게 이미 지불하고 있던 것이다.

`Innodb_rows_updated` 는 결론에 쓰지 않았다. `FLUSH STATUS` 로 리셋되지 않는 전역 누적
카운터라 회차마다 5,000씩 그대로 쌓였을 뿐(5,000 → 10,000 → … → 30,000),
인덱스 유지비에 대한 신호가 아니다. `Handler_update` 는 전·후 모두 5,000 으로 동일했다.

## 결론

인덱스를 추가할 만하다.

- 읽기: 접근 행 수 **40배 감소**, 쿼리 실행 시간 **8~13배 단축**
- 쓰기: UPDATE 1건당 **약 19% 증가**, 그러나 필요 처리량의 **358배** 여유
- 이득은 테이블이 커질수록 벌어지고, 비용은 진행중 배달 수에만 비례해 고정적이다

적용하려면 `backend/sql/sym-boorm-ddl.sql` 의 `CREATE INDEX IX_DELIVERY_ORDER_ID` 아래에
같은 표기 관례로 한 줄 추가한다.

```sql
CREATE INDEX `IX_DELIVERY_STATUS_LAST_LOCATION` ON `DELIVERY` (`delivery_cd`, `last_location_dtm`);
```

## 관찰 사항

- `DreamiOfflineDetector.TRACKED_STATUSES` 에는 아직 `PICKUP_DELAYED` 가 남아 있다.
  사용 예정이 없다면 제거 대상이지만, 이 실험에서는 코드를 건드리지 않고 측정만 두 상태로 맞췄다.
- 쿼리의 `last_location_dtm IS NOT NULL` 은 옵티마이저 관점에서 무의미하다.
  `NULL < '...'` 는 NULL 로 평가되어 어차피 WHERE 를 통과하지 못하므로 뒤의 범위 조건이 이미 NULL 을 배제한다.
  가독성 목적의 조건이므로 그대로 두었다.
- `detectOfflineDreamis()` 는 `@Transactional`(readOnly 아님)이라 조회한 엔티티를 전부 영속성 컨텍스트에
  올리고 더티체킹한다. 결과 행이 많아지면 스캔 비용 외에 이 비용도 같이 늘어난다.

## 정리

```bash
docker compose -f index-test/delivery-stale-location-index-test/docker-compose.yml down
# 데이터까지 지우려면
docker compose -f index-test/delivery-stale-location-index-test/docker-compose.yml down -v
```

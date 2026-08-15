# findNearbyCalls 수동 N+1 비교 환경

`DreamiService.findNearbyCalls`가 주변 주문 최대 10건을 찾은 뒤 주문마다 `findById`를 실행하는 현재 구현과, 주문을 한 번에 조회하도록 개선한 구현을 동일 조건에서 비교한다.

폴더 이름은 기존 작업 흐름에 맞춰 `index-test`를 유지하지만, 이 테스트의 주 지표는 인덱스 실행 계획이 아니라 요청당 SQL 수와 API 응답시간이다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `docker-compose.yml` | 로컬 MySQL 8 실행 |
| `seed.sql` | 부르미 1명과 주문 10,000건 생성 |
| `app.env.example` | 로컬 백엔드 실행 환경변수 |
| `application-query-count.properties` | Hibernate SQL·statement 수 확인용 설정 |
| `query-count.http` | 주변 주문 수 1·5·10의 SQL 증가 확인 |
| `k6/nearby-calls.js` | 준비, 워밍업, 응답시간 측정을 수행하는 k6 시나리오 |

`seed.sql`은 주문을 10,000건 넣지만, 인메모리 매칭 엔진에는 고정 ID의 주문 10건만 등록한다. `findNearbyCalls`의 최대 반환량이 10건이므로 한 요청의 N은 최대 10이고, 나머지 9,990건은 빈 테이블에 가까운 환경에서 측정하는 것을 피하기 위한 배경 데이터다.

## 고정 fixture

- 로그인: `nearby-n-plus-one@index.test` / `index-test`
- 부르미 ID: `46000000-0000-0000-0000-000000000001`
- 주변 주문 ID: `46000000-0000-0000-0001-000000000001`부터 순번 10까지
- 중심 좌표: `37.4979, 127.0276`

fixture는 `index-test` 전용 DB에서만 사용한다. `seed.sql` 재실행 시 위 고정 부르미의 주문만 교체하며 다른 사용자의 데이터는 지우지 않는다.

## 1. MySQL과 스키마 준비

백엔드 루트에서 실행한다.

```bash
docker compose -f index-test/docker-compose.yml up -d
```

새로 생성한 빈 볼륨에 프로젝트 DDL을 한 번 적용한다.

```bash
docker exec -i symboorm-index-test-mysql mysql -uindex_test -pindex-test symboorm_index_test < sql/sym-boorm-ddl.sql
```

fixture를 넣는다.

```bash
docker exec -i symboorm-index-test-mysql mysql -uindex_test -pindex-test symboorm_index_test < index-test/seed.sql
```

DDL은 재실행 가능한 마이그레이션이 아니므로 이미 스키마가 있는 DB에 반복 적용하지 않는다. Docker 볼륨 삭제는 적재 데이터를 모두 잃는 작업이므로 초기화가 정말 필요할 때만 별도로 수행한다.

## 2. 백엔드 실행

환경변수를 로드한다.

```bash
set -a
source index-test/app.env.example
set +a
```

응답시간 측정용 실행에서는 Hibernate 통계를 켜지 않는다.

```bash
./gradlew bootRun
```

애플리케이션을 재시작하면 인메모리 매칭 상태가 사라진다. k6의 `setup()`이 DB와 같은 고정 ID 주문 10건을 디버그 API로 다시 등록하고, 등록 완료를 확인한 뒤 워밍업을 수행한다. 기존 매칭 주문이 남아 있으면 측정을 오염시키지 않도록 k6가 중단한다.

## 3. 개선 전 응답시간 측정

준비 단계에서 로그인과 주문 등록을 수행한다. 실제 비교 지표 `nearby_calls_duration`에는 준비 요청과 워밍업 요청이 포함되지 않는다.

먼저 동시 사용자 1명으로 순수 지연을 측정한다.

```bash
k6 run --summary-export index-test/before-vu1.json -e VUS=1 -e DURATION=2m index-test/k6/nearby-calls.js
```

그다음 동시 사용자 10명으로 커넥션 점유와 처리량 영향을 확인한다.

```bash
k6 run --summary-export index-test/before-vu10.json -e VUS=10 -e DURATION=2m index-test/k6/nearby-calls.js
```

기본 워밍업은 20회다. 필요한 경우 `-e WARMUP_ITERATIONS=50`처럼 변경할 수 있다. 응답 사이 간격이 필요한 경우에만 `-e THINK_TIME_SECONDS=1`을 사용하며, 개선 전후에는 반드시 같은 값을 사용한다.

## 4. 요청당 SQL 수 확인

응답시간 테스트와 별도로 백엔드를 종료한 뒤, SQL 수 확인 설정을 추가해 다시 실행한다.

```bash
SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./index-test/application-query-count.properties ./gradlew bootRun
```

k6를 한 번 실행해 대상 주문을 등록했거나, 같은 JVM에 대상 주문이 이미 등록된 상태에서 `query-count.http`의 요청을 순서대로 실행한다. Hibernate 세션 통계와 SQL 로그에서 다음 값을 확인한다.

| 요청 count | 개선 전 | 개선 후 |
| ---: | ---: | ---: |
| 1 | PK 조회 1회 | `IN` 조회 1회 |
| 5 | PK 조회 5회 | `IN` 조회 1회 |
| 10 | PK 조회 10회 | `IN` 조회 1회 |

응답시간 측정에는 이 설정을 사용하지 않는다. SQL 출력과 Hibernate 통계 수집 자체가 측정값에 영향을 줄 수 있다.

## 5. 개선 후 동일 조건 재측정

개선 코드를 반영한 뒤 백엔드만 재시작한다. MySQL 데이터는 그대로 사용하고 k6 명령도 동일하게 유지한다.

```bash
k6 run --summary-export index-test/after-vu1.json -e VUS=1 -e DURATION=2m index-test/k6/nearby-calls.js
```

```bash
k6 run --summary-export index-test/after-vu10.json -e VUS=10 -e DURATION=2m index-test/k6/nearby-calls.js
```

각 조건을 최소 3회 반복하고 `nearby_calls_duration`의 `avg`, `med`, `p(95)`, `p(99)`와 전체 iteration 처리량을 비교한다. 로컬 MySQL은 네트워크 왕복 지연이 작으므로 지연 차이가 작게 나올 수 있지만, 요청당 SQL 수가 10회에서 1회로 줄었는지는 별도로 확정할 수 있다.

| 버전 | VUs | avg | med | p95 | p99 | iterations/s | 실패율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 개선 전 | 1 |  |  |  |  |  |  |
| 개선 후 | 1 |  |  |  |  |  |  |
| 개선 전 | 10 |  |  |  |  |  |  |
| 개선 후 | 10 |  |  |  |  |  |  |

## k6 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `API_BASE` | `http://localhost:8080` | 테스트 대상 백엔드 |
| `TEST_EMAIL` | fixture 이메일 | 로그인 계정 |
| `TEST_PASSWORD` | `index-test` | 로그인 비밀번호 |
| `VUS` | `1` | 동시 사용자 수 |
| `DURATION` | `2m` | 측정 시간 |
| `WARMUP_ITERATIONS` | `20` | setup 단계 워밍업 횟수 |
| `THINK_TIME_SECONDS` | `0` | 측정 요청 사이 대기 시간 |

이 하니스는 수동 N+1 구간만 고립해서 비교하기 위해 디버그 API로 인메모리 주문을 준비한다. 로그인과 실제 `POST /api/v1/dreami/calls/nearby` 요청은 운영 경로를 사용한다.

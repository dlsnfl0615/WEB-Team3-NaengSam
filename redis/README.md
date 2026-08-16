# Redis (로그인 대기열)

로그인 대기열의 순번과 티켓 상태를 담는 저장소입니다. 관련 이슈: #399

이 폴더는 **백엔드 배포 파이프라인과 분리**되어 있습니다. `monitoring/` 과 같은 방식으로, 이 폴더를 Redis 인스턴스에 복사해 별도 compose 로 띄웁니다.

## 왜 Redis 인가

로그인은 PBKDF2 를 210,000회 돌립니다. CPU 바운드라 동시 처리량 천장이 `vCPU 수 / 해시 시간`으로 고정되어 있고, 넘치는 요청을 그냥 받으면 톰캣 스레드가 전부 해싱에 묶여 나머지 API 까지 굶습니다. 그래서 동시 해싱을 `login.queue.permits` 개로 묶고, 넘치는 요청은 대기열에 세웁니다.

대기열을 인메모리가 아니라 Redis 에 두는 이유는 두 가지입니다.

- 재시작해도 대기자가 사라지지 않습니다.
- 순번 계산(`ZRANK`)과 만료 스윕(`ZREMRANGEBYSCORE`)을 저장소가 대신 해줍니다.

**Redis 가 처리량을 올려주지는 않습니다.** 해싱은 여전히 백엔드 인스턴스의 CPU 에서 돌고, 천장도 그대로입니다. Redis 가 파는 것은 격리·예측 가능성·정상 실패입니다.

## 구조

```
[백엔드 인스턴스]                         [Redis 인스턴스]
 backend(8080)  API                       redis(6379)
       └──── VPC 프라이빗 IP:6379 ────────────┘
       (보안그룹: 6379 는 백엔드 인스턴스 SG 에서만 허용 + requirepass)
```

## 보안 (필수)

Redis 를 별도 인스턴스에 두면 6379 가 VPC 에 노출됩니다. 방어선은 두 개이고, **둘 다 있어야 합니다.**

1. **보안그룹**: 6379 인바운드를 **백엔드 인스턴스의 보안그룹**에서만 허용합니다. `0.0.0.0/0` 이면 인터넷에 열립니다. EC2 는 공인 IP 를 IGW 가 NAT 하므로 바인딩 주소로는 외부 접근을 막을 수 없습니다 — `monitoring/` 의 8081 과 같은 논리입니다.
2. **`requirepass`**: `.env` 의 `REDIS_PASSWORD` 로 설정됩니다. 비워두면 컨테이너가 인증 없이 뜹니다. **반드시 채우세요.**

**이 규칙을 먼저 넣고 백엔드를 띄우세요.**

평문 비밀번호는 Redis 에 저장하지 않습니다. Redis 는 TLS 미구성이고, 공유 저장소에 자격증명을 두는 것은 백엔드 힙 대비 명백한 보안 후퇴입니다. Redis 에는 순번·상태·결과(boormiId 또는 에러코드)만 들어갑니다 (`LoginQueue` 의 설계 근거 참고).

## 기동

Redis 인스턴스에서:

```bash
# 0) 보안그룹 규칙(6379 ← 백엔드 SG)을 먼저 넣는다
# 1) 이 폴더를 복사하고 .env 를 만든다
cp .env.example .env && vi .env      # REDIS_PASSWORD 채우기

docker compose up -d
docker compose logs -f redis
```

백엔드 인스턴스의 `.env` 에는 같은 값을 넣습니다.

```
REDIS_HOST=<Redis 인스턴스 프라이빗 IP>
REDIS_PORT=6379
REDIS_PASSWORD=<위와 동일>
```

## 로컬에서 확인

```bash
docker run --rm -p 6379:6379 redis:7-alpine
```

비밀번호 없이 띄우면 `application.properties` 의 기본값(`REDIS_PASSWORD` 미설정)과 맞습니다.

## 저장되는 키

| 키                        | 타입 | 내용                                                                                             |
| ------------------------- | ---- | ------------------------------------------------------------------------------------------------ |
| `login:queue`             | ZSET | member = ticketId, score = 등록 시각(epoch millis)                                               |
| `login:ticket:{ticketId}` | HASH | `state`(WAITING/READY/FAILED), `payload`(boormiId 또는 에러코드). TTL = `login.queue.ticket-ttl` |

ZSET 멤버에는 개별 TTL 이 없어, 이탈한 대기자가 남아 뒷사람 순번을 부풀립니다. 백엔드가 30초마다 `ZREMRANGEBYSCORE` 로 걷어냅니다 (`LoginQueue.sweepExpired`).

## Redis 가 죽으면

대기열이 비어 있는 저부하에서는 백엔드가 Redis 를 아예 호출하지 않으므로 **로그인이 그대로 됩니다.** 고부하에서만 `LOGIN_QUEUE_UNAVAILABLE`(503)로 떨어집니다.

Redis 장애를 헬스체크에 반영하지 않는 것도 같은 이유입니다 (`management.health.redis.enabled=false`). 살아서 로그인을 처리 중인 인스턴스가 LB 에서 빠지거나 재시작되면 안 됩니다. 관측은 `login_queue_*` 메트릭으로 합니다.

## 관측

| 메트릭                               | 의미                                                                                       |
| ------------------------------------ | ------------------------------------------------------------------------------------------ |
| `login_queue_waiting`                | 이 인스턴스에서 해싱을 기다리는 로그인 수                                                  |
| `login_queue_enqueued_total`         | 대기열에 등록된 누적 건수                                                                  |
| `login_queue_admitted_total`         | 해싱을 마치고 결과가 기록된 누적 건수                                                      |
| `login_queue_rejected_total{reason}` | 거부 사유별 누적 건수 (`full`/`expired`/`unavailable`)                                     |
| `login_hash_seconds`                 | 해싱 1회 소요 시간. `login.queue.permits` 와 `estimated-hash-duration` 을 정하는 실측 근거 |

배포 후 `login_hash_seconds` 의 p50 을 보고 `login.queue.estimated-hash-duration` 을 맞추세요. 기본값 150ms 는 **추정치**입니다. 인스턴스가 t 계열 버스터블이면 CPU 크레딧 소진 후 베이스라인으로 throttle 되므로 실제 해시 시간이 크게 늘어납니다 (CloudWatch `CPUCreditBalance` 확인).

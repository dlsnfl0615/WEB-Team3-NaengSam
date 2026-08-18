# 부하테스트 요약

상세는 [README.md](README.md). 여기는 짧게.

## 실행

Redis 먼저. 로그인이 전부 이걸 탄다 — 없으면 로그인 단계에서 통째로 실패한다.
저장소의 `redis/docker-compose.yml`은 EC2 전용이라 로컬에서는 쓰지 않는다(README 참고).

```bash
docker run -d --name symboorm-redis -p 6379:6379 redis:7-alpine \
  redis-server --maxmemory 64mb --maxmemory-policy noeviction --appendonly no
redis-cli ping   # PONG
```

그다음 백엔드. **매번 재시작** — 엔진 인메모리라 이전 런 상태 되살아난다.
`bootRun`은 도커를 안 띄워 힙 제한이 없으므로, 운영(192MB)과 같은 예산으로 jar를 직접 띄운다.

```bash
cd backend
./gradlew bootJar
set -a; source .env; set +a          # 빼면 SOLAPI_ENABLED 미해결로 기동 실패
KAKAO_ENABLED=false KAKAO_DEV_ZONE_MODE=national \
java -Duser.timezone=Asia/Seoul -Xms192m -Xmx192m -XX:MaxMetaspaceSize=256m \
     -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -Xss512k \
     -XX:+HeapDumpOnOutOfMemoryError -jar build/libs/*.jar
```

제한 없이 빠르게 확인만 할 때: `set -a; source .env; set +a && ./gradlew bootRun`
(단 그 수치는 용량 산정에 쓰지 않는다 — 힙이 운영의 20배가 넘는다.)

프론트(WATCH=1일 때): `cd frontend && npm run dev`

부하:

```bash
cd matchingtest-cell
npm install
cp config/env.example config/.env.local
cp config/users.example.json config/users.json
npm run loadtest
```

`.env.local`의 `DB_URL`은 백엔드와 같은 H2 파일을 보도록 이미 맞춰져 있다(`../backend/data/naengsam`).

단계만 다시: `npm run db:reset` · `seed` · `drive` · `watch`.

## 설정

`config/.env.local`. 규모 넷이 전부다.

| 변수           | 뜻                                |
| -------------- | --------------------------------- |
| `DREAMI_COUNT` | 드리미 계정 수. **완주 상한이다** |
| `BOORMI_COUNT` | 부르미 계정 수                    |
| `ORDER_COUNT`  | 주문 수                           |
| `ORDER_RATE`   | 초당 주문 생성                    |

브라우저 검증은 `WATCH=1` + `WATCH_DREAMI` / `WATCH_BOORMI`. 계정은 `config/users.json`. 모자라면 즉시 중단.

수요 = `ORDER_COUNT + WATCH_BOORMI`. 공급 = `DREAMI_COUNT + WATCH_DREAMI`. 같으면 굶는다. 10건쯤 여유 둔다.

## 리포트

`result/<타임스탬프>.{json,md}`. 절 번호로 읽는다.

| 절  | 뜻                                        |
| --- | ----------------------------------------- |
| 2   | API 응답시간. 톰캣 + 트랜잭션 + DB        |
| 3   | 요청 발신 → SSE 도착. 매칭 엔진 경로 전체 |
| 6   | 유실 알람. 0 아니면 실패                  |
| 7   | DB 대조. 엉뚱한 배차 여기서 잡힌다        |
| 8   | 실클라이언트. 머리줄의 `10/10`부터 본다   |

3절 ≫ 2절 = 서버 느린 게 아니다. 배치 산술이다 — 배치창 2초 + 주문당 `max-concurrent-offers` 팬아웃. 주문 몰리면 웨이브 밀린다.

3절 끝 harness 이벤트 루프 지연 먼저 본다. 크면 SSE 수신 시각 자체가 밀린 것.

종료 코드: 0 통과 · 1 유실/불일치/브라우저 실패 · 2 중단.

## 유실 판정

선행 사실 뒤에 올 이벤트가 제한 시간 안에 안 오면 유실.

| 스테이지            | 기다리는 것          | 제한             |
| ------------------- | -------------------- | ---------------- |
| `오퍼_미발송`       | `offer_popup`        | `T_OFFER` 15s    |
| `dreami_info_유실`  | `dreami_info`        | `T_INFO` 10s     |
| `offer_closed_유실` | 형제 오퍼 마감       | `T_CLOSED` 10s   |
| `배달시작알림_유실` | `delivery_started_*` | `T_DELIVERY` 10s |
| `만료알림_유실`     | TTL 30초 뒤 마감     | `T_EXPIRE` 45s   |

제한 시간 안 지났으면 유실 아니라 **판정보류**. 판정보류 뜨면 `DRAIN_MS` 늘린다.

완주 상한에 막힌 주문, `offer_error`로 마감 통지받은 오퍼, 브라우저 드리미가 이긴 주문은 유실 아니다. 따로 센다.

## 실서버

```bash
npm run tunnel        # 별도 터미널. 웹 인스턴스 경유 필수
npm run prod
npm run prod:cleanup  # 잔여 주문 조회
```

`config/.env.prod` 쓴다. 운영 DB는 INSERT 못 하니 `USE_EXISTING_ACCOUNTS=1` + `EXISTING_EMAIL_LIKE` + `EXISTING_PASSWORD`로 기존 계정 빌려 쓴다. DB 작업은 SELECT뿐.

주문은 실 API로 만든다. `ORDER_COUNT + WATCH_BOORMI`만큼 실주문·실결제가 실계정 명의로 남는다.

## 함정

- **`RESET_DB` 기본값이 `1`이다.** 운영 설정 파일에 `RESET_DB=0`을 반드시 명시한다.
- `DB_URL`이 localhost 아니면 초기화에 `ALLOW_REMOTE_RESET=1` 필요.
- SSH 터널은 호스트가 `127.0.0.1`로 보여 위 가드가 그냥 통과한다. `REMOTE_DB=1` 함께 준다.
- **취소는 되돌릴 수 없다.** `CLEANUP_CONFIRM=1` 없이는 dry-run만. `EXISTING_EMAIL_LIKE`가 넓으면 실사용자 주문까지 취소되니 목록 먼저 확인한다.
- `CLEANUP`은 리포트 낸 **뒤에** 돈다. 먼저 돌면 `IN_PROGRESS` 검증이 거짓 실패.
- 배포 대상은 `CORS_CHECK=0` `KAKAO_CHECK=0`. 안 끄면 거짓 실패로 중단된다.
- 오퍼 배정에 거리 필터 없다. 전역 FIFO. 드리미별 분포 불균등은 버그 아니다.

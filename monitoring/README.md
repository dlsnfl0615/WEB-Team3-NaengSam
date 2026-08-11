# 모니터링 (Actuator + Prometheus + Grafana)

배포 환경의 스프링 상태를 지속적으로 관측하기 위한 구성입니다. 관련 이슈: #193

이 폴더는 **백엔드 배포 파이프라인과 완전히 분리**되어 있습니다. CI 는 `backend/` 만 빌드해서 이미지를 올리고, 모니터링 스택은 이 폴더를 그대로 모니터링 인스턴스에 복사해 별도 compose 로 띄웁니다. 여기 파일을 고쳐도 백엔드 이미지는 다시 빌드되지 않고, 백엔드를 재배포해도 모니터링은 건드릴 필요가 없습니다.

## 구조

모니터링은 **백엔드와 다른 인스턴스**에서 돕니다. 같은 VPC 안에 있으며, 프라이빗 IP 로만 통신합니다.

```
[백엔드 인스턴스]                          [모니터링 인스턴스]
 backend(8080)  API                        prometheus  호스트에 공개하지 않음
 backend(8081)  액츄에이터                  grafana(3000)  대시보드. 호스트에 공개
       └──── VPC 프라이빗 IP:8081 ◀── 15초마다 스크레이프 ────┘
       (보안그룹: 8081 은 모니터링 인스턴스 SG 에서만 허용)
```

도커 네트워크는 인스턴스 경계를 넘지 못하므로 두 스택은 네트워크를 공유하지 않습니다. 연결점은 `prometheus.yml` 에 적힌 백엔드 프라이빗 IP 하나뿐이고, 각 compose 는 자기 기본 네트워크만 씁니다. 백엔드가 내려가 있으면 prometheus 타깃이 `down` 으로 잡힐 뿐 모니터링 스택은 계속 돕니다.

액츄에이터를 8081 로 분리해 두었지만, 인스턴스가 나뉘면서 이 포트는 백엔드 호스트에 열려야 합니다. EC2 는 공인 IP 를 IGW 가 NAT 하므로 **바인딩 주소로는 외부 접근을 막을 수 없습니다** — 8081 인바운드를 모니터링 인스턴스 보안그룹으로만 제한하는 것이 유일한 방어선입니다. **이 규칙을 먼저 넣고 백엔드를 띄우세요.** 규칙이 `0.0.0.0/0` 이면 메트릭 엔드포인트가 인터넷에 그대로 열립니다.

노출 엔드포인트는 `health`, `info`, `prometheus` 세 개로 제한되어 있습니다 (`application.properties` 의 `management.endpoints.web.exposure.include`).

## 로컬에서 확인

```bash
./gradlew bootRun

curl -s localhost:8081/actuator/health
curl -s localhost:8081/actuator/prometheus | grep jvm_memory_used_bytes | head

# API 를 한 번 호출한 뒤 HTTP 메트릭이 쌓이는지 확인
curl -s localhost:8081/actuator/prometheus | grep http_server_requests_seconds_count
```

`localhost:8080/actuator/health` 는 404 가 정상입니다 (액츄에이터가 8081 에만 있음).

`MANAGEMENT_PORT` 환경변수로 포트를 바꿀 수 있습니다 (기본 8081).

### 로컬에서 Grafana 까지 띄우기

`docker-compose.local.yml` 은 로컬 전용입니다. 백엔드는 도커에 넣지 않고, 호스트에서 `bootRun` 으로 띄운 것을 prometheus 가 `host.docker.internal:8081` 로 긁습니다(`prometheus.local.yml`). `.env` 도 필요 없습니다.

```bash
cd monitoring
docker compose -f docker-compose.local.yml up -d

# 다른 터미널
cd backend && ./gradlew bootRun

# http://localhost:3000  (admin / admin)
```

백엔드를 나중에 띄워도 됩니다 — 그 전까지는 타깃이 `down` 으로 잡힐 뿐입니다. 수집이 되는지는 Grafana Explore 에서 `up{job="symboorm-backend"}` 가 `1` 인지로 확인합니다(로컬도 prometheus 9090 은 호스트에 열지 않습니다).

배포용 스택과는 compose 프로젝트명·컨테이너명·볼륨이 모두 분리되어 있어 서로 간섭하지 않습니다. Grafana provisioning(데이터소스·대시보드)은 배포용과 같은 파일을 그대로 씁니다.

## 배포 서버 적용

두 인스턴스에 각각 배치합니다. 서로를 상대경로로 참조하지 않으므로 폴더 위치는 자유입니다.

```
[백엔드 인스턴스]  ~/symboorm/backend/      docker-compose.yml + .env  (CI 이미지를 pull)
[모니터링 인스턴스] ~/symboorm/monitoring/   이 폴더를 통째로 복사 + .env
```

1. **보안그룹 먼저.** 백엔드 인스턴스 보안그룹 인바운드에 규칙을 추가합니다.

   | 타입 | 포트 | 소스                             |
   | ---- | ---- | -------------------------------- |
   | TCP  | 8081 | **모니터링 인스턴스의 보안그룹** |

   소스를 `0.0.0.0/0` 으로 두면 액츄에이터가 인터넷에 열립니다. 이 규칙을 넣기 전에 백엔드를 8081 공개 상태로 띄우지 마세요.

2. 백엔드 인스턴스에서 백엔드를 기동합니다(`backend/docker-compose.yml` 이 8081 을 호스트에 공개합니다).

```bash
cd ~/symboorm/backend && docker compose pull && docker compose up -d
```

3. 이 `monitoring/` 폴더를 **모니터링 인스턴스**로 복사합니다.

```bash
scp -r monitoring <모니터링 인스턴스>:~/symboorm/
```

4. `monitoring/prometheus.yml` 의 타깃을 백엔드 인스턴스의 **VPC 프라이빗 IP** 로 고칩니다. 커밋된 값은 플레이스홀더(`10.0.0.0:8081`)입니다.

```yaml
- targets: ["<백엔드 프라이빗 IP>:8081"]
```

5. `monitoring/.env` 를 만들어 `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` 를 채웁니다(`.env.example` 참고). **기본값 admin/admin 을 그대로 두면 안 됩니다** — Grafana 는 3000 포트가 외부에 열려 있습니다. 가능하면 보안그룹에서 3000 을 팀 IP 로만 제한하세요.
6. 기동 및 확인:

```bash
cd ~/symboorm/monitoring
# 먼저 도달성 확인 — 여기서 실패하면 보안그룹이나 IP 문제다
curl -s http://<백엔드 프라이빗 IP>:8081/actuator/health

docker compose up -d
docker compose logs prometheus   # 스크레이프 에러가 없어야 한다
```

7. `http://<모니터링 인스턴스 IP>:3000` 접속 → Prometheus 데이터소스는 자동 등록되어 있습니다.
8. Explore 에서 `up{job="symboorm-backend"}` 가 `1` 이면 수집 정상입니다.
9. `SymBoorm` 폴더의 **SymBoorm — HTTP & SSE** 대시보드는 자동 등록되어 있습니다(아래 참고).
10. JVM·HikariCP 는 Dashboards → New → Import → ID **19004** (Spring Boot 3.x Statistics) → 데이터소스로 Prometheus 선택. JVM 을 더 파고들려면 ID **4701** (JVM Micrometer)도 함께 import 하세요. ID **6756** 은 Spring Boot 2 용이라 메트릭명이 달라 패널이 깨집니다.

### 타깃이 `down` 일 때

순서대로 확인합니다.

1. `monitoring/prometheus.yml` 의 IP 가 백엔드 인스턴스의 **현재** 프라이빗 IP 인지 (인스턴스를 재생성하면 바뀝니다)
2. 백엔드 보안그룹에 `TCP 8081 ← 모니터링 SG` 인바운드 규칙이 있는지
3. 백엔드 compose 가 `8081:8081` 을 공개하고 있는지 (`docker compose ps` 로 포트 확인)
4. 백엔드 인스턴스에서 `curl -s localhost:8081/actuator/prometheus` 가 응답하는지

메트릭에는 `application="quick"` 라벨이 붙으므로 대시보드에서 인스턴스 구분에 쓸 수 있습니다.

## SSE 메트릭

SSE 는 `SseEmitter` 기반 async 요청이라 `http_server_requests_seconds` 에는 **연결이 끊길 때 한 번만** 기록됩니다. 연결 유지 중의 상태는 안 보이므로 `SseEmitterRegistry` 에서 직접 계측합니다.

| Prometheus 이름                | 타입    | 라벨                                                               | 의미                                                                              |
| ------------------------------ | ------- | ------------------------------------------------------------------ | --------------------------------------------------------------------------------- |
| `sse_connections_active`       | Gauge   | —                                                                  | 현재 유지 중인 연결 수                                                            |
| `sse_connections_opened_total` | Counter | —                                                                  | 누적 연결 수립 수                                                                 |
| `sse_connections_closed_total` | Counter | `reason` = `completion`/`timeout`/`error`/`send_failed`/`replaced` | 누적 연결 종료 수. `replaced` 는 같은 사용자의 재연결로 기존 연결이 덮어써진 경우 |
| `sse_events_sent_total`        | Counter | `event` (SSE 이벤트 이름)                                          | 전송 성공한 이벤트 수                                                             |
| `sse_events_dropped_total`     | Counter | `reason` = `not_connected`/`send_failed`                           | 전송하지 못한 이벤트 수                                                           |

**주의 — `sse_connections_active` 는 실제보다 크게 나올 수 있습니다.** 서버는 클라이언트가 조용히 끊은 것을 다음 전송을 시도할 때에야 알아챕니다. heartbeat 이 없으므로, 이벤트가 없는 사용자의 죽은 연결은 타임아웃(1시간)까지 active 로 남습니다. `sse_connections_closed_total{reason="send_failed"}` 가 꾸준히 오르면 죽은 연결이 쌓이고 있다는 신호입니다.

## 대시보드

| 대시보드                              | 등록 방식                                                               | 이유                                                                                                                         |
| ------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **SymBoorm — HTTP & SSE**             | `grafana/provisioning/dashboards/symboorm-http-sse.json` 으로 자동 등록 | `sse_*` 는 우리가 만든 커스텀 메트릭이라 어떤 커뮤니티 대시보드에도 없습니다. ID 로 재생산이 불가능하므로 JSON 을 커밋합니다 |
| Spring Boot 3.x Statistics (ID 19004) | UI 에서 import                                                          | ID 만 있으면 누구나 동일하게 재생산됩니다. 수천 줄 자동생성 JSON 을 커밋해도 리뷰가 불가능합니다                             |
| JVM (Micrometer) (ID 4701)            | UI 에서 import                                                          | 위와 같음                                                                                                                    |

프로비저닝 대시보드는 `allowUiUpdates: false` 입니다. UI 에서 고쳐도 재기동하면 되돌아가므로, 바꾸려면 **JSON 파일을 고쳐 커밋**하세요(UI 에서 편집 → Export → JSON 붙여넣기).

Tomcat 스레드풀 패널(19004)은 기본으로 비어 있습니다. 필요하면 `server.tomcat.mbeanregistry.enabled=true` 를 켜세요.

## 직접 쓰는 쿼리

HTTP 지연 히스토그램은 `application.properties` 의 `management.metrics.distribution.percentiles-histogram.http.server.requests=true` 로 켜져 있습니다(버킷 범위 1ms~10s).

**SSE 엔드포인트는 HTTP 쿼리에서 제외하세요.** 연결 유지 시간이 최대 1시간이라 종료 시점에 `+Inf` 버킷으로 기록되어 지연 그래프를 왜곡합니다.

```promql
# 엔드포인트별 p95 (SSE 제외)
histogram_quantile(0.95, sum by (le, uri) (
  rate(http_server_requests_seconds_bucket{uri!~".*subscribe.*"}[5m])))

# 엔드포인트별 RPS
sum by (uri) (rate(http_server_requests_seconds_count{uri!~".*subscribe.*"}[1m]))

# 5xx 비율
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# 처리 중 요청 수(SSE 포함)
sum(http_server_requests_active_seconds_count)

# SSE
sse_connections_active
sum by (event) (rate(sse_events_sent_total[1m]))
sum by (reason) (rate(sse_connections_closed_total[5m]))
rate(sse_events_dropped_total[5m])
```

## 보관 기간

Prometheus 는 기본 15일치를 `prometheus-data` 볼륨에 보관합니다. 더 길게 보려면 compose 의 prometheus 서비스에 `command: --storage.tsdb.retention.time=30d` 를 추가하세요.

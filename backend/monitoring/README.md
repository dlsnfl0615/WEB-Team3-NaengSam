# 모니터링 (Actuator + Prometheus + Grafana)

배포 환경의 스프링 상태를 지속적으로 관측하기 위한 구성입니다. 관련 이슈: #193

## 구조

```
backend(8080)          외부 공개 — API
backend(8081)          액츄에이터. 호스트에 공개하지 않음
  └─ prometheus        /actuator/prometheus 를 15초마다 스크레이프. 호스트에 공개하지 않음
       └─ grafana(3000)  대시보드. 호스트에 공개
```

액츄에이터 포트를 분리하고 호스트에 공개하지 않기 때문에 메트릭 엔드포인트가 공인 IP로 노출되지 않습니다. 덕분에 Spring Security 를 도입하지 않아도 됩니다.

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

## 배포 서버 적용

1. compose 파일과 같은 폴더에 `.env` 와 `monitoring/` 디렉토리를 둡니다.
2. `.env` 에 `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` 를 채웁니다. **기본값 admin/admin 을 그대로 두면 안 됩니다** — Grafana 는 3000 포트가 외부에 열려 있습니다. 가능하면 보안그룹에서 3000 을 팀 IP 로만 제한하세요.
3. 기동 및 확인:

```bash
docker compose pull
docker compose up -d
docker compose logs prometheus   # 스크레이프 에러가 없어야 한다
```

4. `http://<서버IP>:3000` 접속 → Prometheus 데이터소스는 자동 등록되어 있습니다.
5. Explore 에서 `up{job="symboorm-backend"}` 가 `1` 이면 수집 정상입니다.
6. Dashboards → New → Import → ID **4701** (JVM Micrometer) 입력 → 데이터소스로 Prometheus 선택. JVM 힙·GC·스레드·HTTP 그래프를 바로 볼 수 있습니다.

메트릭에는 `application="quick"` 라벨이 붙으므로 대시보드에서 인스턴스 구분에 쓸 수 있습니다.

## 보관 기간

Prometheus 는 기본 15일치를 `prometheus-data` 볼륨에 보관합니다. 더 길게 보려면 compose 의 prometheus 서비스에 `command: --storage.tsdb.retention.time=30d` 를 추가하세요.

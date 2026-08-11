# 부하테스트 실행 순서 (EC2 대상)

> 오늘 준비, 내일 k6용 EC2 인스턴스 승인나면 이 순서대로 진행.

## 준비물

- k6용 EC2 인스턴스 1개(승인 대기 중) — WAS와 같은 리전 추천.
- WAS/DB 인스턴스 접속 정보(SSH 또는 3306 접근 가능한 경로), DB 계정.
- 이 저장소의 `loadtest/` 폴더.

## 0. (오늘 가능하면 지금) DB 백업

WAS/DB 인스턴스에 이미 접속 권한이 있다면 k6 인스턴스 승인을 안 기다리고 지금 떠도 된다 —
백업은 새 인스턴스와 무관하다.

```bash
DB_HOST=<DB 프라이빗 IP 또는 127.0.0.1> DB_USER=root DB_NAME=<실제 DB명> \
  bash loadtest/db-backup.sh
```
`loadtest/backup-YYYYMMDD-HHMMSS.sql` 파일이 생기면 성공. **이 파일을 로컬로도 복사해두는 걸 추천**
(인스턴스에만 있으면 인스턴스에 문제 생겼을 때 같이 날아감).

## 1. k6 인스턴스 승인 후 — 셋업

```bash
# k6 인스턴스에 SSH 접속
curl -s https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

git clone <저장소 주소>
cd WEB-Team3-NaengSam
```

## 2. 콜 등록 (매칭엔진에 데이터 채우기)

```bash
BASE_URL=https://<WAS 도메인> bash loadtest/open-calls.sh
```

## 3. k6 실행 — 낮은 VUS부터 단계적으로 올리기

```bash
k6 run -e BASE_URL=https://<WAS 도메인> -e VUS=10  -e DURATION=1m loadtest/k6/matching-nearby-calls.js | tee result-10.txt
k6 run -e BASE_URL=https://<WAS 도메인> -e VUS=50  -e DURATION=1m loadtest/k6/matching-nearby-calls.js | tee result-50.txt
k6 run -e BASE_URL=https://<WAS 도메인> -e VUS=100 -e DURATION=1m loadtest/k6/matching-nearby-calls.js | tee result-100.txt
```
같은 시간대에 배포서버의 Grafana(`SymBoorm — HTTP & SSE`)도 열어서 서버 관점 p95/RPS를 같이 관찰.

## 4. 테스트 종료 후 — DB 원복

```bash
DB_HOST=<DB 프라이빗 IP> DB_USER=root DB_NAME=<실제 DB명> \
  bash loadtest/db-restore.sh loadtest/backup-YYYYMMDD-HHMMSS.sql
```

## 체크리스트

- [ ] DB 백업 완료 (`loadtest/backup-*.sql`, 로컬에도 복사)
- [ ] k6 인스턴스 셋업 완료
- [ ] 콜 등록 완료 (`open-calls.sh` 에러 없이 끝남)
- [ ] VUS 10 → 50 → 100 순서로 실행, 결과 파일로 저장
- [ ] Grafana로 서버 쪽 지표 같이 확인
- [ ] DB 원복 완료

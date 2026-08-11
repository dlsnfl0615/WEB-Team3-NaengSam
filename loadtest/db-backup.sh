#!/bin/bash
# 부하테스트 전 MySQL 백업(mysqldump). WAS/DB 인스턴스에 SSH로 들어가서 로컬로 실행하거나,
# 3306 포트에 접근 가능한 곳에서 DB_HOST를 그 프라이빗 IP로 바꿔서 실행한다.
#
# 사용법:
#   DB_HOST=127.0.0.1 DB_USER=root DB_NAME=<실제 DB명> bash loadtest/db-backup.sh
set -e

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_USER=${DB_USER:-root}
DB_NAME=${DB_NAME:?"DB_NAME 환경변수를 지정하세요 (예: DB_NAME=symboorm)"}
OUT=${OUT:-loadtest/backup-$(date +%Y%m%d-%H%M%S).sql}

echo "백업 대상: $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME → $OUT"
# --single-transaction: InnoDB 기준 잠금 없이 일관된 스냅샷을 뜬다(서비스 중단 불필요).
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p \
  --single-transaction --routines --triggers \
  "$DB_NAME" > "$OUT"

echo "완료: $OUT ($(du -h "$OUT" | cut -f1))"

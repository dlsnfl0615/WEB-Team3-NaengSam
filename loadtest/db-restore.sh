#!/bin/bash
# 부하테스트 후 db-backup.sh로 떠둔 덤프로 원복한다.
# 부하테스트 중 새로 생긴 행이 남아있는 상태에서 그냥 부어넣으면 PK/UNIQUE 충돌이 나므로,
# 스키마를 통째로 비우고(DROP) 다시 만든 뒤 덤프를 복원한다 — 되돌리는 대상은 이 스키마 하나뿐이고
# 인스턴스의 다른 상태(파일 등)는 건드리지 않는다.
#
# 사용법:
#   DB_HOST=127.0.0.1 DB_USER=root DB_NAME=<실제 DB명> bash loadtest/db-restore.sh loadtest/backup-YYYYMMDD-HHMMSS.sql
set -e

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_USER=${DB_USER:-root}
DB_NAME=${DB_NAME:?"DB_NAME 환경변수를 지정하세요"}
DUMP=${1:?"복원할 덤프 파일 경로를 인자로 주세요 (예: bash loadtest/db-restore.sh loadtest/backup-....sql)"}

if [ ! -f "$DUMP" ]; then
  echo "덤프 파일을 찾을 수 없습니다: $DUMP"
  exit 1
fi

echo "경고: $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME 스키마를 통째로 비우고 $DUMP 로 복원합니다."
read -r -p "계속하려면 yes 입력: " confirm
if [ "$confirm" != "yes" ]; then
  echo "취소됨"
  exit 1
fi

mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p -e \
  "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\`;"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p "$DB_NAME" < "$DUMP"

echo "복원 완료"

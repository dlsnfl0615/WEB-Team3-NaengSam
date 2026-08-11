#!/bin/bash
# 부하테스트용 "열린 콜"을 미리 만들어두는 스크립트.
# 백엔드가 이미 떠 있는 상태에서(다른 터미널에서 ./gradlew bootRun) 이 스크립트를 실행한다:
#   bash loadtest/open-calls.sh
set -e

BASE=${BASE_URL:-http://localhost:8080}

open_calls() {
  local email=$1 count=$2
  local jar=$(mktemp)
  curl -s -c "$jar" -X POST "$BASE/api/v1/user/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"string\"}" > /dev/null

  for i in $(seq 1 "$count"); do
    echo "[$email] 콜 $i/$count 등록:"
    curl -s -b "$jar" -X POST "$BASE/api/v1/boormi/calls" \
      -H 'Content-Type: application/json' \
      -d '{
        "originAddressLine1": "서울 강남구 테헤란로 152",
        "destinationAddressLine1": "서울 강남구 역삼로 180",
        "itemName": "서류봉투",
        "itemCd": "DOCUMENT"
      }'
    echo
  done
  rm -f "$jar"
}

open_calls boormi1@test.test 5
open_calls boormi2@test.test 5

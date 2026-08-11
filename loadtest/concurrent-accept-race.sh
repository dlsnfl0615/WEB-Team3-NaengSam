#!/bin/bash
# 같은 콜(주문)에 여러 드리미가 동시에 "수락"을 누르는 상황을 재현한다.
# DreamiService.acceptOffer가 order.markPendingBoormiConfirmation() 전에 주문 상태를 확인하지
# 않고(Orders.java) @Version 같은 낙관적 락도 없어서, 동시에 accept가 들어오면 여러 건이 동시에
# "성공"할 수 있는지를 이 스크립트로 확인한다.
#
# 주의: 매칭엔진은 한 라운드에 최대 3명(MatchingService.MAX_OFFER_COUNT)에게만 동시에 오퍼를
# 보낸다. dreami 계정 10개를 다 온라인으로 등록해도, 실제로 동시에 경쟁하는 건 그중(엔진이
# 가장 가까운 순으로 뽑은) 최대 3명이다. "많은 드리미"라는 조건 자체(10명이 동시에 후보로 존재)는
# 그대로 반영되고, 실제 accept 경쟁은 그 라운드에서 오퍼를 받은 만큼만 일어난다.
#
# bash 3.2(macOS 기본)에서도 동작하도록 연관배열/mapfile 없이 짰다.
# 의존성: jq (brew install jq / apt install -y jq)
# 사용법: BASE_URL=http://localhost:8080 bash loadtest/concurrent-accept-race.sh
set -e

BASE=${BASE_URL:-http://localhost:8080}
ORIGIN_LAT=37.49800000
ORIGIN_LNG=127.02760000

TMPDIR=$(mktemp -d)
echo "작업 디렉터리: $TMPDIR"

login() {
  local email=$1 jar=$2
  curl -s -c "$jar" -X POST "$BASE/api/v1/user/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"string\"}" > /dev/null
}

# ------------------------------------------------------------
# 1. 드리미 10명 로그인 + 온라인 등록 (콜을 열기 전에 먼저 등록해야 매칭엔진이
#    첫 라운드부터 이들을 후보로 본다)
# ------------------------------------------------------------
DREAMI_IDS=()   # 인덱스 1~10 -> dreamiId
DREAMI_JARS=()  # 인덱스 1~10 -> 쿠키 jar 경로

for i in $(seq 1 10); do
  jar="$TMPDIR/dreami$i.jar"
  login "dreami$i@test.test" "$jar"
  id=$(curl -s -b "$jar" "$BASE/api/v1/user/me" | jq -r '.result.boormiId')
  DREAMI_IDS[$i]=$id
  DREAMI_JARS[$i]=$jar

  curl -s -b "$jar" -X POST "$BASE/api/v1/dreami/status/online" \
    -H 'Content-Type: application/json' \
    -d "{\"latitude\": $ORIGIN_LAT, \"longitude\": $ORIGIN_LNG}" > /dev/null
  echo "dreami$i 온라인 등록 요청 (id=$id)"
done

# 등록은 엔진 스레드가 큐에서 순차 처리하므로, 실제로 10명이 다 반영됐는지 확인하고 넘어간다.
for _ in $(seq 1 20); do
  ONLINE_COUNT=$(curl -s "$BASE/api/v1/debug/matching/dreamis" | jq 'length')
  if [ "$ONLINE_COUNT" -ge 10 ]; then
    echo "온라인 드리미 $ONLINE_COUNT 명 확인됨"
    break
  fi
  sleep 0.3
done

# ------------------------------------------------------------
# 2. 부르미가 콜 하나 등록
# ------------------------------------------------------------
BOORMI_JAR="$TMPDIR/boormi.jar"
login "boormi1@test.test" "$BOORMI_JAR"
ORDER_RESULT=$(curl -s -b "$BOORMI_JAR" -X POST "$BASE/api/v1/boormi/calls" \
  -H 'Content-Type: application/json' \
  -d '{
    "originAddressLine1": "서울 강남구 테헤란로 152",
    "destinationAddressLine1": "서울 강남구 역삼로 180",
    "itemName": "서류봉투",
    "itemCd": "DOCUMENT"
  }')
echo "콜 등록 응답: $ORDER_RESULT"
ORDER_ID=$(echo "$ORDER_RESULT" | jq -r '.result')
echo "orderId=$ORDER_ID"

# ------------------------------------------------------------
# 3. 매칭엔진이 오퍼를 만들 때까지 폴링
# ------------------------------------------------------------
GROUP=""
for _ in $(seq 1 20); do
  GROUP=$(curl -s "$BASE/api/v1/debug/matching/orders/$ORDER_ID/group")
  COUNT=$(echo "$GROUP" | jq '.offers | length')
  if [ "$COUNT" -gt 0 ]; then
    echo "오퍼 ${COUNT}건 생성됨"
    break
  fi
  sleep 0.5
done
echo "오퍼 그룹(경쟁 시작 전):"
echo "$GROUP" | jq .

OFFERS=()
while IFS= read -r line; do
  [ -n "$line" ] && OFFERS+=("$line")
done < <(echo "$GROUP" | jq -r '.offers[] | "\(.offerId) \(.dreamiId)"')

if [ ${#OFFERS[@]} -eq 0 ]; then
  echo "오퍼가 생성되지 않았습니다 — 온라인 등록/좌표를 확인하세요."
  exit 1
fi

# ------------------------------------------------------------
# 4. 오퍼를 받은 드리미들의 accept를 동시에 발사
# ------------------------------------------------------------
echo "=== ${#OFFERS[@]}명이 동시에 accept 호출 ==="
for entry in "${OFFERS[@]}"; do
  offer_id=${entry% *}
  dreami_id=${entry#* }

  jar=""
  for i in $(seq 1 10); do
    if [ "${DREAMI_IDS[$i]}" = "$dreami_id" ]; then
      jar=${DREAMI_JARS[$i]}
      break
    fi
  done
  if [ -z "$jar" ]; then
    echo "경고: dreami_id=$dreami_id 에 해당하는 세션을 못 찾음"
    continue
  fi

  (
    RESULT=$(curl -s -w ' [HTTP %{http_code}]' -b "$jar" -X POST "$BASE/api/v1/dreami/offers/$offer_id/accept")
    echo "[offer=$offer_id dreami=$dreami_id] $RESULT"
  ) &
done
wait

# ------------------------------------------------------------
# 5. 결과 확인
# ------------------------------------------------------------
echo "=== 경쟁 후 오퍼 그룹 상태 ==="
curl -s "$BASE/api/v1/debug/matching/orders/$ORDER_ID/group" | jq .
echo "각 offer의 status를 확인하세요 — 두 개 이상이 동시에 '수락됨'류 상태면 레이스가 실제로 발생한 것입니다."
echo "DB의 최종 진실은 ORDERS 테이블에서 직접 확인하세요:"
echo "  SELECT order_cd, dreami_id FROM ORDERS WHERE order_id = X'${ORDER_ID//-/}';"

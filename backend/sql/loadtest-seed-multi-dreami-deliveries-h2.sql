-- ============================================================
-- loadtest-seed-multi-dreami-deliveries.sql의 H2(로컬, MODE=MySQL) 호환 버전.
--
-- 원본은 실제 부하테스트 대상(EC2의 진짜 MySQL)을 겨냥해서 MySQL 전용 문법을 쓰는데,
-- 그 문법 중 일부는 H2의 MySQL 호환 모드에서도 그대로 동작하지 않는다(직접 실행해서 확인한 차이):
--   1) `UUID_TO_BIN(UUID())`      → H2엔 이 함수가 없음. `CAST(RANDOM_UUID() AS BINARY(16))`로 대체.
--   2) `ELT(n, 'a','b','c','d')`  → H2엔 이 함수가 없음. `CASE n WHEN 0 THEN 'a' ... END`로 대체.
--   3) `DATE_SUB(dtm, INTERVAL n MINUTE)` → H2엔 이 함수가 없음. `DATEADD('MINUTE', -n, dtm)`로 대체
--      (음수 값을 넣어 "빼기"를 표현).
--   4) `SET SESSION cte_max_recursion_depth = ...` → H2는 이런 세션 변수가 없어 문법 오류가 난다.
--      H2는 재귀 깊이 제한이 없어(직접 5000단계 재귀로 확인) 이 줄 자체를 지운다.
--   5) `WITH RECURSIVE seq AS (...)` → H2는 재귀 CTE에 컬럼 목록을 명시해야 한다(`seq(n) AS (...)`).
--      컬럼 목록이 없으면 "expected '('" 문법 오류가 난다. MySQL은 첫 분기에서 컬럼을 추론하지만
--      H2는 추론하지 않으므로 모든 재귀 CTE에 `(n)`을 붙인다.
-- 나머지 구조(계정 범위, 컬럼 목록, INSERT 대상 테이블, 정리 방법)는 원본과 동일하다.
--
-- 원본 loadtest-seed-multi-dreami-deliveries.sql의 헤더 주석(대역 선택 이유·전제조건·정리 방법)을
-- 그대로 참고할 것 — 여기서는 문법 차이만 다룬다.
--
-- 전제조건: backend/sql/test-seed-accounts.sql을 먼저 실행해서 dreami(START)..dreami(START+COUNT-1) /
-- boormi2@test.test 계정이 이미 있어야 한다.
--
-- 생성된 행은 item_detail = '부하테스트용 더미 주문(다계정)'(ORDERS)으로 표시해뒀다. 지우려면:
--   DELETE FROM `MONEY_TX` WHERE wallet_id IN (
--       SELECT w.wallet_id FROM `WALLET` w JOIN `BOORMI` b ON b.boormi_id = w.boormi_id
--       WHERE b.email LIKE 'dreami4%@test.test') AND type = 'SETTLEMENT';
--   DELETE FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)';
-- ============================================================

SET @orders_per_account = 5000;
SET @dreami_account_start = 400; -- 다른 loadtest 스크립트와 안 겹치는 대역. k6의 ACCOUNT_START와 맞춰야 함.
SET @dreami_account_count = 20; -- dreami400 ~ dreami419

SET @boormi2_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = 'boormi2@test.test');
SET @first_dreami_email = CONCAT('dreami', @dreami_account_start, '@test.test');
SET @first_dreami_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = @first_dreami_email);
SET @last_dreami_email = CONCAT('dreami', @dreami_account_start + @dreami_account_count - 1, '@test.test');
SET @last_dreami_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = @last_dreami_email);

-- 범위 양 끝(dreami(START), dreami(START+COUNT-1))만 확인한다 — 계정이 이 DB에 없으면 여기서 바로 알아챌 수 있게.
SELECT
    CASE WHEN @boormi2_id IS NULL THEN 'ERROR: boormi2@test.test 계정을 찾을 수 없습니다 — test-seed-accounts.sql을 먼저 실행하세요.'
         WHEN @first_dreami_id IS NULL THEN CONCAT('ERROR: ', @first_dreami_email, ' 계정을 찾을 수 없습니다 — test-seed-accounts.sql을 먼저 실행하세요.')
         WHEN @last_dreami_id IS NULL THEN CONCAT('ERROR: ', @last_dreami_email, ' 계정을 찾을 수 없습니다 — @dreami_account_count를 줄이거나 계정을 더 만드세요.')
         ELSE 'OK'
    END AS precheck;

INSERT INTO `ORDERS`
(`order_id`, `boormi_id`, `item_name`, `item_cd`, `item_detail`, `delivery_amount`, `order_cd`, `delivery_eta`, `delivery_distance`,
 `origin_latitude`, `origin_longitude`, `origin_alias`, `origin_address_line_1`,
 `destination_latitude`, `destination_longitude`, `destination_alias`, `destination_address_line_1`,
 `delivery_request_dtm`, `dreami_id`)
SELECT
    CAST(RANDOM_UUID() AS BINARY(16)),
    @boormi2_id, -- boormi2@test.test (요청자, 모든 계정 공통 고정)
    CASE seq_numbers.n % 4
        WHEN 0 THEN '서류봉투'
        WHEN 1 THEN '샘플 상품'
        WHEN 2 THEN '소포'
        ELSE '기타 물품'
    END,
    CASE seq_numbers.n % 4
        WHEN 0 THEN 'DOCUMENT'
        WHEN 1 THEN 'SAMPLE'
        WHEN 2 THEN 'PACKAGE'
        ELSE 'ETC'
    END,
    '부하테스트용 더미 주문(다계정)',
    5000 + (seq_numbers.n % 5) * 1000,
    'COMPLETED',
    18,
    1500,
    37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
    37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
    DATEADD('MINUTE', -seq_numbers.n, TIMESTAMP '2026-08-10 10:00:00'),
    accounts.dreami_id
FROM (
    -- dreami(START)..dreami(START+COUNT-1) 각 계정의 실제 boormi_id(=dreami_id)를 이메일로 조회.
    WITH RECURSIVE acct_seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n + 1 FROM acct_seq WHERE n < @dreami_account_count
    )
    SELECT b.`boormi_id` AS dreami_id
    FROM acct_seq
    JOIN `BOORMI` AS b ON b.`email` = CONCAT('dreami', @dreami_account_start + acct_seq.n - 1, '@test.test')
) AS accounts
CROSS JOIN (
    WITH RECURSIVE order_seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n + 1 FROM order_seq WHERE n < @orders_per_account
    )
    SELECT n FROM order_seq
) AS seq_numbers;

-- 드리미 대시보드(DreamiService.getDashboard)의 이번 달/최근 6개월 수익 집계는 ORDERS가 아니라
-- MONEY_TX(정산 원장)를 본다 — WALLET.boormi_id로 조인해서 type=SETTLEMENT, status=SETTLED,
-- created_dtm 범위로 집계함(moneyTxRepository.aggregateByBoormiIdAndTypeBetween). 위에서 채운
-- ORDERS만으로는 이 집계에 전혀 안 잡히므로, 계정마다 정산 완료 거래도 같이 채운다.
-- order_id는 대시보드 집계 쿼리가 안 쓰므로 단순화를 위해 특정 주문과 연결하지 않는다(NULL 허용 컬럼).
INSERT INTO `MONEY_TX`
(`money_tx_id`, `status`, `amount`, `created_dtm`, `type`, `order_id`, `wallet_id`)
SELECT
    CAST(RANDOM_UUID() AS BINARY(16)),
    'SETTLED',
    5000 + (seq_numbers.n % 5) * 1000,
    DATEADD('MINUTE', -seq_numbers.n, TIMESTAMP '2026-08-10 10:00:00'),
    'SETTLEMENT',
    NULL,
    accounts.wallet_id
FROM (
    -- dreami(START)..dreami(START+COUNT-1) 각 계정의 WALLET.wallet_id를 이메일로 조회.
    WITH RECURSIVE acct_seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n + 1 FROM acct_seq WHERE n < @dreami_account_count
    )
    SELECT w.`wallet_id` AS wallet_id
    FROM acct_seq
    JOIN `BOORMI` AS b ON b.`email` = CONCAT('dreami', @dreami_account_start + acct_seq.n - 1, '@test.test')
    JOIN `WALLET` AS w ON w.`boormi_id` = b.`boormi_id`
) AS accounts
CROSS JOIN (
    WITH RECURSIVE order_seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n + 1 FROM order_seq WHERE n < @orders_per_account
    )
    SELECT n FROM order_seq
) AS seq_numbers;

-- 확인용
-- SELECT COUNT(*) FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)';
-- SELECT dreami_id, COUNT(*) FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)' GROUP BY dreami_id;
-- SELECT wallet_id, COUNT(*) FROM `MONEY_TX` WHERE type = 'SETTLEMENT' AND status = 'SETTLED'
--     AND wallet_id IN (SELECT w.wallet_id FROM `WALLET` w JOIN `BOORMI` b ON b.boormi_id = w.boormi_id
--         WHERE b.email LIKE 'dreami4%@test.test') GROUP BY wallet_id;

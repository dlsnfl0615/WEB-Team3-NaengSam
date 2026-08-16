-- ============================================================
-- 여러 드리미 계정에 각각 활동 내역 대량 생성 (부하테스트용,
-- loadtest/k6/dreami-deliveries-list-multi-account.js 대상)
--
-- loadtest-seed-dreami1-deliveries.sql이 "계정 1개, 동시 요청 여러 개"로 쿼리 자체의
-- 비용을 보는 테스트였다면, 이 스크립트는 "데이터 많은 계정이 여러 개 동시에 조회될 때
-- 시스템 전체가 버티는지"를 보는 테스트용이다 — VU마다 서로 다른 계정으로 로그인해서
-- 각자 자기 활동 내역을 조회하게 만든다(세션 하나 공유가 아님).
--
-- 계정 범위는 dreami1~10(login.js), dreami1(dreami-dashboard.js/matching-nearby-calls.js),
-- boormi1~100(mass-matching-100x100.js/subscribe-order.js) 등 다른 loadtest 스크립트가 쓰는
-- 범위와 겹치면 안 된다 — 겹치면 그 계정으로 다른 스크립트가 로그인할 때마다 "한 디바이스만"
-- 세션 정책 때문에 이 스크립트가 캐싱해둔 세션이 조용히 무효화되어 401이 난다(실제로 겪은 문제).
-- 그래서 기본값을 dreami400~419(어느 스크립트도 안 쓰는 대역)로 둔다.
--
-- 전제조건: backend/sql/test-seed-accounts.sql을 먼저 실행해서 dreami(START)..dreami(START+COUNT-1)@test.test /
-- boormi2@test.test 계정이 이미 있어야 한다(FK: ORDERS.dreami_id → DREAMI, ORDERS.boormi_id → BOORMI).
-- 지금 test-seed-accounts.sql은 dreami1~500까지 만들어두므로 기본값(400~419) 그대로 사용 가능.
--
-- 이 스크립트는 기존 데이터를 지우지 않고 추가만 한다. test-seed-accounts.sql을 다시
-- 실행하면(전체 재시딩) 여기서 넣은 행도 같이 삭제되니, 그 이후엔 다시 실행해야 한다.
--
-- 생성된 행은 item_detail = '부하테스트용 더미 주문(다계정)'으로 표시해뒀다. 지우려면:
--   DELETE FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)';
--
-- 계정 수(@dreami_account_count)와 계정당 건수(@orders_per_account)만 바꾸면 규모 조절 가능.
-- 기본값(계정 20개 × 5000건)이면 총 100,000행이 새로 생기니 다소 시간이 걸릴 수 있다.
-- ============================================================

SET SESSION cte_max_recursion_depth = 200000;
SET @orders_per_account = 5000;
SET @dreami_account_start = 400; -- 다른 loadtest 스크립트와 안 겹치는 대역. k6의 ACCOUNT_START와 맞춰야 함.
SET @dreami_account_count = 20; -- dreami400 ~ dreami419

SET @boormi2_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = 'boormi2@test.test');
SET @first_dreami_email = CONCAT('dreami', @dreami_account_start, '@test.test');
SET @first_dreami_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = @first_dreami_email);
SET @last_dreami_email = CONCAT('dreami', @dreami_account_start + @dreami_account_count - 1, '@test.test');
SET @last_dreami_id = (SELECT `boormi_id` FROM `BOORMI` WHERE `email` = @last_dreami_email);

-- 범위 양 끝(dreami(START), dreami(START+COUNT-1))만 확인한다 — 계정이 이 DB에 없으면(테스트
-- 계정 시드가 아직 안 됐거나 범위가 500을 넘으면) 여기서 바로 알아챌 수 있게.
SELECT
    CASE WHEN @boormi2_id IS NULL THEN 'ERROR: boormi2@test.test 계정을 찾을 수 없습니다 — test-seed-accounts.sql을 먼저 실행하세요.'
         WHEN @first_dreami_id IS NULL THEN CONCAT('ERROR: ', @first_dreami_email, ' 계정을 찾을 수 없습니다 — test-seed-accounts.sql을 먼저 실행하세요.')
         WHEN @last_dreami_id IS NULL THEN CONCAT('ERROR: ', @last_dreami_email, ' 계정을 찾을 수 없습니다 — @dreami_account_count를 줄이거나 계정을 더 만드세요.')
         ELSE 'OK'
    END AS precheck;

-- WITH RECURSIVE를 INSERT 앞에 바로 두면 일부 MySQL 서버에서 문법 오류가 나므로(INSERT 앞은
-- WITH가 허용되는 위치가 아님), 파생 테이블(서브쿼리)의 SELECT 안에 넣어 "WITH ... SELECT" 형태로 감싼다.
INSERT INTO `ORDERS`
(`order_id`, `boormi_id`, `item_name`, `item_cd`, `item_detail`, `delivery_amount`, `order_cd`, `delivery_eta`, `delivery_distance`,
 `origin_latitude`, `origin_longitude`, `origin_alias`, `origin_address_line_1`,
 `destination_latitude`, `destination_longitude`, `destination_alias`, `destination_address_line_1`,
 `delivery_request_dtm`, `dreami_id`)
SELECT
    UUID_TO_BIN(UUID()),
    @boormi2_id, -- boormi2@test.test (요청자, 모든 계정 공통 고정)
    ELT(1 + (seq_numbers.n % 4), '서류봉투', '샘플 상품', '소포', '기타 물품'),
    ELT(1 + (seq_numbers.n % 4), 'DOCUMENT', 'SAMPLE', 'PACKAGE', 'ETC'),
    '부하테스트용 더미 주문(다계정)',
    5000 + (seq_numbers.n % 5) * 1000,
    'COMPLETED',
    18,
    1500,
    37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
    37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
    DATE_SUB('2026-08-10 10:00:00', INTERVAL seq_numbers.n MINUTE),
    accounts.dreami_id
FROM (
    -- dreami(START)..dreami(START+COUNT-1) 각 계정의 실제 boormi_id(=dreami_id)를 이메일로 조회.
    WITH RECURSIVE acct_seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM acct_seq WHERE n < @dreami_account_count
    )
    SELECT b.`boormi_id` AS dreami_id
    FROM acct_seq
    JOIN `BOORMI` AS b ON b.`email` = CONCAT('dreami', @dreami_account_start + acct_seq.n - 1, '@test.test')
) AS accounts
CROSS JOIN (
    WITH RECURSIVE order_seq AS (
        SELECT 1 AS n
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
    UUID_TO_BIN(UUID()),
    'SETTLED',
    5000 + (seq_numbers.n % 5) * 1000,
    DATE_SUB('2026-08-10 10:00:00', INTERVAL seq_numbers.n MINUTE),
    'SETTLEMENT',
    NULL,
    accounts.wallet_id
FROM (
    -- dreami(START)..dreami(START+COUNT-1) 각 계정의 WALLET.wallet_id를 이메일로 조회.
    WITH RECURSIVE acct_seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM acct_seq WHERE n < @dreami_account_count
    )
    SELECT w.`wallet_id` AS wallet_id
    FROM acct_seq
    JOIN `BOORMI` AS b ON b.`email` = CONCAT('dreami', @dreami_account_start + acct_seq.n - 1, '@test.test')
    JOIN `WALLET` AS w ON w.`boormi_id` = b.`boormi_id`
) AS accounts
CROSS JOIN (
    WITH RECURSIVE order_seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM order_seq WHERE n < @orders_per_account
    )
    SELECT n FROM order_seq
) AS seq_numbers;

-- 확인용
-- SELECT COUNT(*) FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)';
-- 계정별 건수:
-- SELECT dreami_id, COUNT(*) FROM `ORDERS` WHERE item_detail = '부하테스트용 더미 주문(다계정)' GROUP BY dreami_id;
-- MONEY_TX 건수(위 ORDERS 계정별 건수와 같은 값이 나와야 함):
-- SELECT wallet_id, COUNT(*) FROM `MONEY_TX` WHERE type = 'SETTLEMENT' AND status = 'SETTLED'
--     AND wallet_id IN (SELECT w.wallet_id FROM `WALLET` w JOIN `BOORMI` b ON b.boormi_id = w.boormi_id
--         WHERE b.email LIKE 'dreami4%@test.test') GROUP BY wallet_id;

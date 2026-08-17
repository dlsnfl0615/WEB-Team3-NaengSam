-- findStaleLocationDeliveries 복합인덱스 전/후 비교용 MySQL 8 fixture.
-- delivery-stale-location-index-test 전용 DB에만 실행한다.
-- 전체 테이블을 지우지 않고 아래 고정 부르미의 데이터(ORDERS/DELIVERY)만 교체한다.
--
-- 데이터 구성 (n = 1..20000, order_id / delivery_id 는 같은 n 으로 1:1 대응)
--   n 1    ~ 500   : 진행중 + last_location_dtm 이 threshold 보다 과거   -> 쿼리 결과 500건
--   n 501  ~ 950   : 진행중 + last_location_dtm 이 30초 이내             -> 아직 살아있음
--   n 951  ~ 1000  : 진행중 + last_location_dtm IS NULL                  -> 첫 위치 미수신
--   n 1001 ~ 20000 : 종료/취소 + last_location_dtm 이 아주 과거          -> 상태로만 걸러지는 배경 데이터
--
-- 마지막 19,000건의 last_location_dtm 을 전부 threshold 보다 과거로 두는 것이 이 실험의 핵심이다.
-- last_location_dtm 단독(또는 선두) 인덱스라면 이 19,000건이 범위 안에 그대로 들어오고,
-- delivery_cd 를 선두에 둔 복합인덱스만 이들을 먼저 잘라낼 수 있다.

SET @test_boormi_id     = X'47000000000000000000000000000001';
SET @test_dreami_id     = X'47000000000000000000000000000002';
SET @order_id_prefix    = '47000000000000000001';
SET @delivery_id_prefix = '47000000000000000002';
SET @delivery_count     = 20000;
SET @active_count       = 1000;
SET @stale_count        = 500;
SET @fresh_count        = 950;

-- 모든 시각의 기준점. CURRENT_TIMESTAMP 를 쓰면 벤치 도중 실시간이 흐르면서
-- '살아있는 450건'이 점점 stale 로 넘어가 결과 행 수가 달라진다.
-- 고정 기준점 + 고정 threshold 리터럴이어야 전/후 측정이 항상 정확히 500행으로 재현된다.
-- bench.sql 의 threshold 리터럴('2026-08-16 11:59:30')과 반드시 짝을 맞춰 수정한다.
SET @base_dtm = '2026-08-16 12:00:00';

SET SESSION cte_max_recursion_depth = 20000;

START TRANSACTION;

DELETE FROM `DELIVERY`
WHERE `boormi_id` = @test_boormi_id;

DELETE FROM `ORDERS`
WHERE `boormi_id` = @test_boormi_id;

DELETE FROM `BOORMI`
WHERE `boormi_id` = @test_boormi_id;

INSERT INTO `BOORMI` (
    `boormi_id`,
    `email`,
    `password`,
    `name`,
    `phone_number`,
    `birthdate`,
    `user_cd`,
    `is_dreami_activated`,
    `created_dtm`,
    `boormi_avg_score`
) VALUES (
    @test_boormi_id,
    'delivery-stale-location@index.test',
    '00000000000000000000000000000000:0000000000000000000000000000000000000000000000000000000000000000',
    '배달지연 성능테스트',
    '01047000000',
    DATE '1995-01-01',
    'ACTIVE',
    FALSE,
    CURRENT_TIMESTAMP,
    0
);

-- DELIVERY.order_id -> ORDERS.order_id 가 유일한 외래키이므로 ORDERS 를 먼저 넣는다.
-- ORDERS 자체는 이번 측정 대상이 아니라 FK 를 만족시키기 위한 최소 데이터다.
INSERT INTO `ORDERS` (
    `order_id`,
    `boormi_id`,
    `item_name`,
    `item_cd`,
    `order_cd`,
    `delivery_eta`,
    `delivery_request_dtm`,
    `dreami_id`
)
WITH RECURSIVE sequence_numbers (`sequence_no`) AS (
    SELECT 1
    UNION ALL
    SELECT `sequence_no` + 1
    FROM sequence_numbers
    WHERE `sequence_no` < @delivery_count
)
SELECT
    UNHEX(CONCAT(@order_id_prefix, LPAD(HEX(`sequence_no`), 12, '0'))),
    @test_boormi_id,
    CONCAT('위치끊김 성능테스트 물품 ', `sequence_no`),
    ELT(MOD(`sequence_no` - 1, 4) + 1, 'DOCUMENT', 'SAMPLE', 'PACKAGE', 'ETC'),
    CASE WHEN `sequence_no` <= @active_count THEN 'IN_PROGRESS' ELSE 'COMPLETED' END,
    10 + MOD(`sequence_no`, 50),
    TIMESTAMPADD(SECOND, -(3600 + `sequence_no`), @base_dtm),
    NULL
FROM sequence_numbers;

INSERT INTO `DELIVERY` (
    `delivery_id`,
    `delivery_cd`,
    `order_id`,
    `dreami_id`,
    `boormi_id`,
    `current_latitude`,
    `current_longitude`,
    `picked_up_dtm`,
    `delivery_start_dtm`,
    `delivery_end_dtm`,
    `received_dtm`,
    `route_path`,
    `estimated_completion_dtm`,
    `last_location_dtm`,
    `offline_sms_sent_dtm`
)
WITH RECURSIVE sequence_numbers (`sequence_no`) AS (
    SELECT 1
    UNION ALL
    SELECT `sequence_no` + 1
    FROM sequence_numbers
    WHERE `sequence_no` < @delivery_count
)
SELECT
    UNHEX(CONCAT(@delivery_id_prefix, LPAD(HEX(`sequence_no`), 12, '0'))),
    -- PICKUP_DELAYED 는 사용 예정이 없으므로 시드에 넣지 않는다.
    CASE
        WHEN `sequence_no` <= @active_count
            THEN ELT(MOD(`sequence_no`, 2) + 1, 'PICKUP_NORMAL', 'DELIVERING')
        ELSE ELT(MOD(`sequence_no` - 1, 4) + 1,
                 'DELIVERED',
                 'TERMINATED',
                 'RETURNED',
                 'PICKUP_CANCELLED_BY_BOORMI')
    END,
    UNHEX(CONCAT(@order_id_prefix, LPAD(HEX(`sequence_no`), 12, '0'))),
    @test_dreami_id,
    @test_boormi_id,
    37.49790000 + MOD(`sequence_no`, 1000) * 0.000001,
    127.02760000 + MOD(`sequence_no`, 1000) * 0.000001,
    TIMESTAMPADD(SECOND, -(1800 + `sequence_no`), @base_dtm),
    TIMESTAMPADD(SECOND, -(1700 + `sequence_no`), @base_dtm),
    CASE WHEN `sequence_no` <= @active_count
         THEN NULL
         ELSE TIMESTAMPADD(SECOND, -(300 + `sequence_no`), @base_dtm) END,
    CASE WHEN `sequence_no` <= @active_count
         THEN NULL
         ELSE TIMESTAMPADD(SECOND, -(240 + `sequence_no`), @base_dtm) END,
    NULL,
    TIMESTAMPADD(SECOND, 900 - MOD(`sequence_no`, 900), @base_dtm),
    CASE
        -- 1 ~ 500 : 60 ~ 299초 전 -> threshold(30초) 보다 과거. 실제 결과 행.
        WHEN `sequence_no` <= @stale_count
            THEN TIMESTAMPADD(SECOND, -(60 + MOD(`sequence_no`, 240)), @base_dtm)
        -- 501 ~ 950 : 0 ~ 24초 전 -> 아직 살아있는 배달.
        WHEN `sequence_no` <= @fresh_count
            THEN TIMESTAMPADD(SECOND, -MOD(`sequence_no`, 25), @base_dtm)
        -- 951 ~ 1000 : 첫 위치가 아직 안 온 배달. IS NOT NULL 분기를 실제로 태운다.
        WHEN `sequence_no` <= @active_count
            THEN NULL
        -- 1001 ~ 20000 : 종료된 배달인데 위치 시각은 계속 과거로 남아 있다.
        ELSE TIMESTAMPADD(SECOND, -(600 + `sequence_no`), @base_dtm)
    END,
    NULL
FROM sequence_numbers;

COMMIT;

ANALYZE TABLE `ORDERS`;
ANALYZE TABLE `DELIVERY`;

-- 검증: 아래 세 값이 각각 20000 / 1000 / 500 이어야 한다.
SELECT COUNT(*) AS total_delivery_count
FROM `DELIVERY`
WHERE `boormi_id` = @test_boormi_id;

SELECT COUNT(*) AS active_delivery_count
FROM `DELIVERY`
WHERE `boormi_id` = @test_boormi_id
  AND `delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING');

SELECT COUNT(*) AS stale_match_count
FROM `DELIVERY`
WHERE `delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
  AND `last_location_dtm` IS NOT NULL
  AND `last_location_dtm` < TIMESTAMPADD(SECOND, -30, @base_dtm);

-- findNearbyCalls 수동 N+1 비교용 MySQL 8 fixture.
-- index-test 전용 DB에만 실행한다. 전체 테이블을 지우지 않고 아래 고정 부르미의 데이터만 교체한다.

SET @test_boormi_id = X'46000000000000000000000000000001';
SET @order_id_prefix = '46000000000000000001';
SET @order_count = 10000;

START TRANSACTION;

DELETE FROM `ORDERS`
WHERE `boormi_id` = @test_boormi_id;

DELETE FROM `BOORMI`
WHERE `boormi_id` = @test_boormi_id;

-- 로그인 계정: nearby-n-plus-one@index.test / index-test
-- PasswordHasher와 동일한 PBKDF2WithHmacSHA256(210,000회, 256bit) 형식이다.
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
    'nearby-n-plus-one@index.test',
    '00000000000000000000000000000000:01b8621d9f9ab32bdd2ef8461efcd5f9ff3a13c9e1bbcea1be08e8ca7b834784',
    '주변콜 성능테스트',
    '01046000000',
    DATE '1995-01-01',
    'ACTIVE',
    FALSE,
    CURRENT_TIMESTAMP,
    0
);

CREATE TEMPORARY TABLE `INDEX_TEST_DIGIT` (
    `n` tinyint NOT NULL PRIMARY KEY
);

INSERT INTO `INDEX_TEST_DIGIT` (`n`)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

-- 10,000건 중 1~10번 주문만 인메모리 매칭 엔진에도 등록한다.
-- 나머지 9,990건은 빈 테이블에 가까운 측정을 피하기 위한 배경 데이터다.
INSERT INTO `ORDERS` (
    `order_id`,
    `boormi_id`,
    `item_name`,
    `item_cd`,
    `item_detail`,
    `delivery_amount`,
    `order_cd`,
    `delivery_eta`,
    `delivery_distance`,
    `origin_latitude`,
    `origin_longitude`,
    `origin_alias`,
    `origin_address_line_1`,
    `origin_address_line_2`,
    `destination_latitude`,
    `destination_longitude`,
    `destination_alias`,
    `destination_address_line_1`,
    `destination_address_line_2`,
    `delivery_request`,
    `delivery_request_dtm`,
    `dreami_id`
)
SELECT
    UNHEX(CONCAT(@order_id_prefix, LPAD(HEX(sequence_no), 12, '0'))),
    @test_boormi_id,
    CONCAT('성능테스트 물품 ', sequence_no),
    ELT(MOD(sequence_no - 1, 4) + 1, 'DOCUMENT', 'SAMPLE', 'PACKAGE', 'ETC'),
    'findNearbyCalls 성능 비교용 mock 주문',
    3000 + MOD(sequence_no, 10000),
    CASE
        WHEN sequence_no <= 10 THEN 'MATCHING'
        ELSE ELT(MOD(sequence_no - 1, 7) + 1,
                 'MATCHING',
                 'PENDING_BOORMI_CONFIRMATION',
                 'IN_PROGRESS',
                 'WAITING_CONFIRMATION',
                 'COMPLETED',
                 'CANCELLED',
                 'CLAIM_REVIEW')
    END,
    10 + MOD(sequence_no, 50),
    500 + MOD(sequence_no, 5000),
    37.49790000 + MOD(sequence_no, 100) * 0.000001,
    127.02760000 + MOD(sequence_no, 100) * 0.000001,
    '테스트 출발지',
    '서울 강남구 테헤란로 152',
    CONCAT(MOD(sequence_no, 20) + 1, '층'),
    37.49890000 + MOD(sequence_no, 100) * 0.000001,
    127.02860000 + MOD(sequence_no, 100) * 0.000001,
    '테스트 도착지',
    '서울 강남구 테헤란로 212',
    '1층 로비',
    '성능테스트 주문',
    TIMESTAMPADD(SECOND, -sequence_no, CURRENT_TIMESTAMP),
    NULL
FROM (
    SELECT
        d0.n
        + d1.n * 10
        + d2.n * 100
        + d3.n * 1000
        + 1 AS sequence_no
    FROM `INDEX_TEST_DIGIT` d0
    CROSS JOIN `INDEX_TEST_DIGIT` d1
    CROSS JOIN `INDEX_TEST_DIGIT` d2
    CROSS JOIN `INDEX_TEST_DIGIT` d3
) sequence_numbers
WHERE sequence_no <= @order_count;

COMMIT;

ANALYZE TABLE `ORDERS`;

SELECT COUNT(*) AS fixture_order_count
FROM `ORDERS`
WHERE `boormi_id` = @test_boormi_id;

SELECT
    LOWER(CONCAT(
        SUBSTRING(HEX(`order_id`), 1, 8), '-',
        SUBSTRING(HEX(`order_id`), 9, 4), '-',
        SUBSTRING(HEX(`order_id`), 13, 4), '-',
        SUBSTRING(HEX(`order_id`), 17, 4), '-',
        SUBSTRING(HEX(`order_id`), 21, 12)
    )) AS nearby_order_id
FROM `ORDERS`
WHERE `boormi_id` = @test_boormi_id
ORDER BY `order_id`
LIMIT 10;

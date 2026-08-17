-- findStaleLocationDeliveries 계측 스크립트.
-- 인덱스 추가 전/후에 "똑같이" 실행해서 출력을 비교한다.
--
-- 측정 대상 쿼리 (DreamiOfflineDetector 가 5초마다 호출하는 것과 같은 형태):
--
--   SELECT d.* FROM DELIVERY d
--   WHERE d.delivery_cd IN ('PICKUP_NORMAL','DELIVERING')
--     AND d.last_location_dtm IS NOT NULL
--     AND d.last_location_dtm < '2026-08-16 11:59:30';
--
-- threshold 는 seed.sql 의 @base_dtm('2026-08-16 12:00:00') - 30초 고정 리터럴이다.
-- seed.sql 의 @base_dtm 을 바꾸면 이 파일의 리터럴도 같이 바꿔야 한다.
--
-- 반복 측정 구간에서는 COUNT(*) 대신 전 컬럼을 CONCAT_WS 로 훑는다.
-- COUNT(*) 만 세면 복합인덱스가 커버링 인덱스로 동작해 클러스터드 인덱스 조회가 사라지고,
-- Hibernate 가 엔티티 전체를 로딩하는 실제 비용보다 '개선 후'가 부당하게 빨라진다.

DROP PROCEDURE IF EXISTS bench_select;
DROP PROCEDURE IF EXISTS bench_update;

DELIMITER $$

CREATE PROCEDURE bench_select(IN reps INT)
BEGIN
    DECLARE i INT DEFAULT 0;

    WHILE i < reps DO
        SELECT COUNT(*),
               SUM(LENGTH(CONCAT_WS('|',
                   d.`delivery_id`, d.`delivery_cd`, d.`order_id`, d.`dreami_id`, d.`boormi_id`,
                   d.`current_latitude`, d.`current_longitude`, d.`picked_up_dtm`,
                   d.`delivery_start_dtm`, d.`delivery_end_dtm`, d.`received_dtm`, d.`route_path`,
                   d.`estimated_completion_dtm`, d.`last_location_dtm`, d.`offline_sms_sent_dtm`)))
          INTO @sink_rows, @sink_len
          FROM `DELIVERY` d
         WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
           AND d.`last_location_dtm` IS NOT NULL
           AND d.`last_location_dtm` < '2026-08-16 11:59:30';

        SET i = i + 1;
    END WHILE;
END$$

-- 위치 수신 1건 = PK 단건 UPDATE. 진행중 1,000건을 순환하며 갱신한다.
-- 같은 값으로 UPDATE 하면 MySQL 이 no-op 으로 건너뛰므로 매번 다른 값을 쓴다.
-- 루프 전체를 하나의 트랜잭션으로 묶는 이유: 커밋 fsync 는 인덱스 유무와 무관한 고정비라
-- 포함시키면 인덱스 유지비 차이가 그 안에 묻힌다. 여기서는 인덱스 유지비만 분리해서 본다.
CREATE PROCEDURE bench_update(IN reps INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target BINARY(16);

    START TRANSACTION;

    WHILE i < reps DO
        SET target = UNHEX(CONCAT('47000000000000000002', LPAD(HEX(MOD(i, 1000) + 1), 12, '0')));

        UPDATE `DELIVERY`
           SET `last_location_dtm`  = TIMESTAMPADD(SECOND, -MOD(i, 3600), '2026-08-16 12:00:00'),
               `current_latitude`   = 37.49790000 + MOD(i, 10000) * 0.000001,
               `current_longitude`  = 127.02760000 + MOD(i, 10000) * 0.000001
         WHERE `delivery_id` = target;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END$$

DELIMITER ;


-- ---------------------------------------------------------------------------
-- 워밍업: 20,000행을 버퍼 풀에 올린다. 이후 수치는 디스크 I/O 가 아니라
-- CPU 바운드 스캔 비용이며, 5초 주기로 계속 도는 스케줄러의 정상 상태와 같다.
-- ---------------------------------------------------------------------------
SELECT '=== warmup (10 reps) ===' AS section;
CALL bench_select(10);


-- ---------------------------------------------------------------------------
-- (A) 스캔량 — 결정적 지표. 실행 시간과 달리 머신 상태에 흔들리지 않는다.
--     인덱스 전 : Handler_read_rnd_next ~= 20001  (풀스캔)
--     인덱스 후 : Handler_read_key = 2, Handler_read_next ~= 500
-- ---------------------------------------------------------------------------
SELECT '=== (A) scan volume ===' AS section;

FLUSH STATUS;

CALL bench_select(1);

SHOW SESSION STATUS WHERE `Variable_name` IN (
    'Handler_read_first',
    'Handler_read_key',
    'Handler_read_next',
    'Handler_read_rnd_next'
);

-- 결과 행 수. 인덱스 전/후 양쪽 모두 정확히 500 이어야 비교가 성립한다.
SELECT @sink_rows AS matched_rows;


-- ---------------------------------------------------------------------------
-- (B) 실행계획
--     인덱스 전 : type=ALL,   key=NULL,                           rows~=20000
--     인덱스 후 : type=range, key=IX_DELIVERY_STATUS_LAST_LOCATION, rows~=500
-- ---------------------------------------------------------------------------
SELECT '=== (B) EXPLAIN ===' AS section;

EXPLAIN
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';

SELECT '=== (B) EXPLAIN ANALYZE x5 (actual time 중앙값을 기록) ===' AS section;

EXPLAIN ANALYZE
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';

EXPLAIN ANALYZE
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';

EXPLAIN ANALYZE
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';

EXPLAIN ANALYZE
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';

EXPLAIN ANALYZE
SELECT d.* FROM `DELIVERY` d
 WHERE d.`delivery_cd` IN ('PICKUP_NORMAL', 'DELIVERING')
   AND d.`last_location_dtm` IS NOT NULL
   AND d.`last_location_dtm` < '2026-08-16 11:59:30';


-- ---------------------------------------------------------------------------
-- (C) 반복 실행 벽시계 — SELECT 200회 평균 (마이크로초)
-- ---------------------------------------------------------------------------
SELECT '=== (C) SELECT x200 ===' AS section;

SET @started_at = NOW(6);
CALL bench_select(200);
SELECT TIMESTAMPDIFF(MICROSECOND, @started_at, NOW(6)) / 200 AS select_avg_us;


-- ---------------------------------------------------------------------------
-- (D) 쓰기 비용 — 위치 수신 UPDATE 5,000회 평균 (마이크로초)
--     실제 부하는 진행중 1,000건 x 5초당 1핑 = 약 200 UPDATE/s 다.
--     여기서 나온 처리량이 200 TPS 를 충분히 넘으면 인덱스 유지비는 감당 가능하다.
--     이 구간이 last_location_dtm 을 바꿔놓으므로 반드시 (A)~(C) 이후에 실행하고,
--     다음 회차 전에는 seed.sql 을 다시 적재한다.
-- ---------------------------------------------------------------------------
SELECT '=== (D) UPDATE x5000 ===' AS section;

FLUSH STATUS;

SET @started_at = NOW(6);
CALL bench_update(5000);
SELECT TIMESTAMPDIFF(MICROSECOND, @started_at, NOW(6)) / 5000 AS update_avg_us,
       5000 / (TIMESTAMPDIFF(MICROSECOND, @started_at, NOW(6)) / 1000000) AS update_tps;

SHOW SESSION STATUS WHERE `Variable_name` IN (
    'Handler_update',
    'Innodb_rows_updated'
);

DROP PROCEDURE IF EXISTS bench_select;
DROP PROCEDURE IF EXISTS bench_update;

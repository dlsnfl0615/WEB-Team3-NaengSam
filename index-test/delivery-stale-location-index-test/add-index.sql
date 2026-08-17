-- 개선안: delivery_cd 를 선두에 둔 복합인덱스.
--
-- 종료/취소된 과거 배달은 last_location_dtm 이 계속 과거로 남아 있어
-- last_location_dtm < threshold 조건을 그대로 통과한다.
-- last_location_dtm 을 선두에 두면 이 19,000건이 범위 안에 그대로 들어오지만,
-- delivery_cd 를 선두에 두면 PICKUP_NORMAL / DELIVERING 두 개의 range 로
-- 진행중 배달만 먼저 잘라낼 수 있다.

CREATE INDEX `IX_DELIVERY_STATUS_LAST_LOCATION`
    ON `DELIVERY` (`delivery_cd`, `last_location_dtm`);

ANALYZE TABLE `DELIVERY`;

SHOW INDEX FROM `DELIVERY`;

CREATE TABLE `BOORMI` (
                          `boormi_id`            binary(16)    NOT NULL,
                          `email`                varchar(255)  NOT NULL  COMMENT 'unique',
                          `password`             varchar(255)  NOT NULL,
                          `name`                 varchar(50)   NOT NULL,
                          `phone_number`         varchar(50)   NOT NULL  COMMENT 'unique',
                          `birthdate`            date          NOT NULL,
                          `user_cd`              enum('ACTIVE', 'DELETED', 'RESTRICTED', 'BANNED')  NULL,
                          `is_dreami_activated`  boolean       NULL      DEFAULT false,
                          `created_dtm`          timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                          `updated_dtm`          timestamp     NULL,
                          `deleted_dtm`          timestamp     NULL,
                          `boormi_avg_score`     decimal(3,2)  NOT NULL  DEFAULT 0,
                          `restricted_dtm`       timestamp     NULL
);

CREATE TABLE `PARTNER_HANDOFF` (
                                   `partner_handoff_id`  binary(16)   NOT NULL,
                                   `partner_id`          binary(16)   NOT NULL,
                                   `accident_id`         binary(16)   NULL,
                                   `handoff_dtm`         timestamp    NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                   `is_picked_up`        boolean      NOT NULL,
                                   `note`                text         NULL,
                                   `handoff_reason`      varchar(50)  NULL,
                                   `picked_up_dtm`       timestamp    NULL,
                                   `order_id`            binary(16)   NOT NULL
);

CREATE TABLE `RETURN_DELIVERY` (
                                   `return_id`         binary(16)  NOT NULL,
                                   `return_reason`     text        NULL,
                                   `return_start_dtm`  timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                   `return_end_dtm`    timestamp   NULL,
                                   `return_cd`         enum('REPORTED', 'PROCESSING', 'RESOLVED', 'REJECTED')  NULL,
                                   `delivery_id`       binary(16)  NOT NULL
);

CREATE TABLE `MONEY_WALLET` (
                                `wallet_id`       binary(16)  NOT NULL,
                                `pending_amount`  bigint      NOT NULL  DEFAULT 0,
                                `amount`          bigint      NOT NULL  DEFAULT 0,
                                `updated_dtm`     timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `ORDERS` (
                          `order_id`                    binary(16)      NOT NULL,
                          `boormi_id`                   binary(16)      NOT NULL,
                          `item_name`                   varchar(50)     NOT NULL,
                          `item_cd`                     enum('DOCUMENT', 'SAMPLE', 'PACKAGE', 'ETC')  NOT NULL,
                          `item_detail`                 varchar(255)    NULL,
                          `delivery_amount`             bigint          NULL,
                          `order_cd`                    enum('MATCHING', 'PENDING_BOORMI_CONFIRMATION', 'IN_PROGRESS', 'WAITING_CONFIRMATION', 'COMPLETED', 'CANCELLED', 'CLAIM_REVIEW')  NOT NULL,
                          `delivery_eta`                int             NOT NULL  COMMENT '수정 불가능 (변동 사항은 변동 예상시간)',
                          `origin_latitude`             decimal(11, 8)  NULL,
                          `origin_longitude`            decimal(11, 8)  NULL,
                          `origin_alias`                varchar(50)     NULL,
                          `origin_address_line_1`       varchar(255)    NULL,
                          `origin_address_line_2`       varchar(255)    NULL,
                          `destination_latitude`        decimal(11, 8)  NULL,
                          `destination_longitude`       decimal(11, 8)  NULL,
                          `destination_alias`           varchar(50)     NULL,
                          `destination_address_line_1`  varchar(255)    NULL,
                          `destination_address_line_2`  varchar(255)    NULL,
                          `delivery_request`            varchar(255)    NULL,
                          `image_key`                   varchar(500)    NULL,
                          `delivery_request_dtm`        timestamp       NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                          `dreami_id`                   binary(16)      NULL
);

CREATE TABLE `POINT_LEDGERS` (
                                 `point_ledgers_id`  binary(16)  NOT NULL,
                                 `amount`            bigint      NOT NULL  DEFAULT 0,
                                 `balance_after`     bigint      NOT NULL,
                                 `created_dtm`       timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                 `wallet_id`         binary(16)  NOT NULL,
                                 `point_tx_id`       binary(16)  NOT NULL
);

CREATE TABLE `ACCIDENT_PROOF` (
                                  `proof_id`     binary(16)    NOT NULL,
                                  `proof_type`   varchar(200)  NULL,
                                  `file_key`     varchar(500)  NULL,
                                  `created_dtm`  timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                  `accident_id`  binary(16)    NOT NULL
);

CREATE TABLE `DREAMI` (
                          `dreami_id`            binary(16)    NOT NULL,
                          `reject_detail`        varchar(255)  NULL,
                          `request_dtm`          timestamp     NULL,
                          `review_dtm`           timestamp     NULL,
                          `request_cd`           enum('REQUESTED', 'REVIEWING', 'APPROVED', 'REJECTED')  NOT NULL,
                          `id_card_key`          varchar(500)  NOT NULL,
                          `dreami_avg_score`     decimal(3,2)  NOT NULL  DEFAULT 0,
                          `criminal_record_key`  varchar(500)  NOT NULL
);

CREATE TABLE `ORDER_STATUS_HISTORY` (
                                        `order_status_id`  binary(16)  NOT NULL,
                                        `previous_cd`      enum('MATCHING', 'PENDING_BOORMI_CONFIRMATION', 'IN_PROGRESS', 'WAITING_CONFIRMATION', 'COMPLETED', 'CANCELLED', 'CLAIM_REVIEW')  NULL,
                                        `new_cd`           enum('MATCHING', 'PENDING_BOORMI_CONFIRMATION', 'IN_PROGRESS', 'WAITING_CONFIRMATION', 'COMPLETED', 'CANCELLED', 'CLAIM_REVIEW')  NOT NULL,
                                        `changed_dtm`      timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                        `order_id`         binary(16)  NOT NULL
);

CREATE TABLE `PARTNER` (
                           `partner_id`    binary(16)    NOT NULL,
                           `phone_number`  varchar(50)   NULL,
                           `address`       varchar(255)  NULL,
                           `name`          varchar(255)  NULL
);

CREATE TABLE `PAYMENT_METHOD` (
                                  `payment_method_id`   binary(16)    NOT NULL,
                                  `billing_key`         varchar(255)  NOT NULL  COMMENT 'unique',
                                  `pg_name`             varchar(255)  NOT NULL,
                                  `payment_method_cd`   enum('CARD', 'BANK_TRANSFER', 'TOSS_PAY')  NOT NULL,
                                  `masking_no`          varchar(20)   NOT NULL,
                                  `is_default_payment`  boolean       NOT NULL  DEFAULT false,
                                  `created_dtm`         timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                  `updated_dtm`         timestamp     NULL,
                                  `deleted_dtm`         timestamp     NULL,
                                  `boormi_id`           binary(16)    NOT NULL
);

CREATE TABLE `PAYMENT` (
                           `payment_id`      binary(16)  NOT NULL,
                           `payment_amount`  bigint      NOT NULL,
                           `payment_cd`      enum('CARD', 'BANK_TRANSFER', 'TOSS_PAY')  NOT NULL,
                           `payment_dtm`     timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                           `refund_dtm`      timestamp   NULL,
                           `boormi_id`       binary(16)  NOT NULL
);

CREATE TABLE `MONEY_TX` (
                            `money_tx_id`  binary(16)                              NOT NULL,
                            `status`       enum('PENDING', 'SETTLED', 'REJECTED')  NOT NULL  DEFAULT 'PENDING',
                            `amount`       bigint                                  NOT NULL  DEFAULT 0,
                            `created_dtm`  timestamp                               NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                            `updated_dtm`  timestamp                               NULL,
                            `type`         enum('SETTLEMENT', 'REVERSAL', 'CLAIM_ADJUSTMENT', 'EXCHANGE_OUT')  NOT NULL,
                            `order_id`     binary(16)                              NOT NULL,
                            `wallet_id`    binary(16)                              NOT NULL
);

CREATE TABLE `MONEY_LEDGERS` (
                                 `money_ledgers_id`  binary(16)  NOT NULL,
                                 `amount`            bigint      NOT NULL  DEFAULT 0,
                                 `balance_after`     bigint      NOT NULL  DEFAULT 0,
                                 `created_dtm`       timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                 `wallet_id`         binary(16)  NOT NULL,
                                 `money_tx_id`       binary(16)  NOT NULL
);

CREATE TABLE `POINT_TX` (
                            `point_tx_id`  binary(16)  NOT NULL,
                            `type`         enum('CHARGE', 'PAYMENT', 'REFUND', 'EXCHANGE_IN')  NOT NULL,
                            `status`       enum('PAID', 'REFUNDED_PARTIAL', 'REFUNDED_FULL')  NOT NULL,
                            `amount`       bigint      NOT NULL,
                            `created_dtm`  timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                            `updated_dtm`  timestamp   NULL,
                            `payment_id`   binary(16)  NULL,
                            `order_id`     binary(16)  NULL,
                            `wallet_id`    binary(16)  NOT NULL
);

CREATE TABLE `EXCHANGES` (
                             `exchanges_id`  binary(16)  NOT NULL,
                             `amount`        bigint      NOT NULL,
                             `created_dtm`   timestamp   NOT NULL,
                             `point_tx_id`   binary(16)  NOT NULL,
                             `money_tx_id`   binary(16)  NOT NULL,
                             `wallet_id`     binary(16)  NOT NULL
);

CREATE TABLE `BOORMI_REVIEW` (
                                 `review_id`    binary(16)    NOT NULL,
                                 `score`        tinyint       NOT NULL,
                                 `detail`       varchar(200)  NULL,
                                 `created_dtm`  timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                 `updated_dtm`  timestamp     NULL,
                                 `deleted_dtm`  timestamp     NULL,
                                 `order_id`     binary(16)    NOT NULL
);

CREATE TABLE `CANCEL` (
                          `cancel_id`           binary(16)                         NOT NULL,
                          `canceled_dtm`        timestamp                          NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                          `cancel_reason`       varchar(50)                        NULL,
                          `note`                text                               NULL,
                          `is_penalty_applied`  boolean                            NOT NULL,
                          `canceler_cd`         enum('ADMIN', 'BOORMI', 'DREAMI')  NOT NULL,
                          `order_id`            binary(16)                         NOT NULL
);

CREATE TABLE `ADDRESS` (
                           `address_id`      binary(16)      NOT NULL,
                           `address_alias`   varchar(50)     NULL,
                           `latitude`        decimal(11, 8)  NOT NULL,
                           `longitude`       decimal(11, 8)  NOT NULL,
                           `address_line_2`  varchar(255)    NOT NULL,
                           `address_line_1`  varchar(255)    NOT NULL,
                           `boormi_id`       binary(16)      NOT NULL
);

CREATE TABLE `PICKUP_CERTIFICATION` (
                                        `certification_id`  binary(16)    NOT NULL,
                                        `is_contact`        boolean       NOT NULL  DEFAULT true               COMMENT '대면, 비대면',
                                        `image_key`         varchar(500)  NULL,
                                        `sign_key`          varchar(500)  NULL,
                                        `submitted_dtm`     timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                        `order_id`          binary(16)    NOT NULL
);

CREATE TABLE `BOORMI_REJECT_HISTORY` (
                                         `reject_id`      binary(16)    NOT NULL,
                                         `reject_dtm`     timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                         `reject_detail`  varchar(255)  NULL,
                                         `boormi_id`      binary(16)    NOT NULL
);

CREATE TABLE `MATCHING` (
                            `matching_id`   binary(16)  NOT NULL,
                            `accepted_dtm`  timestamp   NULL,
                            `canceled_dtm`  timestamp   NULL,
                            `created_dtm`   timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                            `order_id`      binary(16)  NOT NULL
);

CREATE TABLE `DELIVERY_CERTIFICATION` (
                                          `certification_id`  binary(16)    NOT NULL,
                                          `delivery_id`       binary(16)    NOT NULL,
                                          `is_contact`        boolean       NOT NULL  DEFAULT true,
                                          `image_key`         varchar(500)  NULL,
                                          `sign_key`          varchar(500)  NULL,
                                          `submitted_dtm`     timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `WALLET` (
                          `wallet_id`  binary(16)  NOT NULL,
                          `boormi_id`  binary(16)  NOT NULL
);

CREATE TABLE `POINT_WALLET` (
                                `wallet_id`    binary(16)  NOT NULL,
                                `amount`       bigint      NOT NULL  DEFAULT 0,
                                `updated_dtm`  timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `SETTLEMENT_DETAILS` (
                                      `settlement_id`       binary(16)   NOT NULL,
                                      `dreami_id`           binary(16)   NOT NULL,
                                      `settlement_account`  varchar(50)  NOT NULL,
                                      `registered_dtm`      timestamp    NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                      `settlement_bank`     varchar(50)  NOT NULL,
                                      `account_holder`      varchar(50)  NULL
);

CREATE TABLE `DELIVERY` (
                            `delivery_id`         binary(16)  NOT NULL,
                            `delivery_cd`         enum('PICKUP_NORMAL', 'PICKUP_DELAYED', 'PICKUP_CANCELLED_BY_BOORMI', 'PICKUP_CANCELLED_BY_DREAMI', 'PICKUP_CANCELLED_BY_ADMIN', 'DELIVERING', 'DELIVERED', 'PARTNER_HANDOFF_PENDING', 'TRANSFERRED_TO_PARTNER', 'RETURNING', 'RETURNED', 'TERMINATED' )  NOT NULL,
                            `order_id`            binary(16)  NOT NULL,
                            `dreami_id`           binary(16)  NOT NULL,
                            `boormi_id`           binary(16)  NOT NULL,
                            `current_latitude`    decimal(11,8)  NULL,
                            `current_longitude`   decimal(11,8)  NULL,
                            `picked_up_dtm`       timestamp   NULL,
                            `delivery_start_dtm`  timestamp   NULL,
                            `delivery_end_dtm`    timestamp   NULL,
                            `received_dtm`        timestamp   NULL
);

CREATE TABLE `DELIVERY_ACCIDENT` (
                                     `accident_id`       binary(16)   NOT NULL,
                                     `delivery_id`       binary(16)   NOT NULL,
                                     `accident_type`     varchar(50)  NULL,
                                     `accident_details`  text         NULL,
                                     `processing_cd`     enum('REPORTED', 'PROCESSING', 'RESOLVED', 'REJECTED')  NULL,
                                     `report_dtm`        timestamp    NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                     `resolution_dtm`    timestamp    NULL
);

CREATE TABLE `COMPENSATION_CLAIM` (
                                      `claim_id`             binary(16)  NOT NULL,
                                      `request_cd`           enum('REQUESTED', 'REVIEWING', 'APPROVED', 'REJECTED' )  NOT NULL  DEFAULT 'REQUESTED',
                                      `claim_amount`         bigint      NOT NULL  DEFAULT 0,
                                      `compensation_amount`  bigint      NOT NULL  DEFAULT 0,
                                      `decision_reason`      text        NULL,
                                      `audit_dtm`            timestamp   NULL,
                                      `created_dtm`          timestamp   NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                      `accident_id`          binary(16)  NOT NULL
);

CREATE TABLE `DREAMI_REQUEST_DENIED_DETAILS` (
                                                 `reject_id`      binary(16)    NOT NULL,
                                                 `rejected_dtm`   timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                                 `reject_detail`  varchar(200)  NULL,
                                                 `dreami_id`      binary(16)    NOT NULL
);

CREATE TABLE `DREAMI_REVIEW` (
                                 `review_id`    binary(16)    NOT NULL,
                                 `score`        tinyint       NOT NULL,
                                 `content`      varchar(200)  NULL,
                                 `created_dtm`  timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                 `updated_dtm`  timestamp     NULL,
                                 `deleted_dtm`  timestamp     NULL,
                                 `order_id`     binary(16)    NOT NULL
);

ALTER TABLE `BOORMI` ADD CONSTRAINT `PK_BOORMI` PRIMARY KEY (`boormi_id`);

ALTER TABLE `BOORMI` ADD CONSTRAINT `UQ_BOORMI_EMAIL` UNIQUE (`email`);

ALTER TABLE `BOORMI` ADD CONSTRAINT `UQ_BOORMI_PHONE_NUMBER` UNIQUE (`phone_number`);

ALTER TABLE `PARTNER_HANDOFF` ADD CONSTRAINT `PK_PARTNER_HANDOFF` PRIMARY KEY (`partner_handoff_id`);

ALTER TABLE `RETURN_DELIVERY` ADD CONSTRAINT `PK_RETURN_DELIVERY` PRIMARY KEY (`return_id`);

ALTER TABLE `MONEY_WALLET` ADD CONSTRAINT `PK_MONEY_WALLET` PRIMARY KEY (`wallet_id`);

ALTER TABLE `ORDERS` ADD CONSTRAINT `PK_ORDERS` PRIMARY KEY (`order_id`);

ALTER TABLE `POINT_LEDGERS` ADD CONSTRAINT `PK_POINT_LEDGERS` PRIMARY KEY (`point_ledgers_id`);

ALTER TABLE `ACCIDENT_PROOF` ADD CONSTRAINT `PK_ACCIDENT_PROOF` PRIMARY KEY (`proof_id`);

ALTER TABLE `DREAMI` ADD CONSTRAINT `PK_DREAMI` PRIMARY KEY (`dreami_id`);

ALTER TABLE `ORDER_STATUS_HISTORY` ADD CONSTRAINT `PK_ORDER_STATUS_HISTORY` PRIMARY KEY (`order_status_id`);

ALTER TABLE `PARTNER` ADD CONSTRAINT `PK_PARTNER` PRIMARY KEY (`partner_id`);

ALTER TABLE `PAYMENT_METHOD` ADD CONSTRAINT `PK_PAYMENT_METHOD` PRIMARY KEY (`payment_method_id`);

ALTER TABLE `PAYMENT_METHOD` ADD CONSTRAINT `UQ_PAYMENT_METHOD_BILLING_KEY` UNIQUE (`billing_key`);

ALTER TABLE `PAYMENT` ADD CONSTRAINT `PK_PAYMENT` PRIMARY KEY (`payment_id`);

ALTER TABLE `MONEY_TX` ADD CONSTRAINT `PK_MONEY_TX` PRIMARY KEY (`money_tx_id`);

ALTER TABLE `MONEY_LEDGERS` ADD CONSTRAINT `PK_MONEY_LEDGERS` PRIMARY KEY (`money_ledgers_id`);

ALTER TABLE `POINT_TX` ADD CONSTRAINT `PK_POINT_TX` PRIMARY KEY (`point_tx_id`);

-- 같은 주문에 결제가 두 번 쌓이는 것을 DB가 막는다(포인트 결제 멱등성).
-- order_id 가 NULL 인 거래(CHARGE, EXCHANGE_IN)는 MySQL UNIQUE 가 NULL 중복을 허용하므로 영향받지 않는다.
-- FK(`FK_ORDERS_TO_POINT_TX_1`)보다 먼저 생성해 선두 컬럼 order_id 인덱스를 재사용하게 한다(중복 인덱스 방지).
ALTER TABLE `POINT_TX` ADD CONSTRAINT `UQ_POINT_TX_ORDER_TYPE` UNIQUE (`order_id`, `type`);

ALTER TABLE `EXCHANGES` ADD CONSTRAINT `PK_EXCHANGES` PRIMARY KEY (`exchanges_id`);

ALTER TABLE `BOORMI_REVIEW` ADD CONSTRAINT `PK_BOORMI_REVIEW` PRIMARY KEY (`review_id`);

ALTER TABLE `CANCEL` ADD CONSTRAINT `PK_CANCEL` PRIMARY KEY (`cancel_id`);

ALTER TABLE `ADDRESS` ADD CONSTRAINT `PK_ADDRESS` PRIMARY KEY (`address_id`);

ALTER TABLE `PICKUP_CERTIFICATION` ADD CONSTRAINT `PK_PICKUP_CERTIFICATION` PRIMARY KEY (`certification_id`);

ALTER TABLE `BOORMI_REJECT_HISTORY` ADD CONSTRAINT `PK_BOORMI_REJECT_HISTORY` PRIMARY KEY (`reject_id`);

ALTER TABLE `MATCHING` ADD CONSTRAINT `PK_MATCHING` PRIMARY KEY (`matching_id`);

ALTER TABLE `DELIVERY_CERTIFICATION` ADD CONSTRAINT `PK_DELIVERY_CERTIFICATION` PRIMARY KEY (`certification_id`);

ALTER TABLE `WALLET` ADD CONSTRAINT `PK_WALLET` PRIMARY KEY (`wallet_id`);

-- 한 회원이 지갑을 두 개 갖지 못하게 막고, boormi_id 로 지갑을 찾는 결제 진입 경로의 조회 인덱스를 겸한다.
ALTER TABLE `WALLET` ADD CONSTRAINT `UQ_WALLET_BOORMI` UNIQUE (`boormi_id`);

ALTER TABLE `POINT_WALLET` ADD CONSTRAINT `PK_POINT_WALLET` PRIMARY KEY (`wallet_id`);

ALTER TABLE `SETTLEMENT_DETAILS` ADD CONSTRAINT `PK_SETTLEMENT_DETAILS` PRIMARY KEY (`settlement_id`, `dreami_id`);

ALTER TABLE `DELIVERY` ADD CONSTRAINT `PK_DELIVERY` PRIMARY KEY (`delivery_id`);

ALTER TABLE `DELIVERY_ACCIDENT` ADD CONSTRAINT `PK_DELIVERY_ACCIDENT` PRIMARY KEY (`accident_id`);

ALTER TABLE `COMPENSATION_CLAIM` ADD CONSTRAINT `PK_COMPENSATION_CLAIM` PRIMARY KEY (`claim_id`);

ALTER TABLE `DREAMI_REQUEST_DENIED_DETAILS` ADD CONSTRAINT `PK_DREAMI_REQUEST_DENIED_DETAILS` PRIMARY KEY (`reject_id`);

ALTER TABLE `DREAMI_REVIEW` ADD CONSTRAINT `PK_DREAMI_REVIEW` PRIMARY KEY (`review_id`);

ALTER TABLE `PARTNER_HANDOFF` ADD CONSTRAINT `FK_PARTNER_TO_PARTNER_HANDOFF_1` FOREIGN KEY (`partner_id`) REFERENCES `PARTNER` (`partner_id`);

ALTER TABLE `PARTNER_HANDOFF` ADD CONSTRAINT `FK_DELIVERY_ACCIDENT_TO_PARTNER_HANDOFF_1` FOREIGN KEY (`accident_id`) REFERENCES `DELIVERY_ACCIDENT` (`accident_id`);

ALTER TABLE `PARTNER_HANDOFF` ADD CONSTRAINT `FK_ORDERS_TO_PARTNER_HANDOFF_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `RETURN_DELIVERY` ADD CONSTRAINT `FK_DELIVERY_TO_RETURN_DELIVERY_1` FOREIGN KEY (`delivery_id`) REFERENCES `DELIVERY` (`delivery_id`);

ALTER TABLE `MONEY_WALLET` ADD CONSTRAINT `FK_WALLET_TO_MONEY_WALLET_1` FOREIGN KEY (`wallet_id`) REFERENCES `WALLET` (`wallet_id`);

ALTER TABLE `ORDERS` ADD CONSTRAINT `FK_BOORMI_TO_ORDERS_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `ORDERS` ADD CONSTRAINT `FK_DREAMI_TO_ORDERS_1` FOREIGN KEY (`dreami_id`) REFERENCES `DREAMI` (`dreami_id`);

ALTER TABLE `POINT_LEDGERS` ADD CONSTRAINT `FK_POINT_WALLET_TO_POINT_LEDGERS_1` FOREIGN KEY (`wallet_id`) REFERENCES `POINT_WALLET` (`wallet_id`);

ALTER TABLE `POINT_LEDGERS` ADD CONSTRAINT `FK_POINT_TX_TO_POINT_LEDGERS_1` FOREIGN KEY (`point_tx_id`) REFERENCES `POINT_TX` (`point_tx_id`);

ALTER TABLE `ACCIDENT_PROOF` ADD CONSTRAINT `FK_DELIVERY_ACCIDENT_TO_ACCIDENT_PROOF_1` FOREIGN KEY (`accident_id`) REFERENCES `DELIVERY_ACCIDENT` (`accident_id`);

ALTER TABLE `DREAMI` ADD CONSTRAINT `FK_BOORMI_TO_DREAMI_1` FOREIGN KEY (`dreami_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `ORDER_STATUS_HISTORY` ADD CONSTRAINT `FK_ORDERS_TO_ORDER_STATUS_HISTORY_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `PAYMENT_METHOD` ADD CONSTRAINT `FK_BOORMI_TO_PAYMENT_METHOD_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `PAYMENT` ADD CONSTRAINT `FK_BOORMI_TO_PAYMENT_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `MONEY_TX` ADD CONSTRAINT `FK_ORDERS_TO_MONEY_TX_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `MONEY_TX` ADD CONSTRAINT `FK_MONEY_WALLET_TO_MONEY_TX_1` FOREIGN KEY (`wallet_id`) REFERENCES `MONEY_WALLET` (`wallet_id`);

ALTER TABLE `MONEY_LEDGERS` ADD CONSTRAINT `FK_MONEY_WALLET_TO_MONEY_LEDGERS_1` FOREIGN KEY (`wallet_id`) REFERENCES `MONEY_WALLET` (`wallet_id`);

ALTER TABLE `MONEY_LEDGERS` ADD CONSTRAINT `FK_MONEY_TX_TO_MONEY_LEDGERS_1` FOREIGN KEY (`money_tx_id`) REFERENCES `MONEY_TX` (`money_tx_id`);

ALTER TABLE `POINT_TX` ADD CONSTRAINT `FK_PAYMENT_TO_POINT_TX_1` FOREIGN KEY (`payment_id`) REFERENCES `PAYMENT` (`payment_id`);

ALTER TABLE `POINT_TX` ADD CONSTRAINT `FK_ORDERS_TO_POINT_TX_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `POINT_TX` ADD CONSTRAINT `FK_POINT_WALLET_TO_POINT_TX_1` FOREIGN KEY (`wallet_id`) REFERENCES `POINT_WALLET` (`wallet_id`);

ALTER TABLE `EXCHANGES` ADD CONSTRAINT `FK_POINT_TX_TO_EXCHANGES_1` FOREIGN KEY (`point_tx_id`) REFERENCES `POINT_TX` (`point_tx_id`);

ALTER TABLE `EXCHANGES` ADD CONSTRAINT `FK_MONEY_TX_TO_EXCHANGES_1` FOREIGN KEY (`money_tx_id`) REFERENCES `MONEY_TX` (`money_tx_id`);

ALTER TABLE `EXCHANGES` ADD CONSTRAINT `FK_WALLET_TO_EXCHANGES_1` FOREIGN KEY (`wallet_id`) REFERENCES `WALLET` (`wallet_id`);

ALTER TABLE `BOORMI_REVIEW` ADD CONSTRAINT `FK_ORDERS_TO_BOORMI_REVIEW_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `CANCEL` ADD CONSTRAINT `FK_ORDERS_TO_CANCEL_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `ADDRESS` ADD CONSTRAINT `FK_BOORMI_TO_ADDRESS_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `PICKUP_CERTIFICATION` ADD CONSTRAINT `FK_ORDERS_TO_PICKUP_CERTIFICATION_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `BOORMI_REJECT_HISTORY` ADD CONSTRAINT `FK_BOORMI_TO_BOORMI_REJECT_HISTORY_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `MATCHING` ADD CONSTRAINT `FK_ORDERS_TO_MATCHING_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `DELIVERY_CERTIFICATION` ADD CONSTRAINT `FK_DELIVERY_TO_DELIVERY_CERTIFICATION_1` FOREIGN KEY (`delivery_id`) REFERENCES `DELIVERY` (`delivery_id`);

ALTER TABLE `WALLET` ADD CONSTRAINT `FK_BOORMI_TO_WALLET_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `POINT_WALLET` ADD CONSTRAINT `FK_WALLET_TO_POINT_WALLET_1` FOREIGN KEY (`wallet_id`) REFERENCES `WALLET` (`wallet_id`);

ALTER TABLE `SETTLEMENT_DETAILS` ADD CONSTRAINT `FK_DREAMI_TO_SETTLEMENT_DETAILS_1` FOREIGN KEY (`dreami_id`) REFERENCES `DREAMI` (`dreami_id`);

-- 배송 정보를 order_id로 조회 + 비관적 락(SELECT ... FOR UPDATE)이 주문 1행에만 걸리도록 인덱스를 명시한다(FK 자동 인덱스에 의존하지 않음).
-- FK보다 먼저 생성해 MySQL이 이 인덱스를 재사용하도록 한다(중복 인덱스 방지).
CREATE INDEX `IX_DELIVERY_ORDER_ID` ON `DELIVERY` (`order_id`);

ALTER TABLE `DELIVERY` ADD CONSTRAINT `FK_ORDERS_TO_DELIVERY_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

ALTER TABLE `DELIVERY_ACCIDENT` ADD CONSTRAINT `FK_DELIVERY_TO_DELIVERY_ACCIDENT_1` FOREIGN KEY (`delivery_id`) REFERENCES `DELIVERY` (`delivery_id`);

ALTER TABLE `COMPENSATION_CLAIM` ADD CONSTRAINT `FK_DELIVERY_ACCIDENT_TO_COMPENSATION_CLAIM_1` FOREIGN KEY (`accident_id`) REFERENCES `DELIVERY_ACCIDENT` (`accident_id`);

ALTER TABLE `DREAMI_REQUEST_DENIED_DETAILS` ADD CONSTRAINT `FK_DREAMI_TO_DREAMI_REQUEST_DENIED_DETAILS_1` FOREIGN KEY (`dreami_id`) REFERENCES `DREAMI` (`dreami_id`);

ALTER TABLE `DREAMI_REVIEW` ADD CONSTRAINT `FK_ORDERS_TO_DREAMI_REVIEW_1` FOREIGN KEY (`order_id`) REFERENCES `ORDERS` (`order_id`);

-- ============================================================
-- ERD 도구 생성분 이후 추가 (presigned URL 업로드 세션 추적용)
-- ============================================================

CREATE TABLE `UPLOAD_SESSION` (
                                  `upload_session_id`  binary(16)    NOT NULL,
                                  `boormi_id`          binary(16)    NOT NULL,
                                  `purpose`            varchar(50)   NOT NULL,
                                  `resource_id`        binary(16)    NULL,
                                  `s3_key`             varchar(500)  NOT NULL,
                                  `status`             enum('ISSUED', 'CONSUMED')  NOT NULL  DEFAULT 'ISSUED',
                                  `issued_dtm`         timestamp     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                  `consumed_dtm`       timestamp     NULL
);

ALTER TABLE `UPLOAD_SESSION` ADD CONSTRAINT `PK_UPLOAD_SESSION` PRIMARY KEY (`upload_session_id`);

ALTER TABLE `UPLOAD_SESSION` ADD CONSTRAINT `UQ_UPLOAD_SESSION_S3_KEY` UNIQUE (`s3_key`);

ALTER TABLE `UPLOAD_SESSION` ADD CONSTRAINT `FK_BOORMI_TO_UPLOAD_SESSION_1` FOREIGN KEY (`boormi_id`) REFERENCES `BOORMI` (`boormi_id`);
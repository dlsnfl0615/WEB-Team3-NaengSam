CREATE TABLE `PAYMENT_METHOD` (
                                  `payment_method_id` BINARY(16) NOT NULL,
                                  `billing_key` VARCHAR(255) NOT NULL COMMENT 'unique',
                                  `pg_name` VARCHAR(255) NOT NULL,
                                  `payment_method_cd` ENUM(
        'CARD',
        'BANK_TRANSFER',
        'TOSS_PAY'
    ) NOT NULL,
                                  `masking_no` VARCHAR(20) NOT NULL,
                                  `is_default_payment` BOOLEAN NOT NULL DEFAULT FALSE,
                                  `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  `updated_dtm` TIMESTAMP NULL,
                                  `deleted_dtm` TIMESTAMP NULL,
                                  `boormi_id` BINARY(16) NOT NULL
);

CREATE TABLE `MONEY_WALLET` (
                                `money_wallet_id` BINARY(16) NOT NULL,
                                `wallet_id` BINARY(16) NOT NULL,
                                `birthdate` DATE NOT NULL,
                                `pending_amount` BIGINT NOT NULL DEFAULT 0,
                                `amount` BIGINT NOT NULL DEFAULT 0,
                                `updated_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `EXCHANGES` (
                             `exchanges_id` BINARY(16) NOT NULL,
                             `amount` BIGINT NOT NULL,
                             `created_dtm` TIMESTAMP NOT NULL,
                             `point_tx_id` BINARY(16) NOT NULL,
                             `money_tx_id` BINARY(16) NOT NULL,
                             `wallet_id` BINARY(16) NOT NULL
);

CREATE TABLE `BOORMI_REVIEW` (
                                 `review_id` BINARY(16) NOT NULL,
                                 `score` TINYINT NOT NULL,
                                 `detail` VARCHAR(200) NULL,
                                 `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `updated_dtm` TIMESTAMP NULL,
                                 `deleted_dtm` TIMESTAMP NULL,
                                 `order_id` BINARY(16) NOT NULL
);

CREATE TABLE `ACCIDENT_PROOF` (
                                  `proof_id` BINARY(16) NOT NULL,
                                  `accident_id` BINARY(16) NOT NULL,
                                  `proof_type` VARCHAR(200) NULL,
                                  `file_url` VARCHAR(500) NULL,
                                  `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `MONEY_LEDGERS` (
                                 `money_ledgers_id` BINARY(16) NOT NULL,
                                 `money_tx_id` BINARY(16) NOT NULL,
                                 `birthdate` DATE NOT NULL,
                                 `amount` BIGINT NOT NULL DEFAULT 0,
                                 `balance_after` BIGINT NOT NULL DEFAULT 0,
                                 `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `money_wallet_id` BINARY(16) NOT NULL,
                                 `wallet_id` BINARY(16) NOT NULL
);

CREATE TABLE `POINT_TX` (
                            `point_tx_id` BINARY(16) NOT NULL,
                            `type` ENUM(
        'CHARGE',
        'PAYMENT',
        'REFUND',
        'EXCHANGE_IN'
    ) NOT NULL,
                            `status` ENUM(
        'PAID',
        'REFUNDED_PARTIAL',
        'REFUNDED_FULL'
    ) NOT NULL,
                            `amount` BIGINT NOT NULL,
                            `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `updated_dtm` TIMESTAMP NULL,
                            `payment_id` BINARY(16) NULL,
                            `order_id` BINARY(16) NULL,
                            `point_wallet_id` BINARY(16) NOT NULL,
                            `wallet_id` BINARY(16) NOT NULL
);

CREATE TABLE `PICKUP_CERTIFICATION` (
                                        `certification_id` BINARY(16) NOT NULL,
                                        `is_contact` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '대면, 비대면',
                                        `image_url` VARCHAR(500) NULL,
                                        `sign_url` VARCHAR(500) NULL,
                                        `submitted_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `order_id` BINARY(16) NOT NULL
);

CREATE TABLE `ORDERS` (
                          `order_id` BINARY(16) NOT NULL,
                          `boormi_id` BINARY(16) NOT NULL,
                          `dreami_id` BINARY(16) NULL,
                          `item_name` VARCHAR(50) NOT NULL,
                          `item_cd` ENUM(
        'DOCUMENT',
        'SAMPLE',
        'PACKAGE',
        'ETC'
    ) NOT NULL,
                          `item_detail` VARCHAR(255) NULL,
                          `delivery_amount` BIGINT NULL,
                          `order_cd` ENUM(
        'MATCHING',
        'PENDING_BOORMI_CONFIRMATION',
        'IN_PROGRESS',
        'WAITING_CONFIRMATION',
        'COMPLETED',
        'CANCELLED',
        'CLAIM_REVIEW'
    ) NOT NULL,
                          `delivery_eta` INT NOT NULL COMMENT '수정 불가능 (변동 사항은 변동 예상시간)',
                          `origin_latitude` DECIMAL(11, 8) NULL,
                          `origin_longitude` DECIMAL(11, 8) NULL,
                          `origin_alias` VARCHAR(50) NULL,
                          `origin_address_line_1` VARCHAR(255) NULL,
                          `origin_address_line_2` VARCHAR(255) NULL,
                          `destination_latitude` DECIMAL(11, 8) NULL,
                          `destination_longitude` DECIMAL(11, 8) NULL,
                          `destination_alias` VARCHAR(50) NULL,
                          `destination_address_line_1` VARCHAR(255) NULL,
                          `destination_address_line_2` VARCHAR(255) NULL,
                          `delivery_request` VARCHAR(255) NULL,
                          `image_url` VARCHAR(500) NULL,
                          `delivery_request_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `DELIVERY` (
                            `delivery_id` BINARY(16) NOT NULL,
                            `delivery_cd` ENUM(
        'PICKING_UP',
        'DELIVERING',
        'DELAYED',
        'SUSPENDED',
        'PARTNER_HANDOFF_PENDING',
        'TRANSFERRED_TO_PARTNER',
        'RETURNING',
        'RETURNED',
        'COMPLETED',
        'TERMINATED'
    ) NOT NULL,
                            `picked_up_dtm` TIMESTAMP NULL,
                            `delivery_start_dtm` TIMESTAMP NULL,
                            `delivery_end_dtm` TIMESTAMP NULL,
                            `received_dtm` TIMESTAMP NULL,
                            `order_id` BINARY(16) NOT NULL
);

CREATE TABLE `CANCEL` (
                          `cancel_id` BINARY(16) NOT NULL,
                          `canceled_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `cancel_reason` VARCHAR(50) NULL,
                          `note` TEXT NULL,
                          `is_penalty_applied` BOOLEAN NOT NULL,
                          `canceler_cd` ENUM(
        'ADMIN',
        'BOORMI',
        'DREAMI'
    ) NOT NULL,
                          `order_id` BINARY(16) NOT NULL,
                          `birthdate` DATE NOT NULL
);

CREATE TABLE `DREAMI_REQUEST_DENIED_DETAILS` (
                                                 `reject_id` BINARY(16) NOT NULL,
                                                 `dreami_id` BINARY(16) NOT NULL,
                                                 `rejected_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 `reject_detail` VARCHAR(200) NULL,
                                                 `birthdate` DATE NOT NULL
);

CREATE TABLE `ADDRESS` (
                           `address_id` BINARY(16) NOT NULL,
                           `address_alias` VARCHAR(50) NULL,
                           `longitude` DECIMAL(11, 8) NOT NULL,
                           `latitude` DECIMAL(11, 8) NOT NULL,
                           `address_line_2` VARCHAR(255) NOT NULL,
                           `address_line_1` VARCHAR(255) NOT NULL,
                           `boormi_id` BINARY(16) NOT NULL,
                           `birthdate` DATE NOT NULL
);

CREATE TABLE `PAYMENT` (
                           `payment_id` BINARY(16) NOT NULL,
                           `payment_amount` BIGINT NOT NULL,
                           `payment_cd` ENUM(
        'CARD',
        'BANK_TRANSFER',
        'TOSS_PAY'
    ) NOT NULL,
                           `payment_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           `refund_dtm` TIMESTAMP NULL,
                           `boormi_id` BINARY(16) NOT NULL,
                           `birthdate` DATE NOT NULL
);

CREATE TABLE `PARTNER_HANDOFF` (
                                   `partner_handoff_id` BINARY(16) NOT NULL,
                                   `order_id` BINARY(16) NOT NULL,
                                   `partner_id` BINARY(16) NOT NULL,
                                   `accident_id` BINARY(16) NULL,
                                   `handoff_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `is_picked_up` BOOLEAN NOT NULL,
                                   `note` TEXT NULL,
                                   `handoff_reason` VARCHAR(50) NULL,
                                   `picked_up_dtm` TIMESTAMP NULL,
                                   `birthdate` DATE NOT NULL
);

CREATE TABLE `MATCHING` (
                            `matching_id` BINARY(16) NOT NULL,
                            `accepted_dtm` TIMESTAMP NULL,
                            `canceled_dtm` TIMESTAMP NULL,
                            `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `order_id` BINARY(16) NOT NULL,
                            `birthdate` DATE NOT NULL
);

CREATE TABLE `PARTNER` (
                           `partner_id` BINARY(16) NOT NULL,
                           `phone_number` VARCHAR(50) NULL,
                           `address` VARCHAR(255) NULL,
                           `name` VARCHAR(255) NULL
);

CREATE TABLE `ORDER_STATUS_HISTORY` (
                                        `order_status_id` BINARY(16) NOT NULL,
                                        `previous_cd` ENUM(
        'MATCHING',
        'PENDING_BOORMI_CONFIRMATION',
        'IN_PROGRESS',
        'WAITING_CONFIRMATION',
        'COMPLETED',
        'CANCELLED',
        'CLAIM_REVIEW'
    ) NULL,
                                        `new_cd` ENUM(
        'MATCHING',
        'PENDING_BOORMI_CONFIRMATION',
        'IN_PROGRESS',
        'WAITING_CONFIRMATION',
        'COMPLETED',
        'CANCELLED',
        'CLAIM_REVIEW'
    ) NOT NULL,
                                        `changed_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `order_id` BINARY(16) NOT NULL,
                                        `birthdate` DATE NOT NULL
);

CREATE TABLE `COMPENSATION_CLAIM` (
                                      `claim_id` BINARY(16) NOT NULL,
                                      `request_cd` ENUM(
        'REQUESTED',
        'REVIEWING',
        'APPROVED',
        'REJECTED'
    ) NOT NULL DEFAULT 'REQUESTED',
                                      `claim_amount` BIGINT NOT NULL DEFAULT 0,
                                      `accident_id` BINARY(16) NOT NULL,
                                      `compensation_amount` BIGINT NOT NULL DEFAULT 0,
                                      `decision_reason` TEXT NULL,
                                      `audit_dtm` TIMESTAMP NULL,
                                      `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `birthdate` DATE NOT NULL
);

CREATE TABLE `POINT_WALLET` (
                                `point_wallet_id` BINARY(16) NOT NULL,
                                `wallet_id` BINARY(16) NOT NULL,
                                `birthdate` DATE NOT NULL,
                                `amount` BIGINT NOT NULL DEFAULT 0,
                                `updated_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `DREAMI_REVIEW` (
                                 `review_id` BINARY(16) NOT NULL,
                                 `score` TINYINT NOT NULL,
                                 `content` VARCHAR(200) NULL,
                                 `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `updated_dtm` TIMESTAMP NULL,
                                 `deleted_dtm` TIMESTAMP NULL,
                                 `order_id` BINARY(16) NOT NULL,
                                 `birthdate` DATE NOT NULL
);

CREATE TABLE `DREAMI` (
                          `dreami_id` BINARY(16) NOT NULL,
                          `reject_detail` VARCHAR(255) NULL,
                          `request_dtm` TIMESTAMP NULL,
                          `review_dtm` TIMESTAMP NULL,
                          `request_cd` ENUM(
        'REQUESTED',
        'REVIEWING',
        'APPROVED',
        'REJECTED'
    ) NOT NULL,
                          `id_card_url` VARCHAR(500) NOT NULL,
                          `dreami_avg_score` DECIMAL(3, 2) NOT NULL DEFAULT 0,
                          `criminal_record_url` VARCHAR(500) NOT NULL
);

CREATE TABLE `BOORMI` (
                          `boormi_id` BINARY(16) NOT NULL,
                          `email` VARCHAR(255) NOT NULL COMMENT 'unique',
                          `password` VARCHAR(255) NOT NULL,
                          `name` VARCHAR(50) NOT NULL,
                          `phone_number` VARCHAR(50) NOT NULL COMMENT 'unique',
                          `birthdate` DATE NOT NULL,
                          `user_cd` ENUM(
        'ACTIVE',
        'DELETED',
        'RESTRICTED',
        'BANNED'
    ) NULL,
                          `is_dreami_activated` BOOLEAN NULL DEFAULT FALSE,
                          `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `updated_dtm` TIMESTAMP NULL,
                          `deleted_dtm` TIMESTAMP NULL,
                          `boormi_avg_score` DECIMAL(3, 2) NOT NULL DEFAULT 0,
                          `restricted_dtm` TIMESTAMP NULL
);

CREATE TABLE `DELIVERY_CERTIFICATION` (
                                          `certification_id` BINARY(16) NOT NULL,
                                          `delivery_id` BINARY(16) NOT NULL,
                                          `is_contact` BOOLEAN NOT NULL DEFAULT TRUE,
                                          `image_url` VARCHAR(500) NULL,
                                          `sign_url` VARCHAR(500) NULL,
                                          `submitted_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `BOORMI_REJECT_HISTORY` (
                                         `reject_id` BINARY(16) NOT NULL,
                                         `reject_dtm` TIMESTAMP NOT NULL,
                                         `reject_detail` VARCHAR(255) NULL,
                                         `boormi_id` BINARY(16) NOT NULL,
                                         `birthdate` DATE NOT NULL
);

CREATE TABLE `DELIVERY_ACCIDENT` (
                                     `accident_id` BINARY(16) NOT NULL,
                                     `delivery_id` BINARY(16) NOT NULL,
                                     `accident_type` VARCHAR(50) NULL,
                                     `accident_details` TEXT NULL,
                                     `processing_cd` ENUM(
        'REPORTED',
        'PROCESSING',
        'RESOLVED',
        'REJECTED'
    ) NULL,
                                     `report_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     `resolution_dtm` TIMESTAMP NULL
);

CREATE TABLE `RETURN_DELIVERY` (
                                   `return_id` BINARY(16) NOT NULL,
                                   `delivery_id` BINARY(16) NOT NULL,
                                   `return_reason` TEXT NULL,
                                   `return_start_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `return_end_dtm` TIMESTAMP NULL,
                                   `return_cd` ENUM(
        'REPORTED',
        'PROCESSING',
        'RESOLVED',
        'REJECTED'
    ) NULL,
                                   `birthdate` DATE NOT NULL
);

CREATE TABLE `MONEY_TX` (
                            `money_tx_id` BINARY(16) NOT NULL,
                            `status` ENUM(
        'PENDING',
        'SETTLED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING',
                            `amount` BIGINT NOT NULL DEFAULT 0,
                            `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `money_wallet_id` BINARY(16) NOT NULL,
                            `wallet_id` BINARY(16) NOT NULL,
                            `updated_dtm` TIMESTAMP NULL,
                            `type` ENUM(
        'SETTLEMENT',
        'REVERSAL',
        'CLAIM_ADJUSTMENT',
        'EXCHANGE_OUT'
    ) NOT NULL,
                            `order_id` BINARY(16) NULL,
                            `birthdate` DATE NOT NULL
);

CREATE TABLE `WALLET` (
                          `wallet_id` BINARY(16) NOT NULL,
                          `boormi_id` BINARY(16) NOT NULL,
                          `birthdate` DATE NOT NULL
);

CREATE TABLE `POINT_LEDGERS` (
                                 `point_ledgers_id` BINARY(16) NOT NULL,
                                 `point_tx_id` BINARY(16) NOT NULL,
                                 `amount` BIGINT NOT NULL DEFAULT 0,
                                 `balance_after` BIGINT NOT NULL,
                                 `created_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `point_wallet_id` BINARY(16) NOT NULL,
                                 `wallet_id` BINARY(16) NOT NULL
);

CREATE TABLE `SETTLEMENT_DETAILS` (
                                      `settlement_id` BINARY(16) NOT NULL,
                                      `dreami_id` BINARY(16) NOT NULL,
                                      `birthdate` DATE NOT NULL,
                                      `settlement_account` VARCHAR(50) NOT NULL,
                                      `registered_dtm` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `settlement_bank` VARCHAR(50) NOT NULL,
                                      `account_holder` VARCHAR(50) NULL
);


-- =========================================================
-- PRIMARY KEY
-- =========================================================

ALTER TABLE `PAYMENT_METHOD`
    ADD CONSTRAINT `PK_PAYMENT_METHOD`
        PRIMARY KEY (`payment_method_id`);

ALTER TABLE `MONEY_WALLET`
    ADD CONSTRAINT `PK_MONEY_WALLET`
        PRIMARY KEY (`money_wallet_id`, `wallet_id`, `birthdate`);

ALTER TABLE `EXCHANGES`
    ADD CONSTRAINT `PK_EXCHANGES`
        PRIMARY KEY (`exchanges_id`);

ALTER TABLE `BOORMI_REVIEW`
    ADD CONSTRAINT `PK_BOORMI_REVIEW`
        PRIMARY KEY (`review_id`);

ALTER TABLE `ACCIDENT_PROOF`
    ADD CONSTRAINT `PK_ACCIDENT_PROOF`
        PRIMARY KEY (`proof_id`, `accident_id`);

ALTER TABLE `MONEY_LEDGERS`
    ADD CONSTRAINT `PK_MONEY_LEDGERS`
        PRIMARY KEY (`money_ledgers_id`, `money_tx_id`, `birthdate`);

ALTER TABLE `POINT_TX`
    ADD CONSTRAINT `PK_POINT_TX`
        PRIMARY KEY (`point_tx_id`);

ALTER TABLE `PICKUP_CERTIFICATION`
    ADD CONSTRAINT `PK_PICKUP_CERTIFICATION`
        PRIMARY KEY (`certification_id`);

ALTER TABLE `ORDERS`
    ADD CONSTRAINT `PK_ORDERS`
        PRIMARY KEY (`order_id`);

ALTER TABLE `DELIVERY`
    ADD CONSTRAINT `PK_DELIVERY`
        PRIMARY KEY (`delivery_id`);

ALTER TABLE `CANCEL`
    ADD CONSTRAINT `PK_CANCEL`
        PRIMARY KEY (`cancel_id`);

ALTER TABLE `DREAMI_REQUEST_DENIED_DETAILS`
    ADD CONSTRAINT `PK_DREAMI_REQUEST_DENIED_DETAILS`
        PRIMARY KEY (`reject_id`);

ALTER TABLE `ADDRESS`
    ADD CONSTRAINT `PK_ADDRESS`
        PRIMARY KEY (`address_id`);

ALTER TABLE `PAYMENT`
    ADD CONSTRAINT `PK_PAYMENT`
        PRIMARY KEY (`payment_id`);

ALTER TABLE `PARTNER_HANDOFF`
    ADD CONSTRAINT `PK_PARTNER_HANDOFF`
        PRIMARY KEY (`partner_handoff_id`);

ALTER TABLE `MATCHING`
    ADD CONSTRAINT `PK_MATCHING`
        PRIMARY KEY (`matching_id`);

ALTER TABLE `PARTNER`
    ADD CONSTRAINT `PK_PARTNER`
        PRIMARY KEY (`partner_id`);

ALTER TABLE `ORDER_STATUS_HISTORY`
    ADD CONSTRAINT `PK_ORDER_STATUS_HISTORY`
        PRIMARY KEY (`order_status_id`);

ALTER TABLE `COMPENSATION_CLAIM`
    ADD CONSTRAINT `PK_COMPENSATION_CLAIM`
        PRIMARY KEY (`claim_id`);

ALTER TABLE `POINT_WALLET`
    ADD CONSTRAINT `PK_POINT_WALLET`
        PRIMARY KEY (`point_wallet_id`, `wallet_id`, `birthdate`);

ALTER TABLE `DREAMI_REVIEW`
    ADD CONSTRAINT `PK_DREAMI_REVIEW`
        PRIMARY KEY (`review_id`);

ALTER TABLE `DREAMI`
    ADD CONSTRAINT `PK_DREAMI`
        PRIMARY KEY (`dreami_id`);

ALTER TABLE `BOORMI`
    ADD CONSTRAINT `PK_BOORMI`
        PRIMARY KEY (`boormi_id`);

ALTER TABLE `DELIVERY_CERTIFICATION`
    ADD CONSTRAINT `PK_DELIVERY_CERTIFICATION`
        PRIMARY KEY (`certification_id`);

ALTER TABLE `BOORMI_REJECT_HISTORY`
    ADD CONSTRAINT `PK_BOORMI_REJECT_HISTORY`
        PRIMARY KEY (`reject_id`);

ALTER TABLE `DELIVERY_ACCIDENT`
    ADD CONSTRAINT `PK_DELIVERY_ACCIDENT`
        PRIMARY KEY (`accident_id`);

ALTER TABLE `RETURN_DELIVERY`
    ADD CONSTRAINT `PK_RETURN_DELIVERY`
        PRIMARY KEY (`return_id`);

ALTER TABLE `MONEY_TX`
    ADD CONSTRAINT `PK_MONEY_TX`
        PRIMARY KEY (`money_tx_id`);

ALTER TABLE `WALLET`
    ADD CONSTRAINT `PK_WALLET`
        PRIMARY KEY (`wallet_id`);

ALTER TABLE `POINT_LEDGERS`
    ADD CONSTRAINT `PK_POINT_LEDGERS`
        PRIMARY KEY (`point_ledgers_id`, `point_tx_id`);

ALTER TABLE `SETTLEMENT_DETAILS`
    ADD CONSTRAINT `PK_SETTLEMENT_DETAILS`
        PRIMARY KEY (`settlement_id`, `dreami_id`, `birthdate`);


-- =========================================================
-- UNIQUE CONSTRAINT
-- =========================================================

ALTER TABLE `PAYMENT_METHOD`
    ADD CONSTRAINT `UK_PAYMENT_METHOD_BILLING_KEY`
        UNIQUE (`billing_key`);

ALTER TABLE `BOORMI`
    ADD CONSTRAINT `UK_BOORMI_EMAIL`
        UNIQUE (`email`);

ALTER TABLE `BOORMI`
    ADD CONSTRAINT `UK_BOORMI_PHONE_NUMBER`
        UNIQUE (`phone_number`);

-- =========================================================
-- FOREIGN KEY
-- =========================================================

ALTER TABLE `MONEY_WALLET`
    ADD CONSTRAINT `FK_WALLET_TO_MONEY_WALLET`
        FOREIGN KEY (`wallet_id`)
            REFERENCES `WALLET` (`wallet_id`);

ALTER TABLE `ACCIDENT_PROOF`
    ADD CONSTRAINT `FK_DELIVERY_ACCIDENT_TO_ACCIDENT_PROOF`
        FOREIGN KEY (`accident_id`)
            REFERENCES `DELIVERY_ACCIDENT` (`accident_id`);

ALTER TABLE `MONEY_LEDGERS`
    ADD CONSTRAINT `FK_MONEY_TX_TO_MONEY_LEDGERS`
        FOREIGN KEY (`money_tx_id`)
            REFERENCES `MONEY_TX` (`money_tx_id`);

ALTER TABLE `POINT_WALLET`
    ADD CONSTRAINT `FK_WALLET_TO_POINT_WALLET`
        FOREIGN KEY (`wallet_id`)
            REFERENCES `WALLET` (`wallet_id`);

ALTER TABLE `DREAMI`
    ADD CONSTRAINT `FK_BOORMI_TO_DREAMI`
        FOREIGN KEY (`dreami_id`)
            REFERENCES `BOORMI` (`boormi_id`);

ALTER TABLE `POINT_LEDGERS`
    ADD CONSTRAINT `FK_POINT_TX_TO_POINT_LEDGERS`
        FOREIGN KEY (`point_tx_id`)
            REFERENCES `POINT_TX` (`point_tx_id`);

ALTER TABLE `SETTLEMENT_DETAILS`
    ADD CONSTRAINT `FK_DREAMI_TO_SETTLEMENT_DETAILS`
        FOREIGN KEY (`dreami_id`)
            REFERENCES `DREAMI` (`dreami_id`);
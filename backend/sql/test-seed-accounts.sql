-- ============================================================
-- 초기 테스트 계정 시드 데이터 (boormi1/2, dreami1/2 @test.test)
-- 현재 브랜치(feat/256) sym-boorm-ddl.sql 기준으로 작성.
--
-- 계정 구성
--   boormi1@test.test, boormi2@test.test : 부르미 전용 (드리미 미등록, is_dreami_activated=false)
--   dreami1@test.test, dreami2@test.test : 부르미이자 드리미(APPROVED) — boormi_id = dreami_id 공유
--
-- 공통 비밀번호: test1234!  (PasswordHasher: PBKDF2WithHmacSHA256, "<saltHex>:<hashHex>" 형식)
--
-- 주문 내역 설계 (부르미/드리미 역할별 "수행한 내역" 개수를 정확히 맞추기 위한 배치)
--   - boormi1: 부르미(요청자)로서 완료 주문 2건 (dreami1 1건 수행 + dreami2 1건 수행)
--   - boormi2: 부르미(요청자)로서 완료 주문 2건 (dreami1 1건 수행 + dreami2 1건 수행)
--   - dreami1: 드리미(수행자)로서 완료 주문 10건, 2025-11 ~ 2026-08 한달에 하나씩
--              (그중 8건은 dreami2가 요청자, 나머지 2건은 boormi1/boormi2가 각자의 2건 중 1건으로 겸함)
--   - dreami2: 드리미(수행자)로서 완료 주문 2건 (boormi1 1건 + boormi2 1건 요청 — 위 boormi1/2의 나머지 1건과 겸함)
--   → boormi1/boormi2는 "부르미로서" 정확히 2건, dreami1/dreami2는 "드리미로서" 정확히 10/2건이 되도록
--     동일한 주문 행을 양쪽 집계에서 겸용한다(부르미 2계정만으로 드리미 12건을 전부 감당할 수 없어
--     dreami1↔dreami2가 서로의 요청자 역할도 겸함 — 이 부분은 명시적 설계 선택이다).
-- ============================================================

-- ------------------------------------------------------------
-- 1. BOORMI (계정 4개)
-- ------------------------------------------------------------
INSERT INTO `BOORMI`
(`boormi_id`, `email`, `password`, `name`, `phone_number`, `birthdate`, `user_cd`, `is_dreami_activated`, `created_dtm`, `boormi_avg_score`)
VALUES
(X'd725102b691e4b6c92ddf3e8e76d6f00', 'boormi1@test.test', 'ae3c0029b3fae40b3fa2c8c290b99941:41231c9aee836b7fee590e27fb38dde4f79d612228354e6115d3ccc4a23d2afe', '이보람', '010-1111-2221', '1995-01-01', 'ACTIVE', false, '2025-10-01 09:00:00', 0),
(X'c94dadd5ddad4f069cce589a3d9c58c2', 'boormi2@test.test', '597507c5c057d968139a9134e4e93d21:f772cac7725fd8f5f0cb929b8bdd433b8d87a86791ea721e9c54ab649069c53a', '최부름', '010-1111-2222', '1993-05-12', 'ACTIVE', false, '2025-10-01 09:00:00', 0),
(X'c2bed4f1dece4f50ab97b653212eded5', 'dreami1@test.test', '355c3c6e35620e868283f91c2f644df1:078d9996e8d336edef69dddff83ca8da7e0bcb6d2f283f0be6a4b5bda31f9337', '김드림', '010-2222-3331', '1997-03-20', 'ACTIVE', true, '2025-10-15 09:00:00', 0),
(X'5a9a31cee0fb4717848dbadd14b3ee86', 'dreami2@test.test', '5750188cceac07b9771b0db17f562007:f7803c5ebf0ea97cb39515817f52a238ec7d37396a2dde597779ba33883cf7ff', '박드림', '010-2222-3332', '1996-07-08', 'ACTIVE', true, '2025-10-15 09:00:00', 0);

-- ------------------------------------------------------------
-- 2. DREAMI (승인 완료 — dreami1, dreami2만)
-- ------------------------------------------------------------
INSERT INTO `DREAMI`
(`dreami_id`, `request_dtm`, `review_dtm`, `request_cd`, `id_card_key`, `dreami_avg_score`, `criminal_record_key`)
VALUES
(X'c2bed4f1dece4f50ab97b653212eded5', '2025-10-16 10:00:00', '2025-10-17 10:00:00', 'APPROVED', 'dreami/id-card/dreami1-test.jpg', 4.80, 'dreami/criminal-record/dreami1-test.pdf'),
(X'5a9a31cee0fb4717848dbadd14b3ee86', '2025-10-16 10:00:00', '2025-10-17 10:00:00', 'APPROVED', 'dreami/id-card/dreami2-test.jpg', 4.60, 'dreami/criminal-record/dreami2-test.pdf');

-- ------------------------------------------------------------
-- 3. WALLET + POINT_WALLET + MONEY_WALLET (계정 4개 전부)
-- ------------------------------------------------------------
INSERT INTO `WALLET` (`wallet_id`, `boormi_id`) VALUES
(X'464e3cb564974a109674dbf3d45ab9e1', X'd725102b691e4b6c92ddf3e8e76d6f00'),
(X'7ee8f8f0e498428f893a9d5c29cc170b', X'c94dadd5ddad4f069cce589a3d9c58c2'),
(X'24479f365cfe4d43a7d45ab3548c8f8f', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'c3e1fec8cf3c41f78b8f6eefb2741663', X'5a9a31cee0fb4717848dbadd14b3ee86');

INSERT INTO `POINT_WALLET` (`wallet_id`, `amount`, `updated_dtm`) VALUES
(X'464e3cb564974a109674dbf3d45ab9e1', 1000000, '2026-08-01 00:00:00'),
(X'7ee8f8f0e498428f893a9d5c29cc170b', 1000000, '2026-08-01 00:00:00'),
(X'24479f365cfe4d43a7d45ab3548c8f8f', 1000000, '2026-08-01 00:00:00'),
(X'c3e1fec8cf3c41f78b8f6eefb2741663', 1000000, '2026-08-01 00:00:00');

-- amount = 지금까지 정산 완료(SETTLED)된 MONEY_TX 합계와 맞춰둔다 (dreami1: 10건 합계, dreami2: 2건 합계).
INSERT INTO `MONEY_WALLET` (`wallet_id`, `pending_amount`, `amount`, `updated_dtm`) VALUES
(X'464e3cb564974a109674dbf3d45ab9e1', 0, 0, '2026-08-01 00:00:00'),
(X'7ee8f8f0e498428f893a9d5c29cc170b', 0, 0, '2026-08-01 00:00:00'),
(X'24479f365cfe4d43a7d45ab3548c8f8f', 0, 66500, '2026-08-05 00:00:00'),
(X'c3e1fec8cf3c41f78b8f6eefb2741663', 0, 15000, '2026-08-03 00:00:00');

-- ------------------------------------------------------------
-- 4. ORDERS (완료 내역 12건)
--    boormi1 dedicated: #9, #11 / boormi2 dedicated: #10, #12
--    dreami1 수행(10건, 월별): #1~#10 / dreami2 수행(2건): #11, #12
-- ------------------------------------------------------------
INSERT INTO `ORDERS`
(`order_id`, `boormi_id`, `item_name`, `item_cd`, `item_detail`, `delivery_amount`, `order_cd`, `delivery_eta`, `delivery_distance`,
 `origin_latitude`, `origin_longitude`, `origin_alias`, `origin_address_line_1`,
 `destination_latitude`, `destination_longitude`, `destination_alias`, `destination_address_line_1`,
 `delivery_request_dtm`, `dreami_id`)
VALUES
-- dreami1의 10개월치 (2025-11 ~ 2026-08, 그중 8건은 dreami2가 요청)
(X'd07666c9a8d04c83a6dc768c8241a8de', X'5a9a31cee0fb4717848dbadd14b3ee86', '서류봉투', 'DOCUMENT', '봉투 A4 사이즈', 5000, 'COMPLETED', 15, 1200,
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 '2025-11-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'c97334b2673a430eac2f78f19fe7aa89', X'5a9a31cee0fb4717848dbadd14b3ee86', '샘플 상품', 'SAMPLE', '화장품 샘플 박스', 6000, 'COMPLETED', 18, 1500,
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 '2025-12-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'72d72af1324f490680d79dc42311222f', X'5a9a31cee0fb4717848dbadd14b3ee86', '소포', 'PACKAGE', '소형 택배 상자', 9000, 'COMPLETED', 22, 2400,
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 '2026-01-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'd81045ca4e2b44debbdb81c188c9fece', X'5a9a31cee0fb4717848dbadd14b3ee86', '기타 물품', 'ETC', '기타 물품', 7000, 'COMPLETED', 20, 2000,
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 '2026-02-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'd4c1240c2f6c4a30a70027017ac01ba6', X'5a9a31cee0fb4717848dbadd14b3ee86', '서류봉투', 'DOCUMENT', '봉투 A4 사이즈', 5000, 'COMPLETED', 15, 1200,
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 '2026-03-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'78ffbfca7c964fcc999a27b69a0ea71e', X'5a9a31cee0fb4717848dbadd14b3ee86', '샘플 상품', 'SAMPLE', '화장품 샘플 박스', 6500, 'COMPLETED', 18, 1500,
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 '2026-04-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'8eb802c453294fc0a724a6f81b1be39d', X'5a9a31cee0fb4717848dbadd14b3ee86', '소포', 'PACKAGE', '소형 택배 상자', 8500, 'COMPLETED', 22, 2400,
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 '2026-05-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
(X'4d10a484e6264e4cbaf8f891db80615b', X'5a9a31cee0fb4717848dbadd14b3ee86', '기타 물품', 'ETC', '기타 물품', 7500, 'COMPLETED', 20, 2000,
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 '2026-06-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
-- boormi1의 부르미 내역 1/2 (동시에 dreami1의 10개월치 중 9번째)
(X'674a2f2c26eb41138dbf3765121b32e0', X'd725102b691e4b6c92ddf3e8e76d6f00', '서류봉투', 'DOCUMENT', '봉투 A4 사이즈', 5000, 'COMPLETED', 15, 1200,
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 '2026-07-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
-- boormi2의 부르미 내역 1/2 (동시에 dreami1의 10개월치 중 10번째)
(X'a5bcaf64129344479a17eca8ba8a4515', X'c94dadd5ddad4f069cce589a3d9c58c2', '샘플 상품', 'SAMPLE', '화장품 샘플 박스', 6000, 'COMPLETED', 18, 1500,
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 '2026-08-05 10:00:00', X'c2bed4f1dece4f50ab97b653212eded5'),
-- boormi1의 부르미 내역 2/2 (동시에 dreami2의 드리미 내역 1/2)
(X'e87f7baaee5b49939397c4824b66c4e4', X'd725102b691e4b6c92ddf3e8e76d6f00', '소포', 'PACKAGE', '소형 택배 상자', 8000, 'COMPLETED', 22, 2400,
 37.50060000, 127.03640000, '역삼 이디야', '서울 강남구 역삼로 180',
 37.49800000, 127.02760000, '강남 스타벅스', '서울 강남구 테헤란로 152',
 '2026-07-15 10:00:00', X'5a9a31cee0fb4717848dbadd14b3ee86'),
-- boormi2의 부르미 내역 2/2 (동시에 dreami2의 드리미 내역 2/2)
(X'30b975c836cd4d6cbc82869f0d226926', X'c94dadd5ddad4f069cce589a3d9c58c2', '기타 물품', 'ETC', '기타 물품', 7000, 'COMPLETED', 20, 2000,
 37.58220000, 127.00180000, '대학로 소극장', '서울 종로구 대학로 136',
 37.58940000, 127.01640000, '성북동 주민센터', '서울 성북구 동소문로 1',
 '2026-08-03 10:00:00', X'5a9a31cee0fb4717848dbadd14b3ee86');

-- ------------------------------------------------------------
-- 5. MONEY_TX (완료 주문 12건 전부 정산 완료 처리 → 드리미 수행자 지갑으로 정산)
-- ------------------------------------------------------------
INSERT INTO `MONEY_TX`
(`money_tx_id`, `status`, `amount`, `created_dtm`, `type`, `order_id`, `wallet_id`)
VALUES
(X'e355e47121b04ab3938ac0a24752b3a6', 'SETTLED', 5000, '2025-11-05 12:00:00', 'SETTLEMENT', X'd07666c9a8d04c83a6dc768c8241a8de', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'e9ff4fc624334856a0f060558cf443ed', 'SETTLED', 6000, '2025-12-05 12:00:00', 'SETTLEMENT', X'c97334b2673a430eac2f78f19fe7aa89', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'8ee00465e59e4e45b5118b0c4875fe21', 'SETTLED', 9000, '2026-01-05 12:00:00', 'SETTLEMENT', X'72d72af1324f490680d79dc42311222f', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'c2cc7e3dc93348bfb830ddc82fbf5295', 'SETTLED', 7000, '2026-02-05 12:00:00', 'SETTLEMENT', X'd81045ca4e2b44debbdb81c188c9fece', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'358657ce963845f586b1ae86fae7ea52', 'SETTLED', 5000, '2026-03-05 12:00:00', 'SETTLEMENT', X'd4c1240c2f6c4a30a70027017ac01ba6', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'783669824f35485b940dda49a5772de4', 'SETTLED', 6500, '2026-04-05 12:00:00', 'SETTLEMENT', X'78ffbfca7c964fcc999a27b69a0ea71e', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'b49eb3a20d8e4bf5be37498ac7378800', 'SETTLED', 8500, '2026-05-05 12:00:00', 'SETTLEMENT', X'8eb802c453294fc0a724a6f81b1be39d', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'47dc829c790c4709851518de96f2a3ee', 'SETTLED', 7500, '2026-06-05 12:00:00', 'SETTLEMENT', X'4d10a484e6264e4cbaf8f891db80615b', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'983aba2b50e84766acb57ea80568a3da', 'SETTLED', 5000, '2026-07-05 12:00:00', 'SETTLEMENT', X'674a2f2c26eb41138dbf3765121b32e0', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'9d15533afa4f4cf1b5d87727922dd511', 'SETTLED', 6000, '2026-08-05 12:00:00', 'SETTLEMENT', X'a5bcaf64129344479a17eca8ba8a4515', X'24479f365cfe4d43a7d45ab3548c8f8f'),
(X'b7e32001358b44eca4a9b63104edd008', 'SETTLED', 8000, '2026-07-15 12:00:00', 'SETTLEMENT', X'e87f7baaee5b49939397c4824b66c4e4', X'c3e1fec8cf3c41f78b8f6eefb2741663'),
(X'5b6a68bfb86049a1bf1c9e72aaf8d9f0', 'SETTLED', 7000, '2026-08-03 12:00:00', 'SETTLEMENT', X'30b975c836cd4d6cbc82869f0d226926', X'c3e1fec8cf3c41f78b8f6eefb2741663');

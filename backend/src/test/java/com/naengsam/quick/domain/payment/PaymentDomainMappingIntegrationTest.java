package com.naengsam.quick.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.payment.dto.MoneyTransactionRow;
import com.naengsam.quick.domain.payment.dto.PointTransactionRow;
import com.naengsam.quick.domain.payment.entity.Exchange;
import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxStatusCd;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.Payment;
import com.naengsam.quick.domain.payment.entity.PaymentCd;
import com.naengsam.quick.domain.payment.entity.PaymentMethod;
import com.naengsam.quick.domain.payment.entity.PointLedger;
import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxStatusCd;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.SettlementDetails;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.repository.ExchangeRepository;
import com.naengsam.quick.domain.payment.repository.MoneyLedgerRepository;
import com.naengsam.quick.domain.payment.repository.MoneyTxRepository;
import com.naengsam.quick.domain.payment.repository.MoneyWalletRepository;
import com.naengsam.quick.domain.payment.repository.PaymentMethodRepository;
import com.naengsam.quick.domain.payment.repository.PaymentRepository;
import com.naengsam.quick.domain.payment.repository.PointLedgerRepository;
import com.naengsam.quick.domain.payment.repository.PointTxRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.SettlementDetailsRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * 결제·지갑 도메인 엔티티가 실제 DDL(sym-boorm-ddl.sql)과 맞물리는지 검증한다. 컴파일만으로는 컬럼명 오타·타입 불일치를 잡을 수 없으므로, 운영과 같은 스키마를 올린 뒤 저장·조회를 한
 * 바퀴 돌린다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:wallet-mapping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:./sql/sym-boorm-ddl.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        // application.properties 가 환경변수로 받는 값. 슬라이스 테스트에도 바인딩되므로 채워 준다.
        "solapi.enabled=false"
})
class PaymentDomainMappingIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentMethodRepository paymentMethodRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private PointWalletRepository pointWalletRepository;
    @Autowired
    private MoneyWalletRepository moneyWalletRepository;
    @Autowired
    private PointTxRepository pointTxRepository;
    @Autowired
    private PointLedgerRepository pointLedgerRepository;
    @Autowired
    private MoneyTxRepository moneyTxRepository;
    @Autowired
    private MoneyLedgerRepository moneyLedgerRepository;
    @Autowired
    private ExchangeRepository exchangeRepository;
    @Autowired
    private SettlementDetailsRepository settlementDetailsRepository;

    private UUID boormiId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        Boormi boormi = boormi();
        entityManager.persist(boormi);
        boormiId = boormi.getBoormiId();

        entityManager.persist(Dreami.create(boormiId, "id-card-key", "criminal-record-key"));

        Orders order = order(boormiId);
        entityManager.persist(order);
        orderId = order.getOrderId();

        entityManager.flush();
    }

    @Test
    void 포인트_충전_흐름의_엔티티들이_실제_DDL에_저장되고_조회된다() {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));
        PointWallet pointWallet = pointWalletRepository.save(PointWallet.create(wallet.getWalletId()));
        Payment payment = paymentRepository.save(Payment.create(boormiId, 10_000L, PaymentCd.CARD));
        PointTx pointTx = pointTxRepository.save(
                PointTx.create(wallet.getWalletId(), PointTxTypeCd.CHARGE, 10_000L, payment.getPaymentId(), null));
        PointLedger ledger = pointLedgerRepository.save(
                PointLedger.create(wallet.getWalletId(), pointTx.getPointTxId(), 10_000L, 10_000L));

        flushAndClear();

        assertThat(walletRepository.findById(wallet.getWalletId()))
                .get().extracting(Wallet::getBoormiId).isEqualTo(boormiId);
        assertThat(pointWalletRepository.findById(pointWallet.getWalletId()))
                .get().extracting(PointWallet::getAmount).isEqualTo(0L);
        assertThat(paymentRepository.findById(payment.getPaymentId()))
                .get().extracting(Payment::getPaymentAmount, Payment::getPaymentCd)
                .containsExactly(10_000L, PaymentCd.CARD);
        assertThat(pointTxRepository.findById(pointTx.getPointTxId()))
                .get().extracting(PointTx::getType, PointTx::getStatus, PointTx::getPaymentId)
                .containsExactly(PointTxTypeCd.CHARGE, PointTxStatusCd.PAID, payment.getPaymentId());
        assertThat(pointLedgerRepository.findById(ledger.getPointLedgersId()))
                .get().extracting(PointLedger::getBalanceAfter).isEqualTo(10_000L);
    }

    @Test
    void 배달콜_포인트_결제_거래가_주문을_근거로_저장된다() {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));
        pointWalletRepository.save(PointWallet.create(wallet.getWalletId()));
        PointTx pointTx = pointTxRepository.save(
                PointTx.create(wallet.getWalletId(), PointTxTypeCd.PAYMENT, 5_000L, null, orderId));

        flushAndClear();

        assertThat(pointTxRepository.findById(pointTx.getPointTxId()))
                .get().extracting(PointTx::getType, PointTx::getOrderId, PointTx::getPaymentId)
                .containsExactly(PointTxTypeCd.PAYMENT, orderId, null);
    }

    @Test
    void 정산과_환전_흐름의_엔티티들이_실제_DDL에_저장되고_조회된다() {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));
        pointWalletRepository.save(PointWallet.create(wallet.getWalletId()));
        MoneyWallet moneyWallet = moneyWalletRepository.save(MoneyWallet.create(wallet.getWalletId()));
        MoneyTx moneyTx = moneyTxRepository.save(
                MoneyTx.create(wallet.getWalletId(), MoneyTxTypeCd.SETTLEMENT, 8_000L, orderId));
        MoneyLedger ledger = moneyLedgerRepository.save(
                MoneyLedger.create(wallet.getWalletId(), moneyTx.getMoneyTxId(), 8_000L, 8_000L));

        PointTx exchangeIn = pointTxRepository.save(
                PointTx.create(wallet.getWalletId(), PointTxTypeCd.EXCHANGE_IN, 8_000L, null, null));
        MoneyTx exchangeOut = moneyTxRepository.save(
                MoneyTx.create(wallet.getWalletId(), MoneyTxTypeCd.EXCHANGE_OUT, 8_000L, orderId));
        Exchange exchange = exchangeRepository.save(Exchange.create(
                wallet.getWalletId(), exchangeIn.getPointTxId(), exchangeOut.getMoneyTxId(), 8_000L));

        flushAndClear();

        assertThat(moneyWalletRepository.findById(moneyWallet.getWalletId()))
                .get().extracting(MoneyWallet::getAmount, MoneyWallet::getPendingAmount)
                .containsExactly(0L, 0L);
        assertThat(moneyTxRepository.findById(moneyTx.getMoneyTxId()))
                .get().extracting(MoneyTx::getType, MoneyTx::getStatus, MoneyTx::getOrderId)
                .containsExactly(MoneyTxTypeCd.SETTLEMENT, MoneyTxStatusCd.PENDING, orderId);
        assertThat(moneyLedgerRepository.findById(ledger.getMoneyLedgersId()))
                .get().extracting(MoneyLedger::getBalanceAfter).isEqualTo(8_000L);
        assertThat(exchangeRepository.findById(exchange.getExchangesId()))
                .get().extracting(Exchange::getPointTxId, Exchange::getMoneyTxId)
                .containsExactly(exchangeIn.getPointTxId(), exchangeOut.getMoneyTxId());
    }

    @Test
    void 머니에서_포인트로_전환한_거래는_주문없이_확정상태로_저장된다() {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));
        moneyWalletRepository.save(MoneyWallet.create(wallet.getWalletId()));
        MoneyTx exchangeOut = moneyTxRepository.save(
                MoneyTx.createSettled(wallet.getWalletId(), MoneyTxTypeCd.EXCHANGE_OUT, 5_000L, null));

        flushAndClear();

        // 전환은 근거가 될 주문이 없다. MONEY_TX.order_id 가 NOT NULL 이면 여기서 깨진다.
        assertThat(moneyTxRepository.findById(exchangeOut.getMoneyTxId()))
                .get().extracting(MoneyTx::getType, MoneyTx::getStatus, MoneyTx::getOrderId)
                .containsExactly(MoneyTxTypeCd.EXCHANGE_OUT, MoneyTxStatusCd.SETTLED, null);
    }

    @Test
    void 최근내역_조회는_원장과_거래유형을_함께_최신순으로_돌려준다() {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));
        UUID walletId = wallet.getWalletId();
        pointWalletRepository.save(PointWallet.create(walletId));
        moneyWalletRepository.save(MoneyWallet.create(walletId));
        PointTx charge = pointTxRepository.save(
                PointTx.create(walletId, PointTxTypeCd.CHARGE, 10_000L, null, null));
        PointTx payment = pointTxRepository.save(
                PointTx.create(walletId, PointTxTypeCd.PAYMENT, 4_000L, null, orderId));
        pointLedgerRepository.save(PointLedger.create(walletId, charge.getPointTxId(), 10_000L, 10_000L));
        pointLedgerRepository.save(PointLedger.create(walletId, payment.getPointTxId(), -4_000L, 6_000L));

        MoneyTx settlement = moneyTxRepository.save(
                MoneyTx.create(walletId, MoneyTxTypeCd.SETTLEMENT, 8_000L, orderId));
        moneyLedgerRepository.save(MoneyLedger.create(walletId, settlement.getMoneyTxId(), 8_000L, 8_000L));

        flushAndClear();

        assertThat(pointLedgerRepository.findRecentByWalletId(walletId, PageRequest.ofSize(20)))
                .extracting(PointTransactionRow::type, PointTransactionRow::amount,
                        PointTransactionRow::balanceAfter)
                .containsExactlyInAnyOrder(
                        tuple(PointTxTypeCd.CHARGE, 10_000L, 10_000L),
                        tuple(PointTxTypeCd.PAYMENT, -4_000L, 6_000L));
        assertThat(moneyLedgerRepository.findRecentByWalletId(walletId, PageRequest.ofSize(20)))
                .singleElement()
                .extracting(MoneyTransactionRow::type, MoneyTransactionRow::amount)
                .containsExactly(MoneyTxTypeCd.SETTLEMENT, 8_000L);
    }

    @Test
    void 결제수단과_정산계좌가_실제_DDL에_저장되고_조회된다() {
        PaymentMethod paymentMethod = paymentMethodRepository.save(PaymentMethod.create(
                boormiId, "billing-key-1", "TOSS", PaymentCd.CARD, "1234-****-****-5678", true));
        SettlementDetails settlementDetails = settlementDetailsRepository.save(
                SettlementDetails.create(boormiId, "국민은행", "1234567890", "홍길동"));

        flushAndClear();

        assertThat(paymentMethodRepository.findById(paymentMethod.getPaymentMethodId()))
                .get().extracting(PaymentMethod::getBillingKey, PaymentMethod::getMaskingNo,
                        PaymentMethod::isDefaultPayment, PaymentMethod::getDeletedDtm)
                .containsExactly("billing-key-1", "1234-****-****-5678", true, null);
        assertThat(settlementDetailsRepository.findAll())
                .singleElement()
                .extracting(SettlementDetails::getSettlementId, SettlementDetails::getDreamiId,
                        SettlementDetails::getSettlementBank, SettlementDetails::getAccountHolder)
                .containsExactly(settlementDetails.getSettlementId(), boormiId, "국민은행", "홍길동");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Boormi boormi() {
        return Boormi.create("wallet@test.com", "password", "지갑주인", "01012345678",
                LocalDate.of(1995, 1, 1));
    }

    private static Orders order(UUID boormiId) {
        return Orders.create(UUID.randomUUID(), boormiId, "서류봉투", ItemCd.DOCUMENT, null,
                5_000L, 30, null, null, null, addresses());
    }

    private static Addresses addresses() {
        return Addresses.builder()
                .originAddressLine1("서울시 강남구")
                .originAddressLine2("101호")
                .originLatitude(new BigDecimal("37.49790000"))
                .originLongitude(new BigDecimal("127.02760000"))
                .originAlias("출발지")
                .destinationAddressLine1("서울시 송파구")
                .destinationAddressLine2("202호")
                .destinationLatitude(new BigDecimal("37.51450000"))
                .destinationLongitude(new BigDecimal("127.10600000"))
                .destinationAlias("도착지")
                .build();
    }
}

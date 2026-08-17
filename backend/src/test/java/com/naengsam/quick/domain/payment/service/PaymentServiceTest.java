package com.naengsam.quick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxStatusCd;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.PointLedger;
import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxStatusCd;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.exception.PaymentErrorCode;
import com.naengsam.quick.domain.payment.repository.MoneyLedgerRepository;
import com.naengsam.quick.domain.payment.repository.MoneyTxRepository;
import com.naengsam.quick.domain.payment.repository.MoneyWalletRepository;
import com.naengsam.quick.domain.payment.repository.PointLedgerRepository;
import com.naengsam.quick.domain.payment.repository.PointTxRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 포인트 결제·환불·정산 로직 단위 테스트. 거래 기록(POINT_TX)·지갑 잔액·원장(POINT_LEDGERS) 세 가지가 함께 맞물려 움직이는지, 배달 완료 시 드리미 머니까지 이어지는지
 * 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private PointTxRepository pointTxRepository;

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    @Mock
    private MoneyWalletRepository moneyWalletRepository;

    @Mock
    private MoneyTxRepository moneyTxRepository;

    @Mock
    private MoneyLedgerRepository moneyLedgerRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static PointWallet pointWallet(UUID walletId, long amount) {
        PointWallet pointWallet = PointWallet.create(walletId);
        ReflectionTestUtils.setField(pointWallet, "amount", amount);
        return pointWallet;
    }

    private static MoneyWallet moneyWallet(UUID walletId, long amount) {
        MoneyWallet moneyWallet = MoneyWallet.create(walletId);
        ReflectionTestUtils.setField(moneyWallet, "amount", amount);
        return moneyWallet;
    }

    private static PointTx pendingTx(UUID walletId, UUID orderId, long amount) {
        return PointTx.createPending(walletId, PointTxTypeCd.PAYMENT, amount, null, orderId);
    }

    /**
     * boormiId → Wallet → PointWallet(비관적 락) 조회 경로를 세팅한다.
     */
    private void givenWallet(UUID boormiId, PointWallet pointWallet) {
        Wallet wallet = Wallet.create(boormiId);
        ReflectionTestUtils.setField(wallet, "walletId", pointWallet.getWalletId());
        given(walletRepository.findByBoormiId(boormiId)).willReturn(Optional.of(wallet));
        given(pointWalletRepository.findByIdForUpdate(pointWallet.getWalletId()))
                .willReturn(Optional.of(pointWallet));
    }

    /**
     * dreamiId → Wallet → MoneyWallet(비관적 락) 조회 경로를 세팅한다.
     */
    private void givenMoneyWallet(UUID dreamiId, MoneyWallet moneyWallet) {
        Wallet wallet = Wallet.create(dreamiId);
        ReflectionTestUtils.setField(wallet, "walletId", moneyWallet.getWalletId());
        given(walletRepository.findByBoormiId(dreamiId)).willReturn(Optional.of(wallet));
        given(moneyWalletRepository.findByIdForUpdate(moneyWallet.getWalletId()))
                .willReturn(Optional.of(moneyWallet));
    }

    @Test
    void 결제하면_잔액을_차감하고_PENDING_거래와_원장을_기록한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PointWallet wallet = pointWallet(walletId, 10000L);
        givenWallet(boormiId, wallet);
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.empty());
        given(pointTxRepository.save(any(PointTx.class))).willAnswer(i -> i.getArgument(0));

        paymentService.payWithPoint(boormiId, orderId, 4000L);

        ArgumentCaptor<PointTx> txCaptor = ArgumentCaptor.forClass(PointTx.class);
        then(pointTxRepository).should().save(txCaptor.capture());
        PointTx savedTx = txCaptor.getValue();
        assertThat(savedTx.getType()).isEqualTo(PointTxTypeCd.PAYMENT);
        // 포인트는 지금 빠지지만 배달이 끝나기 전이라 지급은 아직 확정되지 않았다
        assertThat(savedTx.getStatus()).isEqualTo(PointTxStatusCd.PENDING);
        assertThat(savedTx.getAmount()).isEqualTo(4000L);
        assertThat(savedTx.getOrderId()).isEqualTo(orderId);
        assertThat(savedTx.getWalletId()).isEqualTo(walletId);
        assertThat(savedTx.getPaymentId()).isNull(); // PG 충전이 아니므로 결제 근거가 없다

        assertThat(wallet.getAmount()).isEqualTo(6000L);

        ArgumentCaptor<PointLedger> ledgerCaptor = ArgumentCaptor.forClass(PointLedger.class);
        then(pointLedgerRepository).should().save(ledgerCaptor.capture());
        PointLedger savedLedger = ledgerCaptor.getValue();
        assertThat(savedLedger.getAmount()).isEqualTo(-4000L);
        assertThat(savedLedger.getBalanceAfter()).isEqualTo(6000L);
        assertThat(savedLedger.getPointTxId()).isEqualTo(savedTx.getPointTxId());
    }

    @Test
    void 잔액이_모자라면_INSUFFICIENT_POINT_예외이고_원장을_남기지_않는다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PointWallet wallet = pointWallet(UUID.randomUUID(), 3000L);
        givenWallet(boormiId, wallet);
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.empty());
        given(pointTxRepository.save(any(PointTx.class))).willAnswer(i -> i.getArgument(0));

        Throwable thrown = catchThrowable(() -> paymentService.payWithPoint(boormiId, orderId, 4000L));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(PaymentErrorCode.INSUFFICIENT_POINT);
        assertThat(wallet.getAmount()).isEqualTo(3000L);
        then(pointLedgerRepository).should(never()).save(any());
    }

    @Test
    void 지갑이_없으면_WALLET_NOT_FOUND_예외() {
        UUID boormiId = UUID.randomUUID();
        given(walletRepository.findByBoormiId(boormiId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(
                () -> paymentService.payWithPoint(boormiId, UUID.randomUUID(), 4000L));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND);
        then(pointTxRepository).should(never()).save(any());
    }

    @Test
    void 이미_결제된_주문이면_ALREADY_PAID_예외이고_잔액이_그대로다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PointWallet wallet = pointWallet(walletId, 10000L);
        givenWallet(boormiId, wallet);
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(pendingTx(walletId, orderId, 4000L)));

        Throwable thrown = catchThrowable(() -> paymentService.payWithPoint(boormiId, orderId, 4000L));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PAID);
        assertThat(wallet.getAmount()).isEqualTo(10000L);
        then(pointTxRepository).should(never()).save(any());
        then(pointLedgerRepository).should(never()).save(any());
    }

    @Test
    void 환불하면_거래는_새로_쌓지_않고_원본이_REFUNDED_FULL로_전이하며_잔액이_복구된다() {
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PointWallet wallet = pointWallet(walletId, 6000L);
        PointTx tx = pendingTx(walletId, orderId, 4000L);
        given(pointTxRepository.findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));
        given(pointWalletRepository.findByIdForUpdate(walletId)).willReturn(Optional.of(wallet));

        paymentService.refundByPoint(orderId);

        // 결제 4000 + 환불 4000 이 거래 한 행에 net 0 으로 남는다
        assertThat(tx.getStatus()).isEqualTo(PointTxStatusCd.REFUNDED_FULL);
        assertThat(tx.getUpdatedDtm()).isNotNull();
        then(pointTxRepository).should(never()).save(any());

        assertThat(wallet.getAmount()).isEqualTo(10000L);

        ArgumentCaptor<PointLedger> ledgerCaptor = ArgumentCaptor.forClass(PointLedger.class);
        then(pointLedgerRepository).should().save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualTo(4000L);
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualTo(10000L);
    }

    @Test
    void 이미_환불된_주문을_다시_환불해도_잔액이_변하지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PointTx tx = pendingTx(walletId, orderId, 4000L);
        tx.markRefundedFull();
        given(pointTxRepository.findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));

        paymentService.refundByPoint(orderId);

        then(pointWalletRepository).should(never()).findByIdForUpdate(any());
        then(pointLedgerRepository).should(never()).save(any());
    }

    @Test
    void 결제내역이_없는_주문을_환불하면_PAYMENT_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();
        given(pointTxRepository.findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> paymentService.refundByPoint(orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        then(pointLedgerRepository).should(never()).save(any());
    }

    /**
     * 동시 취소 두 건이 같은 PENDING 스냅샷을 읽어 둘 다 환불하는 이중환불을 막으려면, "이미 환불됐는지" 판단 자체가 락 안에서
     * 일어나야 한다. 잠기지 않은 조회로 되돌아가면 이 테스트가 깨진다.
     */
    @Test
    void 환불은_결제거래를_비관적_락으로_읽어_상태_판단을_직렬화한다() {
        UUID orderId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        PointTx tx = pendingTx(walletId, orderId, 4000L);
        given(pointTxRepository.findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));
        given(pointWalletRepository.findByIdForUpdate(walletId))
                .willReturn(Optional.of(pointWallet(walletId, 6000L)));

        paymentService.refundByPoint(orderId);

        then(pointTxRepository).should().findByOrderIdAndTypeForUpdate(orderId, PointTxTypeCd.PAYMENT);
        then(pointTxRepository).should(never()).findByOrderIdAndType(any(), any());
    }

    @Test
    void 정산하면_결제거래가_PAID로_전이하고_드리미_머니와_원장이_쌓인다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID pointWalletId = UUID.randomUUID();
        UUID moneyWalletId = UUID.randomUUID();
        PointTx tx = pendingTx(pointWalletId, orderId, 4000L);
        MoneyWallet wallet = moneyWallet(moneyWalletId, 6000L);
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));
        givenMoneyWallet(dreamiId, wallet);
        given(moneyTxRepository.save(any(MoneyTx.class))).willAnswer(i -> i.getArgument(0));

        paymentService.settleOrder(orderId, dreamiId);

        // 결제 거래는 새 행을 쌓지 않고 원본이 확정 상태로 전이한다
        assertThat(tx.getStatus()).isEqualTo(PointTxStatusCd.PAID);
        assertThat(tx.getUpdatedDtm()).isNotNull();
        then(pointTxRepository).should(never()).save(any());

        assertThat(wallet.getAmount()).isEqualTo(10000L);

        ArgumentCaptor<MoneyTx> txCaptor = ArgumentCaptor.forClass(MoneyTx.class);
        then(moneyTxRepository).should().save(txCaptor.capture());
        MoneyTx savedTx = txCaptor.getValue();
        assertThat(savedTx.getType()).isEqualTo(MoneyTxTypeCd.SETTLEMENT);
        assertThat(savedTx.getStatus()).isEqualTo(MoneyTxStatusCd.SETTLED);
        assertThat(savedTx.getAmount()).isEqualTo(4000L); // 수수료 없이 결제 금액 전액
        assertThat(savedTx.getOrderId()).isEqualTo(orderId);
        assertThat(savedTx.getWalletId()).isEqualTo(moneyWalletId);

        ArgumentCaptor<MoneyLedger> ledgerCaptor = ArgumentCaptor.forClass(MoneyLedger.class);
        then(moneyLedgerRepository).should().save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualTo(4000L);
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualTo(10000L);
        assertThat(ledgerCaptor.getValue().getMoneyTxId()).isEqualTo(savedTx.getMoneyTxId());
    }

    @Test
    void 이미_정산된_주문을_다시_정산해도_머니가_늘지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        PointTx tx = pendingTx(UUID.randomUUID(), orderId, 4000L);
        tx.markPaid();
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));

        paymentService.settleOrder(orderId, dreamiId);

        then(moneyWalletRepository).should(never()).findByIdForUpdate(any());
        then(moneyTxRepository).should(never()).save(any());
        then(moneyLedgerRepository).should(never()).save(any());
    }

    @Test
    void 환불된_주문은_정산해도_머니가_늘지_않는다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        PointTx tx = pendingTx(UUID.randomUUID(), orderId, 4000L);
        tx.markRefundedFull();
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.of(tx));

        paymentService.settleOrder(orderId, dreamiId);

        assertThat(tx.getStatus()).isEqualTo(PointTxStatusCd.REFUNDED_FULL);
        then(moneyTxRepository).should(never()).save(any());
        then(moneyLedgerRepository).should(never()).save(any());
    }

    @Test
    void 결제내역이_없는_주문을_정산하면_PAYMENT_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();
        given(pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT))
                .willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> paymentService.settleOrder(orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        then(moneyTxRepository).should(never()).save(any());
    }
}

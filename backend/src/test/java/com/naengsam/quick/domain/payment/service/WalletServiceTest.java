package com.naengsam.quick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.payment.dto.ExchangeRequest;
import com.naengsam.quick.domain.payment.dto.PointChargeRequest;
import com.naengsam.quick.domain.payment.dto.WalletDto;
import com.naengsam.quick.domain.payment.entity.Exchange;
import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxStatusCd;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.Payment;
import com.naengsam.quick.domain.payment.entity.PaymentCd;
import com.naengsam.quick.domain.payment.entity.PointLedger;
import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.exception.PaymentErrorCode;
import com.naengsam.quick.domain.payment.repository.ExchangeRepository;
import com.naengsam.quick.domain.payment.repository.MoneyLedgerRepository;
import com.naengsam.quick.domain.payment.repository.MoneyTxRepository;
import com.naengsam.quick.domain.payment.repository.MoneyWalletRepository;
import com.naengsam.quick.domain.payment.repository.PaymentRepository;
import com.naengsam.quick.domain.payment.repository.PointLedgerRepository;
import com.naengsam.quick.domain.payment.repository.PointTxRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 지갑 생성·조회·충전·전환 로직 단위 테스트. 잔액 변동이 거래(TX)·원장(LEDGERS) 기록과 항상 짝지어 일어나는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private MoneyWalletRepository moneyWalletRepository;

    @Mock
    private PointTxRepository pointTxRepository;

    @Mock
    private MoneyTxRepository moneyTxRepository;

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    @Mock
    private MoneyLedgerRepository moneyLedgerRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExchangeRepository exchangeRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void 지갑생성_회원지갑과_포인트지갑과_머니지갑을_같은_wallet_id_로_저장한다() {
        UUID boormiId = UUID.randomUUID();
        given(walletRepository.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));

        walletService.createWallet(boormiId);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        then(walletRepository).should().save(walletCaptor.capture());
        UUID walletId = walletCaptor.getValue().getWalletId();
        assertThat(walletCaptor.getValue().getBoormiId()).isEqualTo(boormiId);
        assertThat(walletId).isNotNull();

        ArgumentCaptor<PointWallet> pointCaptor = ArgumentCaptor.forClass(PointWallet.class);
        then(pointWalletRepository).should().save(pointCaptor.capture());
        assertThat(pointCaptor.getValue())
                .extracting(PointWallet::getWalletId, PointWallet::getAmount)
                .containsExactly(walletId, 0L);

        ArgumentCaptor<MoneyWallet> moneyCaptor = ArgumentCaptor.forClass(MoneyWallet.class);
        then(moneyWalletRepository).should().save(moneyCaptor.capture());
        assertThat(moneyCaptor.getValue())
                .extracting(MoneyWallet::getWalletId, MoneyWallet::getAmount, MoneyWallet::getPendingAmount)
                .containsExactly(walletId, 0L, 0L);
    }

    @Test
    void 포인트충전_결제근거와_거래와_원장을_함께_남기고_잔액을_올린다() {
        UUID boormiId = UUID.randomUUID();
        Wallet wallet = Wallet.create(boormiId);
        UUID walletId = wallet.getWalletId();
        PointWallet pointWallet = pointWalletWith(walletId, 2_000L);
        givenWallet(boormiId, wallet);
        givenBalanceLookup(walletId, pointWallet, moneyWalletWith(walletId, 0L));
        givenEmptyLedgers();
        given(paymentRepository.save(any(Payment.class))).willAnswer(returnsArgument());
        given(pointTxRepository.save(any(PointTx.class))).willAnswer(returnsArgument());
        given(pointWalletRepository.findByIdForUpdate(walletId)).willReturn(Optional.of(pointWallet));

        WalletDto result = walletService.chargePoint(boormiId,
                new PointChargeRequest(10_000L, PaymentCd.CARD));

        assertThat(result.pointAmount()).isEqualTo(12_000L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        then(paymentRepository).should().save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue())
                .extracting(Payment::getBoormiId, Payment::getPaymentAmount, Payment::getPaymentCd)
                .containsExactly(boormiId, 10_000L, PaymentCd.CARD);

        ArgumentCaptor<PointTx> txCaptor = ArgumentCaptor.forClass(PointTx.class);
        then(pointTxRepository).should().save(txCaptor.capture());
        assertThat(txCaptor.getValue())
                .extracting(PointTx::getType, PointTx::getAmount, PointTx::getPaymentId, PointTx::getOrderId)
                .containsExactly(PointTxTypeCd.CHARGE, 10_000L, paymentCaptor.getValue().getPaymentId(), null);

        // 원장은 잔액이 얼마나 움직여 얼마가 됐는지를 남긴다(+10000 을 더해 12000).
        ArgumentCaptor<PointLedger> ledgerCaptor = ArgumentCaptor.forClass(PointLedger.class);
        then(pointLedgerRepository).should().save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue())
                .extracting(PointLedger::getAmount, PointLedger::getBalanceAfter, PointLedger::getPointTxId)
                .containsExactly(10_000L, 12_000L, txCaptor.getValue().getPointTxId());
    }

    @Test
    void 머니전환_머니를_차감한_만큼_포인트를_적립하고_두_거래를_짝지어_남긴다() {
        UUID boormiId = UUID.randomUUID();
        Wallet wallet = Wallet.create(boormiId);
        UUID walletId = wallet.getWalletId();
        PointWallet pointWallet = pointWalletWith(walletId, 1_000L);
        MoneyWallet moneyWallet = moneyWalletWith(walletId, 8_000L);
        givenWallet(boormiId, wallet);
        givenBalanceLookup(walletId, pointWallet, moneyWallet);
        givenEmptyLedgers();
        given(moneyWalletRepository.findByIdForUpdate(walletId)).willReturn(Optional.of(moneyWallet));
        given(pointWalletRepository.findByIdForUpdate(walletId)).willReturn(Optional.of(pointWallet));
        given(moneyTxRepository.save(any(MoneyTx.class))).willAnswer(returnsArgument());
        given(pointTxRepository.save(any(PointTx.class))).willAnswer(returnsArgument());

        WalletDto result = walletService.exchangeMoneyToPoint(boormiId, new ExchangeRequest(5_000L));

        assertThat(result).extracting(WalletDto::moneyAmount, WalletDto::pointAmount)
                .containsExactly(3_000L, 6_000L);

        // 전환은 요청 시점에 잔액이 바로 빠지므로 PENDING 을 거치지 않는다.
        ArgumentCaptor<MoneyTx> moneyTxCaptor = ArgumentCaptor.forClass(MoneyTx.class);
        then(moneyTxRepository).should().save(moneyTxCaptor.capture());
        assertThat(moneyTxCaptor.getValue())
                .extracting(MoneyTx::getType, MoneyTx::getStatus, MoneyTx::getAmount, MoneyTx::getOrderId)
                .containsExactly(MoneyTxTypeCd.EXCHANGE_OUT, MoneyTxStatusCd.SETTLED, 5_000L, null);

        ArgumentCaptor<PointTx> pointTxCaptor = ArgumentCaptor.forClass(PointTx.class);
        then(pointTxRepository).should().save(pointTxCaptor.capture());
        assertThat(pointTxCaptor.getValue())
                .extracting(PointTx::getType, PointTx::getAmount)
                .containsExactly(PointTxTypeCd.EXCHANGE_IN, 5_000L);

        ArgumentCaptor<MoneyLedger> moneyLedgerCaptor = ArgumentCaptor.forClass(MoneyLedger.class);
        then(moneyLedgerRepository).should().save(moneyLedgerCaptor.capture());
        assertThat(moneyLedgerCaptor.getValue())
                .extracting(MoneyLedger::getAmount, MoneyLedger::getBalanceAfter)
                .containsExactly(-5_000L, 3_000L);

        ArgumentCaptor<PointLedger> pointLedgerCaptor = ArgumentCaptor.forClass(PointLedger.class);
        then(pointLedgerRepository).should().save(pointLedgerCaptor.capture());
        assertThat(pointLedgerCaptor.getValue())
                .extracting(PointLedger::getAmount, PointLedger::getBalanceAfter)
                .containsExactly(5_000L, 6_000L);

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        then(exchangeRepository).should().save(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue())
                .extracting(Exchange::getAmount, Exchange::getMoneyTxId, Exchange::getPointTxId)
                .containsExactly(5_000L, moneyTxCaptor.getValue().getMoneyTxId(),
                        pointTxCaptor.getValue().getPointTxId());
    }

    @Test
    void 머니전환_머니가_부족하면_INSUFFICIENT_MONEY_를_던지고_아무것도_남기지_않는다() {
        UUID boormiId = UUID.randomUUID();
        Wallet wallet = Wallet.create(boormiId);
        UUID walletId = wallet.getWalletId();
        MoneyWallet moneyWallet = moneyWalletWith(walletId, 1_000L);
        givenWallet(boormiId, wallet);
        given(moneyWalletRepository.findByIdForUpdate(walletId)).willReturn(Optional.of(moneyWallet));

        Throwable thrown = catchThrowable(
                () -> walletService.exchangeMoneyToPoint(boormiId, new ExchangeRequest(5_000L)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(PaymentErrorCode.INSUFFICIENT_MONEY);
        assertThat(moneyWallet.getAmount()).isEqualTo(1_000L);
        then(moneyTxRepository).should(never()).save(any());
        then(pointTxRepository).should(never()).save(any());
        then(exchangeRepository).should(never()).save(any());
    }

    private void givenWallet(UUID boormiId, Wallet wallet) {
        given(walletRepository.findByBoormiId(boormiId)).willReturn(Optional.of(wallet));
    }

    /** 응답을 만들 때 다시 읽는 잔액 조회. */
    private void givenBalanceLookup(UUID walletId, PointWallet pointWallet, MoneyWallet moneyWallet) {
        given(pointWalletRepository.findById(walletId)).willReturn(Optional.of(pointWallet));
        given(moneyWalletRepository.findById(walletId)).willReturn(Optional.of(moneyWallet));
    }

    private void givenEmptyLedgers() {
        given(pointLedgerRepository.findRecentByWalletId(any(UUID.class), any(Pageable.class)))
                .willReturn(List.of());
        given(moneyLedgerRepository.findRecentByWalletId(any(UUID.class), any(Pageable.class)))
                .willReturn(List.of());
    }

    private PointWallet pointWalletWith(UUID walletId, long amount) {
        PointWallet pointWallet = PointWallet.create(walletId);
        pointWallet.charge(amount);
        return pointWallet;
    }

    private MoneyWallet moneyWalletWith(UUID walletId, long amount) {
        MoneyWallet moneyWallet = MoneyWallet.create(walletId);
        ReflectionTestUtils.setField(moneyWallet, "amount", amount);
        return moneyWallet;
    }

    private static Answer<Object> returnsArgument() {
        return invocation -> invocation.getArgument(0);
    }
}

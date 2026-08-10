package com.naengsam.quick.domain.payment.service;

import com.naengsam.quick.domain.payment.dto.ExchangeRequest;
import com.naengsam.quick.domain.payment.dto.PointChargeRequest;
import com.naengsam.quick.domain.payment.dto.WalletDto;
import com.naengsam.quick.domain.payment.dto.WalletTransactionDto;
import com.naengsam.quick.domain.payment.entity.Exchange;
import com.naengsam.quick.domain.payment.entity.MoneyLedger;
import com.naengsam.quick.domain.payment.entity.MoneyTx;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.Payment;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 지갑을 만들고, 잔액·내역 조회와 포인트 충전·머니 전환을 처리한다. 지갑은 {@link Wallet} 한 행과 그 wallet_id 를 공유 PK 로 쓰는
 * {@link PointWallet}·{@link MoneyWallet} 로 이루어진다.
 * <p>
 * 주문 결제·환불은 {@link PaymentService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    /**
     * 지갑 화면이 보여주는 최근 내역 건수.
     */
    private static final int RECENT_TRANSACTION_SIZE = 20;

    private final WalletRepository walletRepository;
    private final PointWalletRepository pointWalletRepository;
    private final MoneyWalletRepository moneyWalletRepository;
    private final PointTxRepository pointTxRepository;
    private final MoneyTxRepository moneyTxRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final MoneyLedgerRepository moneyLedgerRepository;
    private final PaymentRepository paymentRepository;
    private final ExchangeRepository exchangeRepository;

    /**
     * 회원가입 시 지갑 세 행을 잔액 0 으로 함께 만든다. FK 때문에 WALLET 을 먼저 저장한 뒤 나머지 둘이 그 wallet_id 를 물고 붙는다.
     */
    @Transactional
    public void createWallet(UUID boormiId) {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));

        pointWalletRepository.save(PointWallet.create(wallet.getWalletId()));
        moneyWalletRepository.save(MoneyWallet.create(wallet.getWalletId()));
    }

    /**
     * 지갑 화면 한 벌(포인트·머니 잔액 + 최근 내역)을 조회한다.
     */
    @Transactional(readOnly = true)
    public WalletDto getWallet(UUID boormiId) {
        return toDto(findWallet(boormiId).getWalletId());
    }

    /**
     * 포인트를 충전한다. 결제 근거(PAYMENT) → 거래(POINT_TX) → 지갑 적립 → 원장 순으로 한 트랜잭션 안에서 처리한다.
     * <p>
     * PG 는 아직 붙지 않아 결제는 항상 성공한 것으로 보고 포인트를 즉시 적립한다.
     */
    @Transactional
    public WalletDto chargePoint(UUID boormiId, PointChargeRequest request) {
        UUID walletId = findWallet(boormiId).getWalletId();
        long amount = request.amount();

        Payment payment = paymentRepository.save(
                Payment.create(boormiId, amount, request.paymentCd()));

        PointTx pointTx = pointTxRepository.save(
                PointTx.create(walletId, PointTxTypeCd.CHARGE, amount, payment.getPaymentId(), null));

        PointWallet pointWallet = lockPointWallet(walletId);
        pointWallet.charge(amount);

        pointLedgerRepository.save(PointLedger.create(walletId, pointTx.getPointTxId(),
                amount, pointWallet.getAmount()));

        return toDto(walletId);
    }

    /**
     * 머니를 포인트로 전환한다(1:1, 수수료 없음). 나가는 쪽 머니 거래와 들어오는 쪽 포인트 거래를 만들고 {@link Exchange} 로 짝지어 남긴다.
     * <p>
     * 두 지갑을 모두 잠그므로 락 순서를 항상 머니 → 포인트로 고정해 다른 트랜잭션과 엇갈린 순서로 잡는 일이 없게 한다.
     */
    @Transactional
    public WalletDto exchangeMoneyToPoint(UUID boormiId, ExchangeRequest request) {
        UUID walletId = findWallet(boormiId).getWalletId();
        long amount = request.amount();

        MoneyWallet moneyWallet = moneyWalletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        moneyWallet.deduct(amount); // 잔액이 모자라면 INSUFFICIENT_MONEY 로 트랜잭션 전체가 롤백된다

        MoneyTx moneyTx = moneyTxRepository.save(
                MoneyTx.createSettled(walletId, MoneyTxTypeCd.EXCHANGE_OUT, amount, null));
        moneyLedgerRepository.save(MoneyLedger.create(walletId, moneyTx.getMoneyTxId(),
                -amount, moneyWallet.getAmount()));

        PointWallet pointWallet = lockPointWallet(walletId);
        pointWallet.charge(amount);

        PointTx pointTx = pointTxRepository.save(
                PointTx.create(walletId, PointTxTypeCd.EXCHANGE_IN, amount, null, null));
        pointLedgerRepository.save(PointLedger.create(walletId, pointTx.getPointTxId(),
                amount, pointWallet.getAmount()));

        exchangeRepository.save(Exchange.create(walletId, pointTx.getPointTxId(),
                moneyTx.getMoneyTxId(), amount));

        return toDto(walletId);
    }

    /**
     * 잔액과 최근 내역을 읽어 응답을 만든다. 두 원장을 각각 최신 20건씩 읽어 시간순으로 합친 뒤 상위 20건만 남긴다.
     */
    private WalletDto toDto(UUID walletId) {
        PointWallet pointWallet = pointWalletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        MoneyWallet moneyWallet = moneyWalletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));

        PageRequest limit = PageRequest.ofSize(RECENT_TRANSACTION_SIZE);
        List<WalletTransactionDto> transactions = new ArrayList<>();
        pointLedgerRepository.findRecentByWalletId(walletId, limit)
                .forEach(row -> transactions.add(WalletTransactionDto.from(row)));
        moneyLedgerRepository.findRecentByWalletId(walletId, limit)
                .forEach(row -> transactions.add(WalletTransactionDto.from(row)));
        transactions.sort(Comparator.comparing(WalletTransactionDto::createdDtm).reversed());

        return new WalletDto(pointWallet.getAmount(), moneyWallet.getAmount(),
                moneyWallet.getPendingAmount(),
                transactions.subList(0, Math.min(transactions.size(), RECENT_TRANSACTION_SIZE)));
    }

    /**
     * 회원의 지갑을 찾는다. 지갑은 회원가입 때 만들어지므로, 지갑이 없는 건 지갑 생성 도입 이전에 가입한 계정뿐이다.
     */
    private Wallet findWallet(UUID boormiId) {
        return walletRepository.findByBoormiId(boormiId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    private PointWallet lockPointWallet(UUID walletId) {
        return pointWalletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
    }
}

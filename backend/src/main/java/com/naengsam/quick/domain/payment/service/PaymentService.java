package com.naengsam.quick.domain.payment.service;

import com.naengsam.quick.domain.payment.entity.PointLedger;
import com.naengsam.quick.domain.payment.entity.PointTx;
import com.naengsam.quick.domain.payment.entity.PointTxTypeCd;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.exception.PaymentErrorCode;
import com.naengsam.quick.domain.payment.repository.PointLedgerRepository;
import com.naengsam.quick.domain.payment.repository.PointTxRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 결제·환불을 처리한다. 거래 1건은 {@link PointTx} 한 행으로 표현하고(환불은 새 행이 아니라 그 행의 상태 전이), 잔액이 실제로 얼마씩 오르내렸는지는 {@link PointLedger}
 * 가 로그로 남긴다. 지갑 반영은 미루지 않고 같은 트랜잭션 안에서 끝낸다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final WalletRepository walletRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTxRepository pointTxRepository;
    private final PointLedgerRepository pointLedgerRepository;

    /**
     * 배달 콜 요금을 포인트로 결제한다. 거래 기록(POINT_TX) → 지갑 차감 → 원장 기록 순으로 한 트랜잭션 안에서 처리한다.
     * <p>
     * 같은 주문으로 결제가 두 번 일어나지 않도록 기존 거래를 먼저 확인하고, 경합으로 검사를 동시에 통과하더라도 {@code UQ_POINT_TX_ORDER_TYPE} 이 두 번째 저장을 막는다.
     */
    @Transactional
    public void payWithPoint(UUID boormiId, UUID orderId, long amount) {
        PointWallet pointWallet = lockPointWallet(boormiId);

        if (pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT).isPresent()) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PAID);
        }

        PointTx pointTx = pointTxRepository.save(
                PointTx.create(pointWallet.getWalletId(), PointTxTypeCd.PAYMENT, amount, null, orderId));

        pointWallet.deduct(amount); // 잔액이 모자라면 INSUFFICIENT_POINT 로 트랜잭션 전체가 롤백된다

        pointLedgerRepository.save(PointLedger.create(pointWallet.getWalletId(),
                pointTx.getPointTxId(), -amount, pointWallet.getAmount()));
    }

    /**
     * 주문 취소에 따라 결제한 포인트를 전액 환불한다. 원본 결제 거래의 상태를 REFUNDED_FULL 로 전이시키고 잔액을 되돌린 뒤 원장에 +금액을 남긴다.
     * <p>
     * 이미 환불된 주문이면 잔액을 건드리지 않고 그대로 끝낸다.
     */
    @Transactional
    public void refundByPoint(UUID orderId) {
        PointTx pointTx = pointTxRepository.findByOrderIdAndType(orderId, PointTxTypeCd.PAYMENT)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!pointTx.markRefundedFull()) {
            return;
        }

        PointWallet pointWallet = pointWalletRepository.findByIdForUpdate(pointTx.getWalletId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));

        pointWallet.refund(pointTx.getAmount());

        pointLedgerRepository.save(PointLedger.create(pointWallet.getWalletId(),
                pointTx.getPointTxId(), pointTx.getAmount(), pointWallet.getAmount()));
    }

    /**
     * 회원의 포인트 지갑을 비관적 쓰기 락으로 조회한다. 지갑은 회원가입 때 만들어지므로, 지갑이 없는 건 지갑 생성 도입 이전에 가입한 계정뿐이다.
     */
    private PointWallet lockPointWallet(UUID boormiId) {
        Wallet wallet = walletRepository.findByBoormiId(boormiId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));

        return pointWalletRepository.findByIdForUpdate(wallet.getWalletId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
    }
}

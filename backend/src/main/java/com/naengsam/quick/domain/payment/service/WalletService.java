package com.naengsam.quick.domain.payment.service;

import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.repository.MoneyWalletRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 지갑을 만든다. 지갑은 {@link Wallet} 한 행과 그 wallet_id 를 공유 PK 로 쓰는 {@link PointWallet}·{@link MoneyWallet} 로 이루어진다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final PointWalletRepository pointWalletRepository;
    private final MoneyWalletRepository moneyWalletRepository;

    /**
     * 회원가입 시 지갑 세 행을 잔액 0 으로 함께 만든다. FK 때문에 WALLET 을 먼저 저장한 뒤 나머지 둘이 그 wallet_id 를 물고 붙는다.
     */
    @Transactional
    public void createWallet(UUID boormiId) {
        Wallet wallet = walletRepository.save(Wallet.create(boormiId));

        pointWalletRepository.save(PointWallet.create(wallet.getWalletId()));
        moneyWalletRepository.save(MoneyWallet.create(wallet.getWalletId()));
    }
}

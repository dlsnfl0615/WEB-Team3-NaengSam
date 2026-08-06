package com.naengsam.quick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.naengsam.quick.domain.payment.entity.MoneyWallet;
import com.naengsam.quick.domain.payment.entity.PointWallet;
import com.naengsam.quick.domain.payment.entity.Wallet;
import com.naengsam.quick.domain.payment.repository.MoneyWalletRepository;
import com.naengsam.quick.domain.payment.repository.PointWalletRepository;
import com.naengsam.quick.domain.payment.repository.WalletRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 지갑 생성 로직 단위 테스트. WALLET 한 행과 그 wallet_id 를 공유 PK 로 쓰는 포인트·머니 지갑이 잔액 0 으로 함께 만들어지는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private MoneyWalletRepository moneyWalletRepository;

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
}

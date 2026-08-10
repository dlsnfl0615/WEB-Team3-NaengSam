package com.naengsam.quick.domain.payment.controller;

import com.naengsam.quick.domain.payment.dto.ExchangeRequest;
import com.naengsam.quick.domain.payment.dto.PointChargeRequest;
import com.naengsam.quick.domain.payment.dto.WalletDto;
import com.naengsam.quick.domain.payment.exception.PaymentErrorCode;
import com.naengsam.quick.domain.payment.service.WalletService;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallet")
@Tag(name = "지갑 컨트롤러", description = "포인트·머니 잔액 조회와 충전·전환 API")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "내 지갑 조회", description = "포인트·머니 잔액과 두 지갑을 합친 최근 거래 내역 20건을 조회한다.")
    @GetMapping
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = PaymentErrorCode.class, codes = {"WALLET_NOT_FOUND"})
    public WalletDto getWallet(@LoginUser UUID boormiId) {
        return walletService.getWallet(boormiId);
    }

    @Operation(summary = "포인트 충전",
            description = "결제 금액만큼 포인트를 적립한다(1원 = 1P). PG 연동 전이라 결제는 항상 성공한 것으로 보고 즉시 적립한다.")
    @PostMapping("/point/charge")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = PaymentErrorCode.class, codes = {"WALLET_NOT_FOUND"})
    public WalletDto chargePoint(@LoginUser UUID boormiId, @Valid @RequestBody PointChargeRequest request) {
        return walletService.chargePoint(boormiId, request);
    }

    @Operation(summary = "머니 → 포인트 전환", description = "드리미 수익(머니)을 포인트로 전환한다. 비율은 1:1 이고 수수료는 없다.")
    @PostMapping("/exchange")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = PaymentErrorCode.class, codes = {"WALLET_NOT_FOUND", "INSUFFICIENT_MONEY"})
    public WalletDto exchangeMoneyToPoint(@LoginUser UUID boormiId, @Valid @RequestBody ExchangeRequest request) {
        return walletService.exchangeMoneyToPoint(boormiId, request);
    }
}

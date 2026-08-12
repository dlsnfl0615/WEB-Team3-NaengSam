package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.notification.dto.PushSubscriptionRequest;
import com.naengsam.quick.global.notification.dto.PushUnsubscribeRequest;
import com.naengsam.quick.global.notification.dto.VapidPublicKeyDto;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.session.PublicApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹푸시 구독 등록·해제와 VAPID 공개키 조회. {@code global.sse.SseController}와 마찬가지로, 특정 도메인에 속하지 않는
 * 횡단 인프라라서 도메인 패키지가 아니라 여기에 둔다.
 */
@Tag(name = "Push", description = "웹푸시 구독 관리")
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushProperties webPushProperties;

    @Operation(summary = "Web Push VAPID 공개키 조회",
            description = "브라우저가 pushManager.subscribe의 applicationServerKey로 쓰는 공개키. "
                    + "공개 상수이므로 로그인 없이 조회할 수 있다. 미설정(푸시 비활성)이면 publicKey는 null이며, "
                    + "이 경우 프론트는 권한 요청 UI를 렌더하지 않는다.")
    @ApiResponse(responseCode = "200", description = "조회 성공(비활성이면 publicKey=null)")
    @GetMapping("/vapid-public-key")
    @PublicApi
    public VapidPublicKeyDto vapidPublicKey() {
        return new VapidPublicKeyDto(
                webPushProperties.hasPublicKey() ? webPushProperties.publicKey() : null);
    }

    @Operation(summary = "웹푸시 구독 등록",
            description = "브라우저 PushSubscription.toJSON() 결과를 그대로 보낸다. 같은 endpoint를 다시 보내면 "
                    + "행을 새로 만들지 않고 소유자와 키를 갱신하는 멱등 연산이라, 앱 포그라운드 복귀마다 재등록해도 안전하다.")
    @ApiResponse(responseCode = "200", description = "등록 또는 갱신 완료")
    @PostMapping("/subscriptions")
    public void subscribe(
            @LoginUser UUID boormiId,
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        pushSubscriptionService.subscribe(boormiId, request, userAgent);
    }

    @Operation(summary = "웹푸시 구독 해제",
            description = "로그아웃 직전에 호출한다(로그아웃 후에는 세션이 없어 호출할 수 없다). 이미 없는 구독이어도 성공이다.")
    @ApiResponse(responseCode = "200", description = "해제 완료")
    @DeleteMapping("/subscriptions")
    public void unsubscribe(@LoginUser UUID boormiId, @Valid @RequestBody PushUnsubscribeRequest request) {
        pushSubscriptionService.unsubscribe(boormiId, request.endpoint());
    }
}

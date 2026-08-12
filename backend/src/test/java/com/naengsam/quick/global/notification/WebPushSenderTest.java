package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.global.notification.dto.PushEnvelope;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * VAPID 초기화와 서비스워커 페이로드 계약을 검증한다. 실제 푸시 서비스로의 전송은 외부 의존이라 다루지 않고,
 * 조용한 미전달로 이어지기 쉬운 두 지점(키 로딩, 봉투 필드명)만 고정한다.
 */
class WebPushSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    /** {@code npx web-push generate-vapid-keys} 가 만드는 것과 같은 형식(P-256, base64url)의 키 쌍. */
    private static String[] generateVapidKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(Utils.ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec(Utils.CURVE));
        KeyPair keyPair = generator.generateKeyPair();

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new String[]{
                encoder.encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic())),
                encoder.encodeToString(Utils.encode((ECPrivateKey) keyPair.getPrivate()))
        };
    }

    private static WebPushSender senderWith(String publicKey, String privateKey) {
        WebPushProperties properties = new WebPushProperties(
                true, publicKey, privateKey, "mailto:noreply@symboorm.com");
        return new WebPushSender(properties, new ObjectMapper());
    }

    @Test
    void 올바른_VAPID_키_쌍이면_초기화에_성공한다() throws Exception {
        String[] keyPair = generateVapidKeyPair();

        WebPushSender sender = senderWith(keyPair[0], keyPair[1]);

        assertThatCode(sender::init).doesNotThrowAnyException();
    }

    @Test
    void VAPID_키가_잘못되면_기동_시점에_실패한다() {
        WebPushSender sender = senderWith("올바르지-않은-키", "올바르지-않은-키");

        Throwable thrown = catchThrowable(sender::init);

        // 조용히 모든 전송이 실패하는 것보다 기동을 실패시켜 배포에서 잡는 편이 낫다.
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 봉투_JSON은_서비스워커가_읽는_필드명을_유지한다() {
        PushEnvelope envelope = new PushEnvelope("새 배달 요청이 왔어요", "앱을 열어 확인해주세요", "/", "offer_popup");

        String json = objectMapper.writeValueAsString(envelope);

        // frontend/src/sw.ts 의 PushEnvelope 인터페이스와 같은 이름이어야 한다.
        assertThat(json)
                .contains("\"title\":\"새 배달 요청이 왔어요\"")
                .contains("\"body\":\"앱을 열어 확인해주세요\"")
                .contains("\"url\":\"/\"")
                .contains("\"tag\":\"offer_popup\"");
    }
}

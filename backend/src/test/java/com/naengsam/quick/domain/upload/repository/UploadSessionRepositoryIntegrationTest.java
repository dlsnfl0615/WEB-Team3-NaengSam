package com.naengsam.quick.domain.upload.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.entity.UploadSession;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * markConsumedIfIssued 가 같은 트랜잭션의 다른(이미 로드된) 엔티티를 영속성 컨텍스트에서 detach시키지 않는지 검증한다.
 * clearAutomatically를 켜면 이 쿼리와 무관한 Delivery까지 detach돼, 이후 그 엔티티에 가한 변경(dirty checking)이
 * 조용히 사라진다(예외 없음) — 픽업 완료 처리가 반영되지 않는 실제 버그의 재현.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:upload-session-detach;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:./sql/sym-boorm-ddl.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        "solapi.enabled=false"
})
class UploadSessionRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UploadSessionRepository uploadSessionRepository;
    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    void 세션소비_쿼리_이후에도_같은_트랜잭션에서_먼저_로드한_Delivery의_변경이_반영된다() {
        Boormi boormi = boormi("sender@test.com", "부르미", "01011112222");
        entityManager.persist(boormi);
        UUID boormiId = boormi.getBoormiId();

        Boormi dreamiAccount = boormi("dreami@test.com", "드리미", "01033334444");
        entityManager.persist(dreamiAccount);
        UUID dreamiId = dreamiAccount.getBoormiId();
        entityManager.persist(Dreami.create(dreamiId, "id-card-key", "criminal-record-key"));

        Orders order = Orders.create(UUID.randomUUID(), boormiId, "서류봉투", ItemCd.DOCUMENT, null,
                5_000L, 30, null, null, addresses());
        entityManager.persist(order);

        Delivery delivery = Delivery.create(order.getOrderId(), dreamiId, boormiId);
        entityManager.persist(delivery);

        String s3Key = "uploads/PICKUP_CERTIFICATION_IMAGE/test.png";
        entityManager.persist(
                UploadSession.create(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, order.getOrderId(), s3Key));
        entityManager.flush();

        // 같은 트랜잭션 안에서: Delivery를 먼저 로드해 관리 상태로 만들어 둔 뒤, 세션 소비 쿼리를 실행한다
        // (DeliveryService.doPickupFinishByDreami가 delivery를 먼저 들고 있다가 checkUpload를 부르는 순서와 동일).
        Delivery loaded = deliveryRepository.findByOrderIdWithoutLock(order.getOrderId()).orElseThrow();
        int updated = uploadSessionRepository.markConsumedIfIssued(s3Key);
        assertThat(updated).isEqualTo(1);

        // 세션 소비 쿼리 이후에도 loaded는 여전히 관리 상태여야 하고, 이 변경이 dirty checking으로 flush돼야 한다.
        loaded.markDelivering();
        entityManager.flush();
        entityManager.clear();

        Delivery reloaded = deliveryRepository.findByOrderIdWithoutLock(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getDeliveryCd()).isEqualTo(DeliveryCd.DELIVERING);
    }

    private static Boormi boormi(String email, String name, String phoneNumber) {
        return Boormi.create(email, "password", name, phoneNumber, LocalDate.of(1995, 1, 1));
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

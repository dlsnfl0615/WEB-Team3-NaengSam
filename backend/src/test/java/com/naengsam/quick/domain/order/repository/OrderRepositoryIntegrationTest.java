package com.naengsam.quick.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.order.dto.NearbyCallOrderDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 주변 콜 목록의 주문 일괄 조회 쿼리를 실제 스키마에 대고 검증한다. 프로젝션 생성자 인자는 출발/도착 주소처럼 같은 String 타입이 이어져 있어 순서가 뒤바뀌어도 컴파일과 기동이
 * 모두 통과하므로, 컬럼이 제자리에 담기는지는 실제 행을 왕복해야만 확인할 수 있다. BINARY(16) UUID에 대한 {@code IN} 바인딩도 함께 확인한다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-nearby-call;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:./sql/sym-boorm-ddl.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        "solapi.enabled=false"
})
class OrderRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void 주변콜_주문조회는_요청한_id의_주문을_품목_금액_주소까지_담아_돌려준다() {
        UUID boormiId = persistBoormi("sender@test.com", "부르미", "01011112222");
        UUID documentOrderId = persistOrder(boormiId, "서류봉투", ItemCd.DOCUMENT, 3_500L, 15);
        UUID sampleOrderId = persistOrder(boormiId, "샘플박스", ItemCd.SAMPLE, 7_000L, 25);
        entityManager.flush();
        entityManager.clear();

        List<NearbyCallOrderDto> orders =
                orderRepository.findNearbyCallOrders(List.of(documentOrderId, sampleOrderId));

        assertThat(orders).extracting(NearbyCallOrderDto::orderId, NearbyCallOrderDto::itemName,
                        NearbyCallOrderDto::itemCd, NearbyCallOrderDto::orderCd,
                        NearbyCallOrderDto::deliveryAmount, NearbyCallOrderDto::deliveryEta)
                .containsExactlyInAnyOrder(
                        tuple(documentOrderId, "서류봉투", ItemCd.DOCUMENT, OrderCd.MATCHING, 3_500L, 15),
                        tuple(sampleOrderId, "샘플박스", ItemCd.SAMPLE, OrderCd.MATCHING, 7_000L, 25));

        // 출발/도착 주소는 line1·line2가 모두 String이라 슬롯이 뒤바뀌어도 컴파일된다. 값으로 자리를 고정한다.
        NearbyCallOrderDto document = orders.stream()
                .filter(order -> order.orderId().equals(documentOrderId))
                .findFirst()
                .orElseThrow();
        assertThat(document.originAddressLine1()).isEqualTo("서울시 강남구");
        assertThat(document.originAddressLine2()).isEqualTo("101호");
        assertThat(document.destinationAddressLine1()).isEqualTo("서울시 송파구");
        assertThat(document.destinationAddressLine2()).isEqualTo("202호");
    }

    @Test
    void 존재하지_않는_주문_id가_섞여도_있는_것만_돌려준다() {
        UUID boormiId = persistBoormi("sender@test.com", "부르미", "01011112222");
        UUID liveOrderId = persistOrder(boormiId, "서류봉투", ItemCd.DOCUMENT, 3_500L, 15);
        entityManager.flush();
        entityManager.clear();

        List<NearbyCallOrderDto> orders =
                orderRepository.findNearbyCallOrders(List.of(liveOrderId, UUID.randomUUID()));

        assertThat(orders).extracting(NearbyCallOrderDto::orderId).containsExactly(liveOrderId);
    }

    private UUID persistOrder(UUID boormiId, String itemName, ItemCd itemCd, long amount, int eta) {
        Orders order = Orders.create(UUID.randomUUID(), boormiId, itemName, itemCd, null,
                amount, eta, 4_000L, null, null, addresses(), null);
        entityManager.persist(order);
        return order.getOrderId();
    }

    private UUID persistBoormi(String email, String name, String phoneNumber) {
        Boormi boormi = Boormi.create(email, "password", name, phoneNumber, LocalDate.of(1995, 1, 1));
        entityManager.persist(boormi);
        return boormi.getBoormiId();
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

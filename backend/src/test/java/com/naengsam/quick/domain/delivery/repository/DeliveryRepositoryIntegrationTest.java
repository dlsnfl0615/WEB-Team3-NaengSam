package com.naengsam.quick.domain.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.dto.MonthlySavingAggregate;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.order.entity.Orders;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 부르미 대시보드의 월별 절감액 집계 쿼리를 실제 스키마에 대고 검증한다. Delivery와 Orders는 연관관계 매핑 없이 order_id 세타 조인으로 붙으므로,
 * 조인·월 그룹핑·기간 경계는 단위 테스트로는 확인할 수 없다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery-monthly-saving;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:./sql/sym-boorm-ddl.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        "solapi.enabled=false"
})
class DeliveryRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    void 월별_집계는_완료된_배달만_월단위로_묶고_결제액을_합산한다() {
        UUID boormiId = persistAccount("sender@test.com", "부르미", "01011112222");
        UUID otherBoormiId = persistAccount("other@test.com", "다른부르미", "01055556666");
        UUID dreamiId = persistDreami("dreami@test.com", "드리미", "01033334444");

        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);

        persistDelivered(boormiId, dreamiId, 3_000L, thisMonth.atDay(2).atTime(10, 0));
        persistDelivered(boormiId, dreamiId, 4_000L, thisMonth.atDay(9).atTime(18, 30));
        persistDelivered(boormiId, dreamiId, 5_000L, lastMonth.atDay(20).atTime(9, 0));
        // 집계에서 빠져야 하는 것들: 아직 배달 중 / 다른 부르미의 완료 배달
        persistDelivering(boormiId, dreamiId, 9_000L);
        persistDelivered(otherBoormiId, dreamiId, 8_000L, thisMonth.atDay(3).atTime(11, 0));
        entityManager.flush();
        entityManager.clear();

        List<MonthlySavingAggregate> aggregates = deliveryRepository.aggregateSavingByBoormiBetween(boormiId,
                thisMonth.minusMonths(5).atDay(1).atStartOfDay(),
                thisMonth.plusMonths(1).atDay(1).atStartOfDay());

        assertThat(aggregates).extracting(MonthlySavingAggregate::yearMonth,
                        MonthlySavingAggregate::count, MonthlySavingAggregate::paidAmount)
                .containsExactlyInAnyOrder(
                        tuple(thisMonth, 2L, 7_000L),
                        tuple(lastMonth, 1L, 5_000L));
    }

    @Test
    void 조회_기간_밖의_배달은_집계되지_않는다() {
        UUID boormiId = persistAccount("sender@test.com", "부르미", "01011112222");
        UUID dreamiId = persistDreami("dreami@test.com", "드리미", "01033334444");

        YearMonth thisMonth = YearMonth.now();
        persistDelivered(boormiId, dreamiId, 6_000L, thisMonth.minusMonths(6).atDay(15).atTime(12, 0));
        entityManager.flush();
        entityManager.clear();

        List<MonthlySavingAggregate> aggregates = deliveryRepository.aggregateSavingByBoormiBetween(boormiId,
                thisMonth.minusMonths(5).atDay(1).atStartOfDay(),
                thisMonth.plusMonths(1).atDay(1).atStartOfDay());

        assertThat(aggregates).isEmpty();
    }

    private void persistDelivered(UUID boormiId, UUID dreamiId, long amount, LocalDateTime deliveryEndDtm) {
        Delivery delivery = persistDelivering(boormiId, dreamiId, amount);
        delivery.markDelivered();
        ReflectionTestUtils.setField(delivery, "deliveryEndDtm", deliveryEndDtm);
    }

    private Delivery persistDelivering(UUID boormiId, UUID dreamiId, long amount) {
        Orders order = Orders.create(UUID.randomUUID(), boormiId, "서류봉투", ItemCd.DOCUMENT, null,
                amount, 30, null, null, null, addresses(), null);
        entityManager.persist(order);

        Delivery delivery = Delivery.create(order.getOrderId(), dreamiId, boormiId);
        entityManager.persist(delivery);
        return delivery;
    }

    private UUID persistAccount(String email, String name, String phoneNumber) {
        Boormi boormi = Boormi.create(email, "password", name, phoneNumber, LocalDate.of(1995, 1, 1));
        entityManager.persist(boormi);
        return boormi.getBoormiId();
    }

    private UUID persistDreami(String email, String name, String phoneNumber) {
        UUID dreamiId = persistAccount(email, name, phoneNumber);
        entityManager.persist(Dreami.create(dreamiId, "id-card-key", "criminal-record-key"));
        return dreamiId;
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

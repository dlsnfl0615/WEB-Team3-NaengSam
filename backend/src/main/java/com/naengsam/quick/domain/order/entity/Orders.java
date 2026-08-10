package com.naengsam.quick.domain.order.entity;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ORDERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dreami_id", columnDefinition = "BINARY(16)")
    private UUID dreamiId;

    @Column(name = "item_name", length = 50, nullable = false)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_cd", nullable = false)
    private ItemCd itemCd;

    @Column(name = "item_detail")
    private String itemDetail;

    @Column(name = "delivery_amount")
    private Long deliveryAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_cd", nullable = false)
    private OrderCd orderCd;

    @Column(name = "delivery_eta", nullable = false)
    private int deliveryEta;

    @Column(name = "delivery_distance")
    private Long deliveryDistance; // 출발지-도착지 예상 거리(m)

    @Column(name = "origin_latitude", precision = 11, scale = 8)
    private BigDecimal originLatitude;
    @Column(name = "origin_longitude", precision = 11, scale = 8)
    private BigDecimal originLongitude;
    @Column(name = "origin_alias", length = 50)
    private String originAlias;
    @Column(name = "origin_address_line_1")
    private String originAddressLine1; // 기본주소
    @Column(name = "origin_address_line_2")
    private String originAddressLine2; // 상세주소

    @Column(name = "destination_latitude", precision = 11, scale = 8)
    private BigDecimal destinationLatitude;
    @Column(name = "destination_longitude", precision = 11, scale = 8)
    private BigDecimal destinationLongitude;
    @Column(name = "destination_alias", length = 50)
    private String destinationAlias;
    @Column(name = "destination_address_line_1")
    private String destinationAddressLine1;
    @Column(name = "destination_address_line_2")
    private String destinationAddressLine2;

    @Column(name = "delivery_request")
    private String deliveryRequest;

    @Column(name = "route_path", columnDefinition = "TEXT")
    private String routePath; // 카카오 추천 이동경로 좌표 JSON([{latitude, longitude}, ...]) — 추적 지도 폴리라인용

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "delivery_request_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime deliveryRequestDtm;

    /**
     * 주문 접수 시 부르미 요청 정보로 신규 주문을 생성한다. PK 는 앱에서 생성(BINARY(16))하며 상태는 MATCHING 으로 시작한다. {@code dreami_id} 는 매칭 성사 시 채워지고,
     * {@code delivery_request_dtm} 은 DB 기본값(CURRENT_TIMESTAMP)이 적용된다.
     */
    public static Orders create(UUID orderId, UUID boormiId, String itemName, ItemCd itemCd,
                                String itemDetail, Long deliveryAmount, int deliveryEta, Long deliveryDistance,
                                String deliveryRequest, String imageKey, Addresses addresses, String routePath) {
        Orders order = new Orders();
        order.orderId = orderId;
        order.boormiId = boormiId;
        order.itemName = itemName;
        order.itemCd = itemCd;
        order.itemDetail = itemDetail;
        order.deliveryAmount = deliveryAmount;
        order.deliveryEta = deliveryEta;
        order.deliveryDistance = deliveryDistance;
        order.deliveryRequest = deliveryRequest;
        order.imageKey = imageKey;
        order.routePath = routePath;
        order.orderCd = OrderCd.MATCHING;
        order.updateAddresses(addresses);
        return order;
    }

    /**
     * Matching Service 의 기존 객체 compatibility 를 위한 임시 생성자. 좌표만으로 매칭 대상 주문을 만든다(영속화하지 않음).
     */
    public static Orders create(UUID orderId, UUID boormiId,
            GeoPoint origin, GeoPoint destination) {
        Orders order = new Orders();
        order.orderId = orderId;
        order.boormiId = boormiId;
        order.destinationLatitude = destination.latitude();
        order.destinationLongitude = destination.longitude();
        order.originLatitude = origin.latitude();
        order.originLongitude = origin.longitude();
        order.orderCd = OrderCd.MATCHING;
        return order;
    }

    /**
     * 주문을 취소 상태로 전이한다.
     */
    public void cancel() {
        this.orderCd = OrderCd.CANCELLED;
    }

    /**
     * 배달이 완료되어 주문을 완료 상태로 전이한다.
     */
    public void complete() {
        this.orderCd = OrderCd.COMPLETED;
    }

    /**
     * 부르미가 드리미를 최종 확정한다. 확정된 드리미로 dreami_id 를 채우고 IN_PROGRESS 로 전이한다(dirty checking 반영). 검증은 서비스에서 수행한다.
     */
    public void confirmDreami(UUID dreamiId) {
        this.dreamiId = dreamiId;
        this.orderCd = OrderCd.IN_PROGRESS;
    }

    /**
     * 드리미가 제안을 수락해 부르미의 최종 확인을 기다리는 상태로 전이한다. 검증은 서비스에서 수행한다.
     */
    public void markPendingBoormiConfirmation() {
        this.orderCd = OrderCd.PENDING_BOORMI_CONFIRMATION;
    }

    /**
     * 매칭이 확정된 주문에 드리미를 배정하고 진행 중 상태로 전이한다. !! delivery에서 테스트 용도로 만든 것. 추후에 매칭 기능 완성되면 삭제하겠습니다.
     */
    public void assignDreamiTest(UUID dreamiId) {
        this.dreamiId = dreamiId;
        this.orderCd = OrderCd.IN_PROGRESS;
    }

    /**
     * 부르미가 확정 대기 중인 드리미를 거절한다. 배정을 비우고 다시 매칭 대기(MATCHING)로 되돌린다(dirty checking 반영). 검증은 서비스에서 수행한다.
     */
    public void rejectDreami() {
        this.dreamiId = null;
        this.orderCd = OrderCd.MATCHING;
    }

    public void updateAddresses(Addresses addresses) {
        this.originAddressLine1 = addresses.originAddressLine1();
        this.originAddressLine2 = addresses.originAddressLine2();
        this.originLatitude = addresses.originLatitude();
        this.originLongitude = addresses.originLongitude();
        this.originAlias = addresses.originAlias();

        this.destinationAddressLine1 = addresses.destinationAddressLine1();
        this.destinationAddressLine2 = addresses.destinationAddressLine2();
        this.destinationLatitude = addresses.destinationLatitude();
        this.destinationLongitude = addresses.destinationLongitude();
        this.destinationAlias = addresses.destinationAlias();
    }
}

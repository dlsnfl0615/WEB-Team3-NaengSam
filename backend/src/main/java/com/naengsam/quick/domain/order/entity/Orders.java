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

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "delivery_request_dtm", nullable = false, insertable = false, updatable = false)
    private LocalDateTime deliveryRequestDtm;

    /**
     * 주문 접수 시 부르미 요청 정보로 신규 주문을 생성한다. PK 는 앱에서 생성(BINARY(16))하며 상태는 MATCHING 으로 시작한다.
     * {@code dreami_id} 는 매칭 성사 시 채워지고, {@code delivery_request_dtm} 은 DB 기본값(CURRENT_TIMESTAMP)이 적용된다.
     */
    public static Orders create(UUID orderId, UUID boormiId, String itemName, ItemCd itemCd,
            String itemDetail, Long deliveryAmount, int deliveryEta, String deliveryRequest,
            String imageUrl, Addresses addresses) {
        Orders order = new Orders();
        order.orderId = orderId;
        order.boormiId = boormiId;
        order.itemName = itemName;
        order.itemCd = itemCd;
        order.itemDetail = itemDetail;
        order.deliveryAmount = deliveryAmount;
        order.deliveryEta = deliveryEta;
        order.deliveryRequest = deliveryRequest;
        order.imageUrl = imageUrl;
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
     * 매칭 대기 중인 주문을 취소 상태로 전이한다.
     */
    public void cancel() {
        this.orderCd = OrderCd.CANCELLED;
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

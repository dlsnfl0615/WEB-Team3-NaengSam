package com.naengsam.quick.domain.order.entity;

import com.naengsam.quick.domain.address.dto.Addresses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Orders {
    @Id
    private UUID orderId;

    @Column(name = "boormi_id")
    private UUID boormiId;

    @Column(name = "origin_address_line_1")
    private String originAddressLine1; // 기본주소
    @Column(name = "origin_address_line_2")
    private String originAddressLine2; // 상세주소
    @Column(name = "origin_latitude", precision = 11, scale = 8)
    private BigDecimal originLatitude;
    @Column(name = "origin_longitude", precision = 11, scale = 8)
    private BigDecimal originLongitude;
    @Column(name = "origin_alias")
    private String originAlias;

    @Column(name = "destination_address_line_1")
    private String destinationAddressLine1;
    @Column(name = "destination_address_line_2")
    private String destinationAddressLine2;
    @Column(name = "destination_latitude", precision = 11, scale = 8)
    private BigDecimal destinationLatitude;
    @Column(name = "destination_longitude", precision = 11, scale = 8)
    private BigDecimal destinationLongitude;
    @Column(name = "destination_alias")
    private String destinationAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_cd")
    private OrderCd orderCd;

    /**
     * 매칭 시작 등 아직 영속화 흐름이 없는 곳에서 임시로 필요한 최소 정보만 채운 인스턴스를 만든다. DB에 저장되지 않는다.
     */
    public static Orders create(UUID orderId, UUID boormiId,
                                BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        Orders order = new Orders();
        order.orderId = orderId;
        order.boormiId = boormiId;
        order.destinationLatitude = destinationLatitude;
        order.destinationLongitude = destinationLongitude;
        return order;
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

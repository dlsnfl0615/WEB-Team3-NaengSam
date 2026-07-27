package com.naengsam.quick.domain.order.entity;

import com.naengsam.quick.domain.address.dto.Addresses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class Orders {
    @Id
    private UUID orderId;

    @Column(name = "origin_address_line1")
    private String originAddressLine1; // 기본주소
    @Column(name = "origin_address_line2")
    private String originAddressLine2; // 상세주소
    @Column(name = "origin_latitude")
    private String originLatitude;
    @Column(name = "origin_longitude")
    private String originLongitude;
    @Column(name = "origin_alias")
    private String originAlias;

    @Column(name = "destination_address_line1")
    private String destinationAddressLine1;
    @Column(name = "destination_address_line2")
    private String destinationAddressLine2;
    @Column(name = "destination_latitude")
    private String destinationLatitude;
    @Column(name = "destination_longitude")
    private String destinationLongitude;
    @Column(name = "destination_alias")
    private String destinationAlias;

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

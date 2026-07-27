package com.naengsam.quick.domain.order.entity;

import com.naengsam.quick.domain.dreami.entity.Addresses;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Orders {
    @Id
    private String orderId;

    private String originAddressLine1; // 기본주소
    private String originAddressLine2; // 상세주소
    private String originLatitude;
    private String originLongitude;
    private String originAlias;

    private String destinationAddressLine1;
    private String destinationAddressLine2;
    private String destinationLatitude;
    private String destinationLongitude;
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

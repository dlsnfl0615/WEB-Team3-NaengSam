package com.naengsam.quick.domain.address.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 부르미가 등록해 둔 배송지 엔티티. 좌표는 저장 시점에 {@code CoordinatesService} 로 도로명주소를 변환해 채운다.
 */
@Entity
@Table(name = "ADDRESS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "address_id", columnDefinition = "BINARY(16)")
    private UUID addressId;

    @Column(name = "address_alias", length = 50)
    private String addressAlias;

    @Column(name = "latitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;

    @Column(name = "address_line_2", length = 255, nullable = false)
    private String addressLine2; // 상세 주소

    @Column(name = "address_line_1", length = 255, nullable = false)
    private String addressLine1; // 기본 주소

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;
}

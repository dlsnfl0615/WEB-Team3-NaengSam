package com.naengsam.quick.domain.delivery.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 배달 에러코드의 코드값과 HTTP 상태 매핑을 검증한다.
 */
class DeliveryErrorCodeTest {
    @Test
    void 배달_에러코드는_정의된_코드와_HTTP상태를_사용한다() {
        assertThat(DeliveryErrorCode.values())
                .extracting(DeliveryErrorCode::getCode, DeliveryErrorCode::getStatus)
                .containsExactly(
                        tuple("DELIVERY_001", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_003", HttpStatus.BAD_REQUEST),
                        tuple("DELIVERY_004", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_005", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_006", HttpStatus.CONFLICT),
                        tuple("DELIVERY_007", HttpStatus.CONFLICT),
                        tuple("DELIVERY_008", HttpStatus.CONFLICT),
                        tuple("DELIVERY_009", HttpStatus.CONFLICT),
                        tuple("DELIVERY_010", HttpStatus.NOT_FOUND),
                        tuple("DELIVERY_012", HttpStatus.CONFLICT),
                        tuple("DELIVERY_013", HttpStatus.CONFLICT),
                        tuple("DELIVERY_014", HttpStatus.CONFLICT),
                        tuple("DELIVERY_015", HttpStatus.INTERNAL_SERVER_ERROR),
                        tuple("DELIVERY_016", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_017", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_018", HttpStatus.CONFLICT),
                        tuple("DELIVERY_019", HttpStatus.CONFLICT),
                        tuple("DELIVERY_020", HttpStatus.CONFLICT),
                        tuple("DELIVERY_021", HttpStatus.CONFLICT),
                        tuple("DELIVERY_022", HttpStatus.CONFLICT),
                        tuple("DELIVERY_023", HttpStatus.CONFLICT),
                        tuple("DELIVERY_024", HttpStatus.CONFLICT),
                        tuple("DELIVERY_025", HttpStatus.CONFLICT),
                        tuple("DELIVERY_026", HttpStatus.CONFLICT),
                        tuple("DELIVERY_027", HttpStatus.CONFLICT),
                        tuple("DELIVERY_028", HttpStatus.CONFLICT),
                        tuple("DELIVERY_029", HttpStatus.CONFLICT),
                        tuple("DELIVERY_030", HttpStatus.CONFLICT),
                        tuple("DELIVERY_031", HttpStatus.FORBIDDEN),
                        tuple("DELIVERY_032", HttpStatus.CONFLICT));
    }
}

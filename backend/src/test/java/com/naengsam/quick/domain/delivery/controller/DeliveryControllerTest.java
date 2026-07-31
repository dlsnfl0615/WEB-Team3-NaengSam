package com.naengsam.quick.domain.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naengsam.quick.domain.delivery.dto.DeliveryLocationDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 배달 컨트롤러가 비즈니스 예외를 HTTP 상태와 공통 응답 코드로 변환하는지 검증한다.
 */
class DeliveryControllerTest {
    private DeliveryService deliveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deliveryService = mock(DeliveryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(deliveryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 위치정보의_JSON숫자를_BigDecimal로_서비스에_전달한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        DeliveryStatusResponseDto response = new DeliveryStatusResponseDto(
                orderId,
                DeliveryCd.PICKUP_NORMAL,
                new DeliveryLocationDto(new BigDecimal("37.12345679"), new BigDecimal("127.10000000")),
                "위치 갱신됨");
        when(deliveryService.updateDreamiLocation(eq(orderId), any(DreamiLocationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/dreami-location", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.123456789, "longitude": 127.1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLocation.latitude").value(37.12345679))
                .andExpect(jsonPath("$.currentLocation.longitude").value(127.1));

        ArgumentCaptor<DreamiLocationRequest> locationCaptor =
                ArgumentCaptor.forClass(DreamiLocationRequest.class);
        verify(deliveryService).updateDreamiLocation(eq(orderId), locationCaptor.capture());
        assertThat(locationCaptor.getValue().latitude())
                .isEqualByComparingTo(new BigDecimal("37.123456789"));
        assertThat(locationCaptor.getValue().longitude())
                .isEqualByComparingTo(new BigDecimal("127.1"));
    }

    @Test
    void 위치정보가_누락되면_400과_DELIVERY_003을_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new BusinessException(DeliveryErrorCode.LOCATION_COLLECTION_FAILED))
                .when(deliveryService).updateDreamiLocation(orderId, null);

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/dreami-location", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("DELIVERY_003"));
    }

    @Test
    void 픽업완료전_배달완료를_시도하면_409와_DELIVERY_014를_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED))
                .when(deliveryService).finishDelivery(orderId);

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/finish", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("DELIVERY_014"))
                .andExpect(jsonPath("$.message").value("픽업 완료 후 진행해 주세요."));
    }
}

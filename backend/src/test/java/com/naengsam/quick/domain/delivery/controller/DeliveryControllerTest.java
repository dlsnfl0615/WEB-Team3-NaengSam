package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.exception.GlobalExceptionHandler;
import com.naengsam.quick.global.session.LoginUserArgumentResolver;
import com.naengsam.quick.global.session.SessionConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
                .setCustomArgumentResolvers(new LoginUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 위치정보의_JSON숫자를_BigDecimal로_서비스에_전달하고_ack를_응답한다() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/dreami-location", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.123456789, "longitude": 127.1}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

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
        UUID dreamiId = UUID.randomUUID();
        doThrow(new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED))
                .when(deliveryService).finishDelivery(eq(orderId), eq(dreamiId), any());

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/finish", orderId)
                        .sessionAttr(SessionConst.LOGIN_USER, dreamiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoKey": "uploads/dreami/finish.png"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("DELIVERY_014"))
                .andExpect(jsonPath("$.message").value("픽업 완료 처리에 실패했습니다."));
    }

    @Test
    void 픽업완료_로그인_드리미와_사진key를_서비스에_전달한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        String photoKey = "uploads/dreami/pickup.png";

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/pickup-finish", orderId)
                        .sessionAttr(SessionConst.LOGIN_USER, dreamiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoKey": "%s"}
                                """.formatted(photoKey)))
                .andExpect(status().isOk());

        verify(deliveryService).pickupFinishByDreami(orderId, dreamiId, photoKey);
    }

    @Test
    void 픽업완료_사진이_없으면_400과_DELIVERY_002를_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        doThrow(new BusinessException(DeliveryErrorCode.PICKUP_PHOTO_MISSING))
                .when(deliveryService).pickupFinishByDreami(eq(orderId), eq(dreamiId), any());

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/pickup-finish", orderId)
                        .sessionAttr(SessionConst.LOGIN_USER, dreamiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoKey": "uploads/dreami/pickup.png"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("DELIVERY_002"));
    }

    @Test
    void 픽업완료_남의_key를_제출하면_403과_FILE_007을_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        doThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .when(deliveryService).pickupFinishByDreami(eq(orderId), eq(dreamiId), any());

        mockMvc.perform(post("/api/v1/delivery/orders/{orderId}/pickup-finish", orderId)
                        .sessionAttr(SessionConst.LOGIN_USER, dreamiId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoKey": "uploads/other/pickup.png"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("FILE_007"));
    }
}

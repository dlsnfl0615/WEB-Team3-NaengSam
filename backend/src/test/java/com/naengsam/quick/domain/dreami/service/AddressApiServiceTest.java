package com.naengsam.quick.domain.dreami.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.service.AddressApiService;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressApiServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AddressApiService addressApiService;

    @Test
    void 존재하는_주문을_조회하면_해당_행을_반환한다() {
        UUID orderId = UUID.randomUUID();
        Orders order = Orders.create(orderId, null, null, null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Orders result = addressApiService.findById(orderId);

        assertThat(result).isSameAs(order);
    }

    @Test
    void 존재하지_않는_주문을_조회하면_예외를_던진다() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> addressApiService.findById(orderId));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    void 주소를_수정하면_조회한_주문에_반영된다() {
        UUID orderId = UUID.randomUUID();
        Orders order = mock(Orders.class);
        Addresses addresses = Addresses.builder()
                .originAddressLine1("서울시 강남구")
                .destinationAddressLine1("서울시 종로구")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        addressApiService.updateAddresses(orderId, addresses);

        verify(order).updateAddresses(addresses);
    }

    @Test
    void 존재하지_않는_주문의_주소를_수정하면_예외를_던진다() {
        UUID orderId = UUID.randomUUID();
        Addresses addresses = Addresses.builder().build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> addressApiService.updateAddresses(orderId, addresses));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}

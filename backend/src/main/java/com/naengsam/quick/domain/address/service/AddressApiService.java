package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressApiService {

    private final OrderRepository orderRepository;

    /**
     * 주문에 배송지(출발지/도착지) 정보를 반영한다.
     */
    @Transactional
    public void updateAddresses(UUID orderId, Addresses addresses) {
        Orders order = findById(orderId);
        order.updateAddresses(addresses);
    }

    /**
     * 식별자로 주문을 조회한다. 존재하지 않으면 예외를 던진다.
     */
    public Orders findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR));
    }
}

package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressApiService {

    private final OrderRepository orderRepository;

    @Transactional
    public void updateAddresses(UUID orderId, Addresses addresses) {
        Orders order = findById(orderId);
        order.updateAddresses(addresses);
    }

    public Orders findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않음. id=" + orderId));
    }
}

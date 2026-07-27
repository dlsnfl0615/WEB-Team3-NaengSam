package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.repository.AddressRepository;
import com.naengsam.quick.domain.order.entity.Orders;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional
    public void updateAddresses(UUID orderId, Addresses addresses) {
        Orders order = findById(orderId);
        order.updateAddresses(addresses);
    }

    public Orders findById(UUID orderId) {
        return addressRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않음. id=" + orderId));
    }
}

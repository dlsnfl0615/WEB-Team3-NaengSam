package com.naengsam.quick.domain.address.service;

import com.naengsam.quick.domain.address.dto.AddressRequestDto;
import com.naengsam.quick.domain.address.dto.AddressResponseDto;
import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.entity.Address;
import com.naengsam.quick.domain.address.repository.AddressRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final CoordinatesService coordinatesService;

    public UUID saveAddress(AddressRequestDto requestDto) {
        CoordinatesResponseDto coordinates = coordinatesService.getCoordinates(requestDto.addressLine1());

        Address address = Address.builder()
                .addressId(UUID.randomUUID())
                .addressAlias(requestDto.addressAlias())
                .latitude(new BigDecimal(coordinates.documents().getFirst().roadAddress().y()))
                .longitude(new BigDecimal(coordinates.documents().getFirst().roadAddress().x()))
                .addressLine1(requestDto.addressLine1())
                .addressLine2(requestDto.addressLine2())
                .boormiId(requestDto.boormiId())
                .build();

        return addressRepository.save(address).getAddressId();
    }

    public List<AddressResponseDto> findAll() {
        return addressRepository.findAll().stream()
                .map(address -> new AddressResponseDto(
                        address.getAddressAlias(),
                        address.getAddressLine1(),
                        address.getAddressLine2(),
                        address.getBoormiId().toString()
                ))
                .toList();
    }
}

package com.naengsam.quick.domain.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.naengsam.quick.domain.address.dto.AddressRequestDto;
import com.naengsam.quick.domain.address.dto.AddressResponseDto;
import com.naengsam.quick.domain.address.entity.Address;
import com.naengsam.quick.domain.address.repository.AddressRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    @Test
    void 주소를_저장하면_요청값이_반영된_주소가_저장되고_생성된_id가_반환된다() {
        AddressRequestDto requestDto = new AddressRequestDto(
                "우리집",
                "37.123456",
                "127.123456",
                "서울시 강남구",
                "101동 202호",
                UUID.randomUUID()
        );
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID savedId = addressService.saveAddress(requestDto);

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        org.mockito.Mockito.verify(addressRepository).save(captor.capture());
        Address savedAddress = captor.getValue();

        assertThat(savedId).isEqualTo(savedAddress.getAddressId());
        assertThat(savedAddress.getAddressAlias()).isEqualTo(requestDto.addressAlias());
        assertThat(savedAddress.getLatitude()).isEqualTo(new BigDecimal(requestDto.latitude()));
        assertThat(savedAddress.getLongitude()).isEqualTo(new BigDecimal(requestDto.longitude()));
        assertThat(savedAddress.getAddressLine1()).isEqualTo(requestDto.addressLine1());
        assertThat(savedAddress.getAddressLine2()).isEqualTo(requestDto.addressLine2());
        assertThat(savedAddress.getBoormiId()).isEqualTo(requestDto.boormiId());
    }

    @Test
    void 전체_주소를_조회하면_응답_dto_목록으로_변환되어_반환된다() {
        Address address = Address.builder()
                .addressId(UUID.randomUUID())
                .addressAlias("우리집")
                .latitude(new BigDecimal("37.123456"))
                .longitude(new BigDecimal("127.123456"))
                .addressLine1("서울시 강남구")
                .addressLine2("101동 202호")
                .boormiId(UUID.randomUUID())
                .build();
        when(addressRepository.findAll()).thenReturn(List.of(address));

        List<AddressResponseDto> result = addressService.findAll();

        assertThat(result).containsExactly(new AddressResponseDto(
                address.getAddressAlias(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getBoormiId().toString()
        ));
    }
}

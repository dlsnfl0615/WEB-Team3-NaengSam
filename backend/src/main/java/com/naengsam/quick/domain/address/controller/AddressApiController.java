package com.naengsam.quick.domain.address.controller;

import com.naengsam.quick.domain.address.dto.AddressApiRequestDto;
import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.service.AddressApiService;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/address")
@RequiredArgsConstructor
public class AddressApiController {

    private final AddressApiService addressApiService;
    private final CoordinatesService coordinatesService;

    @PostMapping("/place")
    public UUID saveAddresses(@RequestBody AddressApiRequestDto requestDto) {
        // 카카오의 도로명주소 -> 위도 경도로 변환 api 사용
        CoordinatesResponseDto originCoordinates = coordinatesService.getCoordinates(requestDto.origin());
        CoordinatesResponseDto destinationCoordinates = coordinatesService.getCoordinates(requestDto.destination());

        // 별명은 나중에 생성 가능하도록 처음엔 null로 생성
        Addresses addresses = Addresses.builder()
                .originAddressLine1(requestDto.origin())
                .originAddressLine2(requestDto.originDetail())
                .originLatitude(originCoordinates.documents().getFirst().roadAddress().y())
                .originLongitude(originCoordinates.documents().getFirst().roadAddress().x())
                .destinationAddressLine1(requestDto.destination())
                .destinationAddressLine2(requestDto.destinationDetail())
                .destinationLatitude(destinationCoordinates.documents().getFirst().roadAddress().y())
                .destinationLongitude(destinationCoordinates.documents().getFirst().roadAddress().x())
                .build();

        // 로그인 기능 연결해서 하드코딩 제거 예정
        UUID orderId = UUID.fromString("1f1684da-bdc1-4e2a-a87f-66975aa090a8");
        addressApiService.updateAddresses(orderId, addresses);

        return orderId;
    }
}

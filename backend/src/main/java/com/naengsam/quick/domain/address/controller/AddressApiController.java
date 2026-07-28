package com.naengsam.quick.domain.address.controller;

import com.naengsam.quick.domain.address.dto.AddressRequestDto;
import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.service.AddressService;
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
public class AddressController {

    private final AddressService addressService;
    private final CoordinatesService coordinatesService;

    @PostMapping("/place")
    public UUID saveAddresses(@RequestBody AddressRequestDto requestDto) {
        String origin = requestDto.origin();
        String originDetail = requestDto.originDetail();
        String destination = requestDto.destination();
        String destinationDetail = requestDto.destinationDetail();

        // 카카오의 도로명주소 -> 위도 경도로 변환 api 사용
        CoordinatesResponseDto originCoordinates = coordinatesService.getCoordinates(origin);
        String originLatitude = originCoordinates.documents().getFirst().roadAddress().y();
        String originLongitude = originCoordinates.documents().getFirst().roadAddress().x();

        CoordinatesResponseDto destinationCoordinates = coordinatesService.getCoordinates(destination);
        String destinationLatitude = destinationCoordinates.documents().getFirst().roadAddress().y();
        String destinationLongitude = destinationCoordinates.documents().getFirst().roadAddress().x();

        // 별명은 나중에 생성 가능하도록 처음엔 null로 생성
        Addresses addresses = Addresses.builder()
                .originAddressLine1(origin)
                .originAddressLine2(originDetail)
                .originLatitude(originLatitude)
                .originLongitude(originLongitude)
                .destinationAddressLine1(destination)
                .destinationAddressLine2(destinationDetail)
                .destinationLatitude(destinationLatitude)
                .destinationLongitude(destinationLongitude)
                .build();

        // 로그인 기능 연결해서 하드코딩 제거 예정
        addressService.updateAddresses(UUID.fromString("1f1684da-bdc1-4e2a-a87f-66975aa090a8"), addresses);

        return UUID.fromString("1f1684da-bdc1-4e2a-a87f-66975aa090a8");
    }
}

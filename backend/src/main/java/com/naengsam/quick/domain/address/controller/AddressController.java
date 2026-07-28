package com.naengsam.quick.domain.address.controller;

import com.naengsam.quick.domain.address.dto.AddressRequestDto;
import com.naengsam.quick.domain.address.dto.AddressResponseDto;
import com.naengsam.quick.domain.address.service.AddressService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/address")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public UUID saveAddress(@RequestBody AddressRequestDto requestDto) {
        return addressService.saveAddress(requestDto);
    }

    @GetMapping
    public List<AddressResponseDto> findAll() {
        return addressService.findAll();
    }
}

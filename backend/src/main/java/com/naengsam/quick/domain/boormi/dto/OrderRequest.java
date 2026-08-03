package com.naengsam.quick.domain.boormi.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @Schema(description = "출발지 기본주소(도로명)", example = "서울시 강남구 테헤란로 123")
        @NotBlank
        @Size(max = 255)
        String originAddressLine1,

        @Schema(description = "출발지 상세주소", example = "101동 1201호")
        @Size(max = 255)
        String originAddressLine2,

        @Schema(description = "도착지 기본주소(도로명)", example = "서울시 서초구 서초대로 45")
        @NotBlank
        @Size(max = 255)
        String destinationAddressLine1,

        @Schema(description = "도착지 상세주소", example = "202동 305호")
        @Size(max = 255)
        String destinationAddressLine2,

        @Schema(description = "물건 이름", example = "서류봉투")
        @NotBlank
        @Size(max = 50)
        String itemName,

        @Schema(description = "물건 유형", example = "DOCUMENT")
        @NotNull
        ItemCd itemCd,

        @Schema(description = "물건 이미지 URL", example = "https://cdn.naengsam.com/orders/sample.jpg")
        @Size(max = 500)
        String imageKey,

        @Schema(description = "물건 상세 설명", example = "파손 주의 계약서")
        @Size(max = 255)
        String itemDetail,

        @Schema(description = "배달 요청사항", example = "문 앞에 두고 벨 눌러주세요")
        @Size(max = 255)
        String deliveryRequest
) {
}

package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 진행 중인 배달의 상대방 연락처. 배달 화면에서 사용자가 '연락하기'를 눌렀을 때만 조회한다.
 * 개인정보라 추적 상세(DeliveryDetailResponseDto)에 싣지 않고 별도 응답으로 분리했다.
 */
public record DeliveryContactDto(
        @Schema(description = "상대방 이름", example = "김드림")
        String counterpartName,

        @Schema(description = "상대방 전화번호(하이픈 없는 숫자열)", example = "01012345678")
        String counterpartPhoneNumber,

        @Schema(description = "조회한 사용자가 이 배달의 드리미인지 여부(false면 부르미). 상대 역할 표기 분기용",
                example = "false")
        boolean viewerIsDreami
) {
    public static DeliveryContactDto from(Boormi counterpart, boolean viewerIsDreami) {
        return new DeliveryContactDto(counterpart.getName(), counterpart.getPhoneNumber(), viewerIsDreami);
    }
}

package com.naengsam.quick.domain.dreami.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 업로드 확인 요청. presigned URL 발급 시 받은 신분증/범죄이력조회서 S3 key를 그대로 담아 보낸다.
 */
// record: 필드를 선언하면 생성자·getter(필드명() 형태)·equals/hashCode/toString이 자동으로 만들어지는
// 불변(immutable) 데이터 클래스 문법. 값이 한 번 만들어지면 안 바뀌는 DTO/데이터 전달용으로 자주 쓴다.
public record DreamiAuthRequestDto(
        @NotBlank // Jakarta Bean Validation: null이거나 공백만 있으면 검증 실패(컨트롤러의 @Valid와 함께 동작)
        String idCardKey,

        @NotBlank
        String criminalRecordKey
) {
}

package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.dto.UploadRequestDto;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 드리미 인증 컨트롤러. 신분증/범죄이력조회서가 S3에 모두 업로드됐는지 확인하고, 확인되면 인증 신청을 저장한다.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/dreami")
@Tag(name = "드리미 인증 컨트롤러", description = "신분증/범죄이력조회서 업로드를 확인하고 드리미 인증 신청을 저장한다.")
public class DreamiAuthController {

    private final S3PresignService s3PresignService;
    private final UploadSessionService uploadSessionService;
    private final DreamiService dreamiService;

    @Operation(summary = "업로드 확인", description = "presigned URL로 업로드한 신분증/범죄이력조회서 파일이 S3에 실제로 존재하는지 확인한다.")
    @PostMapping("/check")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = UploadErrorCode.class, codes = {"FILE_NOT_FOUND", "KEY_OWNER_MISMATCH",
            "STORAGE_UPLOAD_FAILED"})
    public Boolean checkUpload(@Valid @RequestBody UploadRequestDto requestDto, @LoginUser UUID boormiId) {
        // 다른 사람에게 발급됐거나 다른 용도로 발급된 key를 그대로 제출하는 것을 막는다.
        uploadSessionService.validateScope(UploadPurpose.DREAMI_ID_CARD, boormiId, null, requestDto.idCardKey());
        uploadSessionService.validateScope(UploadPurpose.DREAMI_CRIMINAL_RECORD, boormiId, null,
                requestDto.criminalRecordKey());

        // 하나라도 업로드 완료되지 않은 상태라면
        if (!s3PresignService.isFileUploaded(requestDto.idCardKey())
                || !s3PresignService.isFileUploaded(requestDto.criminalRecordKey())) {
            return false;
        }

        // 세션을 소비 처리한다. 재시도로 이미 소비된 요청이면 저장을 반복하지 않는다.
        boolean idCardNewlyConsumed = uploadSessionService.consume(requestDto.idCardKey());
        boolean criminalRecordNewlyConsumed = uploadSessionService.consume(requestDto.criminalRecordKey());

        // todo: 일단 어드민 개입 없이 무조건 허용으로
        if (idCardNewlyConsumed || criminalRecordNewlyConsumed) {
            dreamiService.saveVerificationFileKeys(boormiId, requestDto.idCardKey(), requestDto.criminalRecordKey());
        }

        return true;
    }
}

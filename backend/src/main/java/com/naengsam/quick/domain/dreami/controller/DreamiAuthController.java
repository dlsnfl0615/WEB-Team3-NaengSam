package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.dto.UploadRequestDto;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
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
    private final DreamiService dreamiService;

    @Operation(summary = "업로드 확인", description = "presigned URL로 업로드한 신분증/범죄이력조회서 파일이 S3에 실제로 존재하는지 확인한다.")
    @PostMapping("/check")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = UploadErrorCode.class, codes = {"KEY_OWNER_MISMATCH", "STORAGE_UPLOAD_FAILED"})
    public Boolean checkUpload(@Valid @RequestBody UploadRequestDto requestDto, @LoginUser UUID boormiId) {
        // 다른 사용자에게 발급된 key를 유출/추측해서 제출하는 것을 막는다.
        s3PresignService.validateOwnership(boormiId, requestDto.idCardKey());
        s3PresignService.validateOwnership(boormiId, requestDto.criminalRecordKey());

        // 하나라도 업로드 완료되지 않은 상태라면
        if (!s3PresignService.isFileUploaded(requestDto.idCardKey())
                || !s3PresignService.isFileUploaded(requestDto.criminalRecordKey())) {
            return false;
        }

        // 모두 완료됐으면 presigned url의 download url을 위한 key만 db에 저장
        // 나중에 이 키를 가지고  생성해서 s3에서 다운로드하면 됨
        // todo: 일단 어드민 개입 없이 무조건 허용으로
        dreamiService.saveVerificationFileKeys(boormiId, requestDto.idCardKey(), requestDto.criminalRecordKey());

        return true;
    }
}

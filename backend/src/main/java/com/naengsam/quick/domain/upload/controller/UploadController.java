package com.naengsam.quick.domain.upload.controller;

import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.dto.PresignedUrlResponseDto;
import com.naengsam.quick.domain.upload.dto.UploadRequestDto;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.session.LoginRequired;
import com.naengsam.quick.global.session.LoginUser;
import com.naengsam.quick.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 업로드 컨트롤러. presigned URL 발급과, 클라이언트가 그 URL로 S3에 실제 파일을 올렸는지 확인하는 것을 담당한다.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/upload")
@Tag(name = "업로드 컨트롤러", description = "S3 presigned URL을 발급하고, 실제로 파일이 업로드됐는지 확인한다.")
@LoginRequired
public class UploadController {

    private final S3PresignService s3PresignService;
    private final DreamiService dreamiService;

    @Operation(summary = "업로드용 presigned URL 발급", description = "이 fileName으로 S3에 직접 PUT 할 수 있는 presigned URL과, 그 파일의 S3 key를 발급한다.")
    @GetMapping("/url")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = UploadErrorCode.class, codes = {"UNSUPPORTED_FILE_TYPE"})
    public PresignedUrlResponseDto getPresignedUrl(@RequestParam String fileName) {
        String key = "/uploads/" + UUID.randomUUID() + "-" + fileName;

        String url = s3PresignService.generateUploadUrl(key);

        return new PresignedUrlResponseDto(url, key);
    }

    @Operation(summary = "업로드 확인", description = "presigned URL로 업로드한 신분증/범죄이력조회서 파일이 S3에 실제로 존재하는지 확인한다.")
    @PostMapping("/check")
    @ApiResponse(responseCode = "200", description = "요청에 성공했습니다.")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"UNAUTHORIZED"})
    public Boolean checkUpload(@Valid @RequestBody UploadRequestDto requestDto, @LoginUser UUID boormiId) {

        // 하나라도 업로드 완료되지 않은 상태라면
        if (!s3PresignService.isFileUploaded(requestDto.idCardKey())
                || !s3PresignService.isFileUploaded(requestDto.criminalRecordKey())) {
            return false;
        }

        // 모두 완료됐으면 presigned url의 download url을 위한 key만 db에 저장
        // 나중에 이 키를 가지고  생성해서 s3에서 다운로드하면 됨
        dreamiService.saveVerificationFileKeys(boormiId, requestDto.idCardKey(), requestDto.criminalRecordKey());

        return true;
    }
}

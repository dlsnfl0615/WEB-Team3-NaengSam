package com.naengsam.quick.domain.upload.controller;

import com.naengsam.quick.domain.upload.dto.UploadRequestDto;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final S3PresignService s3PresignService;

    @PostMapping("/check")
    public Boolean checkUpload(@Valid @RequestBody UploadRequestDto requestDto) {

        // 하나라도 업로드 완료되지 않은 상태라면
        if (!s3PresignService.isFileUploaded(requestDto.idCardKey())
                || !s3PresignService.isFileUploaded(requestDto.criminalRecordKey())) {
            return false;
        }

        String idCardDownloadUrl = s3PresignService.generateDownloadUrl(requestDto.idCardKey());
        String criminalRecordDownloadUrl = s3PresignService.generateDownloadUrl(requestDto.criminalRecordKey());

        return true;
    }
}

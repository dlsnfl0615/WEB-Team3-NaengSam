package com.naengsam.quick.domain.dreami.controller;

import com.naengsam.quick.domain.dreami.dto.PresignedUrlResponseDto;
import com.naengsam.quick.domain.dreami.service.DreamiService;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.global.session.LoginRequired;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dreami")
public class DreamiAuthController {

    private final S3PresignService s3PresignService;
    private final DreamiService dreamiService;

    @LoginRequired
    @GetMapping("/verification")
    public PresignedUrlResponseDto getPresignedUrl(@RequestParam String fileName) {
        String idCardKey = "uploads/idCard/" + UUID.randomUUID() + "-" + fileName;
        String criminalRecordKey = "uploads/criminalRecord/" + UUID.randomUUID() + "-" + fileName;

        // todo: 사진 하나 올리고 뒤로가기 했다가 다시 접속하는 경우 이전에 올렸던 사진 다시 나타나야 함

        String idCardUrl = s3PresignService.generateUploadUrl(idCardKey);
        String criminalRecordUrl = s3PresignService.generateUploadUrl(criminalRecordKey);

        return new PresignedUrlResponseDto(idCardUrl, idCardKey, criminalRecordUrl, criminalRecordKey);
    }
}

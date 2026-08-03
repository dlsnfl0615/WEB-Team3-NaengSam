package com.naengsam.quick.domain.upload.exception;

import com.naengsam.quick.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UploadErrorCode implements BaseErrorCode {

    NO_FILE_ATTACHED(HttpStatus.BAD_REQUEST, "FILE_001", "파일을 첨부해 주세요."),
    // TODO: 최대 파일 용량 정책 미확정. 확정되면 presigned URL 발급 시 fileSize를 받아 여기서 검사한다.
    FILE_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "FILE_002", "파일 용량이 너무 커요."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_003", "지원하지 않는 파일 형식이에요."),
    STORAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "업로드에 실패했어요. 다시 시도해 주세요."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_005", "파일을 찾을 수 없습니다."),
    INVALID_FILE_NAME(HttpStatus.NOT_FOUND, "FILE_006", "파일 이름이 올바르지 않아요."),
    KEY_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "FILE_007", "본인이 업로드한 파일만 사용할 수 있어요."),
    MISSING_RESOURCE_ID(HttpStatus.BAD_REQUEST, "FILE_008", "이 용도로 업로드하려면 대상 정보가 필요해요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

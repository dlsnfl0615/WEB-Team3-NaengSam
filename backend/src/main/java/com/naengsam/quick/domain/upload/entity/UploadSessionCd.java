package com.naengsam.quick.domain.upload.entity;

public enum UploadSessionCd {
    ISSUED, // presigned url을 발급한 직후의 상태
    CONSUMED // 해당 키가 엔드포인트에서 실제로 사용 완료 처리된 상태
}

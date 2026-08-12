package com.naengsam.quick.global.notification.dto;

/**
 * 브라우저가 {@code pushManager.subscribe}의 {@code applicationServerKey}로 쓰는 VAPID 공개키.
 *
 * <p>공개 상수라 로그인 없이 조회할 수 있다. 빌드 타임에 인라인되는 {@code VITE_*} 환경변수 대신 이 엔드포인트로
 * 내려주므로, 키 교체나 푸시 on/off가 프론트 재배포 없이 백엔드 환경변수만으로 끝난다.
 *
 * @param publicKey 미설정(푸시 비활성)이면 {@code null}. 프론트는 이 경우 권한 요청 UI를 렌더하지 않는다.
 */
public record VapidPublicKeyDto(String publicKey) {
}

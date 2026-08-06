/**
 * 지정한 색의 핀 모양 MarkerImage를 만든다(카카오 마커는 이미지 URI 필요).
 * KakaoMap·DeliveryRouteMap이 공유한다.
 */
export function pinImage(kakao: typeof window.kakao, color: string) {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="30" height="40" viewBox="0 0 30 40">` +
    `<path d="M15 0C6.716 0 0 6.716 0 15c0 10.5 15 25 15 25s15-14.5 15-25C30 6.716 23.284 0 15 0z" fill="${color}"/>` +
    `<circle cx="15" cy="15" r="5.5" fill="#fff"/></svg>`;
  return new kakao.maps.MarkerImage(
    `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`,
    new kakao.maps.Size(30, 40),
    { offset: new kakao.maps.Point(15, 40) },
  );
}

import { useEffect } from "react";
import { useCurrentAddressStore } from "@/shared/store/currentAddressStore";

export interface CurrentAddressState {
  /** 도로명주소(없으면 지번주소로 대체). 아직 못 구했으면 null. */
  address: string | null;
  /** geolocation 미지원/권한 거부/지오코딩 실패 등 오류 메시지(정상 시 null). */
  error: string | null;
}

/**
 * 현재 위치의 도로명주소. 실제 조회·캐시(5분·100m 재사용)는 `currentAddressStore`가 맡고,
 * 여기서는 화면 마운트 시 캐시 갱신을 트리거만 한다 — 화면을 오갈 때마다 카카오를 다시
 * 부르지 않기 위해서다.
 */
export function useCurrentAddress(): CurrentAddressState {
  const ensureCurrentAddress = useCurrentAddressStore((s) => s.ensureCurrentAddress);
  const address = useCurrentAddressStore((s) => s.address);
  const error = useCurrentAddressStore((s) => s.error);

  useEffect(() => {
    void ensureCurrentAddress();
  }, [ensureCurrentAddress]);

  return { address, error };
}

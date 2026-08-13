import { useRef, useState } from "react";

/**
 * S3 SigV4 presigned URL의 `X-Amz-Date`/`X-Amz-Expires` 쿼리 파라미터를 읽어 실제 만료 시각(epoch ms)을
 * 계산한다. 백엔드가 URL에 직접 서명해 넣은 값을 그대로 읽는 것이라, 백엔드의 signatureDuration이 바뀌어도
 * 프론트에 따로 상수를 맞춰둘 필요가 없다.
 *
 * 두 파라미터가 없으면(로컬 dev-storage처럼 서명 없는 URL, 또는 URL 파싱 자체가 실패하면) null을 반환한다 —
 * "만료 개념이 없음"으로 취급해 호출부가 계속 재사용해도 되게 한다.
 */
export function parsePresignedUrlExpiresAt(url: string): number | null {
  try {
    const params = new URL(url).searchParams;
    const amzDate = params.get("X-Amz-Date");
    const amzExpiresSeconds = params.get("X-Amz-Expires");
    if (!amzDate || !amzExpiresSeconds) return null;

    const issuedAt = parseAmzDate(amzDate);
    const expiresSeconds = Number(amzExpiresSeconds);
    if (issuedAt == null || Number.isNaN(expiresSeconds)) return null;

    return issuedAt + expiresSeconds * 1000;
  } catch {
    return null;
  }
}

/** AWS SigV4의 `X-Amz-Date` 형식(예: "20260813T123456Z", 항상 UTC)을 epoch ms로 변환한다. */
function parseAmzDate(amzDate: string): number | null {
  const match = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/.exec(amzDate);
  if (!match) return null;
  const [, year, month, day, hour, minute, second] = match;
  return Date.UTC(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second),
  );
}

export interface UsePresignedPhotoResult {
  open: boolean;
  /** undefined = 아직 조회 안 함, null = 조회했는데 사진이 없음, string = 사진 URL. */
  photoUrl: string | null | undefined;
  loading: boolean;
  /** 모달을 열고, 캐싱된 URL이 없거나 만료됐으면 그때 새로 조회한다. */
  openModal: () => void;
  closeModal: () => void;
}

/**
 * presigned 사진 URL을 "누른 시점에" 조회하고, URL 자체에 박힌 만료 시각이 지나기 전까지만 재사용한다.
 * "사진 없음"(null)은 만료 판단 없이 항상 다음 클릭에서 재시도한다 — 나중에 사진이 생기는 경우(픽업 인증
 * 사진처럼)가 있어서, 없다고 캐싱해버리면 영영 "없음"으로 남기 때문이다.
 */
export function usePresignedPhoto(
  fetchUrl: () => Promise<string | null>,
): UsePresignedPhotoResult {
  const [open, setOpen] = useState(false);
  const [photoUrl, setPhotoUrl] = useState<string | null | undefined>(
    undefined,
  );
  const [loading, setLoading] = useState(false);
  // null = 만료 개념 없음(로컬 dev-storage 등) → 있는 한 계속 재사용해도 됨.
  const expiresAtRef = useRef<number | null>(null);

  const openModal = () => {
    setOpen(true);
    const isFresh =
      !!photoUrl &&
      (expiresAtRef.current === null || Date.now() < expiresAtRef.current);
    if (isFresh || loading) return;

    setLoading(true);
    fetchUrl()
      .then((url) => {
        setPhotoUrl(url);
        expiresAtRef.current = url ? parsePresignedUrlExpiresAt(url) : null;
      })
      .catch(() => {
        setPhotoUrl(null);
        expiresAtRef.current = null;
      })
      .finally(() => setLoading(false));
  };

  return { open, photoUrl, loading, openModal, closeModal: () => setOpen(false) };
}

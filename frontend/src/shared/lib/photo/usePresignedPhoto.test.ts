import { describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { parsePresignedUrlExpiresAt, usePresignedPhoto } from "./usePresignedPhoto";

describe("parsePresignedUrlExpiresAt", () => {
  it("X-Amz-Date와 X-Amz-Expires로 만료 시각(epoch ms)을 계산한다", () => {
    const url =
      "https://bucket.s3.amazonaws.com/key?X-Amz-Date=20260813T120000Z&X-Amz-Expires=300";

    const expiresAt = parsePresignedUrlExpiresAt(url);

    expect(expiresAt).toBe(Date.UTC(2026, 7, 13, 12, 0, 0) + 300 * 1000);
  });

  it("서명 파라미터가 없으면(로컬 dev-storage 등) null을 반환한다", () => {
    const url = "http://localhost:8080/api/v1/upload/dev-storage?key=abc";

    expect(parsePresignedUrlExpiresAt(url)).toBeNull();
  });

  it("URL 자체가 잘못됐으면 null을 반환한다", () => {
    expect(parsePresignedUrlExpiresAt("not a url")).toBeNull();
  });
});

describe("usePresignedPhoto", () => {
  it("만료 전에는 openModal을 다시 불러도 재조회하지 않는다", async () => {
    const farFutureExpiry = `X-Amz-Date=20990101T000000Z&X-Amz-Expires=300`;
    const fetchUrl = vi
      .fn()
      .mockResolvedValue(`https://s3.example.com/key?${farFutureExpiry}`);

    const { result } = renderHook(() => usePresignedPhoto(fetchUrl));

    act(() => result.current.openModal());
    await waitFor(() => expect(result.current.photoUrl).toBeTruthy());
    expect(fetchUrl).toHaveBeenCalledTimes(1);

    act(() => result.current.closeModal());
    act(() => result.current.openModal());

    expect(fetchUrl).toHaveBeenCalledTimes(1);
  });

  it("사진이 없으면(null) 다음 openModal에서 다시 조회한다", async () => {
    const fetchUrl = vi.fn().mockResolvedValue(null);

    const { result } = renderHook(() => usePresignedPhoto(fetchUrl));

    act(() => result.current.openModal());
    await waitFor(() => expect(result.current.photoUrl).toBeNull());
    expect(fetchUrl).toHaveBeenCalledTimes(1);

    act(() => result.current.closeModal());
    act(() => result.current.openModal());
    await waitFor(() => expect(fetchUrl).toHaveBeenCalledTimes(2));
  });
});

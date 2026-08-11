import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import {
  api,
  DeliveryStatusResponseDtoStatus,
  type DeliveryDetailResponseDto,
  type GetDeliveryDetail200,
} from "@/shared/api";
import { useDeliveryDetailGate } from "./useDeliveryDetailGate";

vi.mock("@/shared/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/shared/api")>();
  return {
    ...actual,
    api: { ...actual.api, getDeliveryDetail: vi.fn() },
  };
});

const ORDER_ID = "order-1";
const getDeliveryDetail = vi.mocked(api.getDeliveryDetail);

function detailResponse(
  status: DeliveryStatusResponseDtoStatus,
): GetDeliveryDetail200 {
  return {
    result: { orderId: ORDER_ID, status } as DeliveryDetailResponseDto,
  };
}

beforeEach(() => {
  getDeliveryDetail.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useDeliveryDetailGate", () => {
  it("이미 ready인 상태에서 refresh가 터미널 상태를 받으면 blockingModal이 열린다", async () => {
    getDeliveryDetail
      .mockResolvedValueOnce(
        detailResponse(DeliveryStatusResponseDtoStatus.DELIVERING),
      )
      .mockResolvedValueOnce(
        detailResponse(
          DeliveryStatusResponseDtoStatus.PICKUP_CANCELLED_BY_DREAMI,
        ),
      );

    const { result } = renderHook(() => useDeliveryDetailGate(ORDER_ID));

    await waitFor(() => expect(result.current.ready).toBe(true));
    expect(result.current.blockingModal.open).toBe(false);

    act(() => {
      result.current.refresh();
    });

    await waitFor(() => expect(result.current.blockingModal.open).toBe(true));
    expect(result.current.ready).toBe(false);
    expect(result.current.blockingModal.canRetry).toBe(false);
    expect(result.current.blockingModal.title).toContain("드리미");
  });
});

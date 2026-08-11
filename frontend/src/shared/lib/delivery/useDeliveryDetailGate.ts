import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  isApiError,
  type DeliveryDetailResponseDto,
} from "@/shared/api";
import { getUntrackableDeliveryNotice } from "../deliveryAvailability";
import { rememberDeliveryStage } from "../deliveryStageMemo";

const DEFAULT_ERROR = "잠시 후 다시 시도해 주세요.";
const DEFAULT_TITLE = "배달 정보를 불러오지 못했어요";
const DEFAULT_GUIDANCE =
  "정보를 확인하기 전에는 배달 기능을 사용할 수 없어요.";

export interface UseDeliveryDetailGateOptions {
  /** false면 상세 조회 없이 기능을 허용한다(예: mock 모드). 기본 true. */
  enabled?: boolean;
  /** 조회 성공 후 화면 전용 상태를 반영한다. */
  onLoaded?: (detail: DeliveryDetailResponseDto) => void;
}

export interface DeliveryDetailBlockNotice {
  title: string;
  message: string;
  guidance?: string;
  canRetry?: boolean;
}

export interface DeliveryDetailBlockingModalState {
  open: boolean;
  title: string;
  message: string;
  guidance: string;
  canRetry: boolean;
}

export interface DeliveryDetailGateState {
  detail: DeliveryDetailResponseDto | null;
  ready: boolean;
  loading: boolean;
  blockingModal: DeliveryDetailBlockingModalState;
  retry: () => void;
  /** ready 상태를 유지한 채 상세를 조용히 다시 불러온다(경로·배송완료예상시간이 뒤늦게 계산됐을 때 반영용). */
  refresh: () => void;
  block: (notice: DeliveryDetailBlockNotice) => void;
}

/** 배송 상세 조회가 성공한 주문에만 추적 기능을 허용한다. */
export function useDeliveryDetailGate(
  orderId: string | null,
  options: UseDeliveryDetailGateOptions = {},
): DeliveryDetailGateState {
  const { enabled = true, onLoaded } = options;
  const [detail, setDetail] = useState<DeliveryDetailResponseDto | null>(null);
  const [readyOrderId, setReadyOrderId] = useState<string | null>(null);
  const [attemptedOrderId, setAttemptedOrderId] = useState<string | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [message, setMessage] = useState(DEFAULT_ERROR);
  const [title, setTitle] = useState(DEFAULT_TITLE);
  const [guidance, setGuidance] = useState(DEFAULT_GUIDANCE);
  const [canRetry, setCanRetry] = useState(true);
  const requestIdRef = useRef(0);
  const onLoadedRef = useRef(onLoaded);

  const active = enabled && !!orderId;
  const ready = !enabled || (!!orderId && readyOrderId === orderId);
  const attempted = !!orderId && attemptedOrderId === orderId;

  useEffect(() => {
    onLoadedRef.current = onLoaded;
  });

  const requestDetail = useCallback(() => {
    if (!enabled || !orderId) return;

    const requestId = ++requestIdRef.current;
    void api
      .getDeliveryDetail(orderId)
      .then(({ result }) => {
        if (requestId !== requestIdRef.current) return;
        if (!result) throw new Error("배달 정보가 비어 있습니다.");

        const closedNotice = getUntrackableDeliveryNotice(result.status);
        if (closedNotice) {
          if (result.status) rememberDeliveryStage(orderId, result.status);
          // 이미 ready였던 화면에서 조용한 refresh(재연결 복구 등)로 취소/완료를 발견하면
          // ready를 내려 차단 모달(blockingModal.open = attempted && !ready)이 열리도록 한다.
          setDetail(null);
          setReadyOrderId(null);
          setAttemptedOrderId(orderId);
          setTitle(closedNotice.title);
          setMessage(closedNotice.message);
          setGuidance("");
          setCanRetry(false);
          return;
        }

        if (result.status) rememberDeliveryStage(orderId, result.status);
        onLoadedRef.current?.(result);
        setDetail(result);
        setReadyOrderId(orderId);
        setAttemptedOrderId(orderId);
      })
      .catch((error: unknown) => {
        if (requestId !== requestIdRef.current) return;
        const status = isApiError(error) ? error.status : 0;
        setAttemptedOrderId(orderId);
        setMessage(isApiError(error) ? error.message : DEFAULT_ERROR);
        setTitle(DEFAULT_TITLE);
        setGuidance(DEFAULT_GUIDANCE);
        setCanRetry(![401, 403, 404].includes(status));
      })
      .finally(() => {
        if (requestId === requestIdRef.current) setLoading(false);
      });
  }, [enabled, orderId]);

  useEffect(() => {
    if (!active) {
      requestIdRef.current += 1;
      return;
    }

    requestDetail();
    return () => {
      requestIdRef.current += 1;
    };
  }, [active, requestDetail]);

  const retry = useCallback(() => {
    setDetail(null);
    setReadyOrderId(null);
    setLoading(true);
    setTitle(DEFAULT_TITLE);
    setMessage(DEFAULT_ERROR);
    setGuidance(DEFAULT_GUIDANCE);
    setCanRetry(true);
    requestDetail();
  }, [requestDetail]);

  const block = useCallback(
    (notice: DeliveryDetailBlockNotice) => {
      if (!orderId) return;
      requestIdRef.current += 1;
      setDetail(null);
      setReadyOrderId(null);
      setAttemptedOrderId(orderId);
      setLoading(false);
      setTitle(notice.title);
      setMessage(notice.message);
      setGuidance(notice.guidance ?? "");
      setCanRetry(notice.canRetry ?? false);
    },
    [orderId],
  );

  return {
    detail,
    ready,
    loading: active && loading,
    blockingModal: {
      open: enabled && attempted && !ready,
      title,
      message,
      guidance,
      canRetry,
    },
    retry,
    refresh: requestDetail,
    block,
  };
}

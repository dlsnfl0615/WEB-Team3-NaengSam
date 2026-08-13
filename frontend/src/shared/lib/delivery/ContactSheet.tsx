import { useEffect, useState } from "react";
import { BottomSheet, Button, Icon } from "@/shared/ui";
import { api, isApiError, type DeliveryContactDto } from "@/shared/api";
import { formatPhone } from "../formatPhone";

export interface ContactSheetProps {
  open: boolean;
  /** 진행 중인 배달의 주문 ID. 없으면 조회하지 않는다(mock 화면). */
  orderId: string | null;
  onClose: () => void;
}

/**
 * 배달 화면의 '연락' 시트. 열릴 때 상대방(부르미↔드리미) 연락처를 한 번 조회하고,
 * 전화하기 / 핑 보내기 두 버튼을 세로로 보여준다.
 *
 * 전화번호는 개인정보라 시트를 열 때만 받아온다(추적 상세 응답에는 실려 있지 않다).
 * 핑 보내기는 아직 UI만 있고 동작하지 않는다.
 */
export function ContactSheet({ open, orderId, onClose }: ContactSheetProps) {
  // 결과와 에러를 한 상태로 묶어 두면 로딩 여부를 "둘 다 비어 있음"으로 계산할 수 있어,
  // 이펙트 본문에서 setState를 호출하지 않아도 된다(react-hooks/set-state-in-effect).
  const [fetched, setFetched] = useState<{
    contact: DeliveryContactDto | null;
    error: string | null;
  }>({ contact: null, error: null });

  useEffect(() => {
    if (!open || !orderId) return;
    let cancelled = false;
    void api
      .getDeliveryContact(orderId)
      .then(({ result }) => {
        if (!cancelled) setFetched({ contact: result ?? null, error: null });
      })
      .catch((e) => {
        if (cancelled) return;
        setFetched({
          contact: null,
          error: isApiError(e) ? e.message : "연락처를 불러오지 못했어요.",
        });
      });
    return () => {
      cancelled = true;
    };
  }, [open, orderId]);

  const { contact, error } = fetched;
  const loading = !!orderId && !contact && !error;
  const phone = contact?.counterpartPhoneNumber ?? "";
  const role = contact?.viewerIsDreami ? "부르미" : "드리미";

  return (
    <BottomSheet open={open} label="연락" onClose={onClose}>
      <p className="text-lg font-bold text-navy-900">연락</p>

      {loading ? (
        <p className="py-6 text-center text-sm text-muted">불러오는 중…</p>
      ) : error ? (
        <p className="py-6 text-center text-sm text-status-danger">{error}</p>
      ) : (
        <div className="flex flex-col gap-1 py-2">
          <p className="text-sm text-muted">{role}</p>
          <p className="text-md font-bold text-navy-900">
            {contact?.counterpartName ?? "-"}
          </p>
          {phone && <p className="text-sm text-muted">{formatPhone(phone)}</p>}
        </div>
      )}

      <div className="flex flex-col gap-2">
        <Button
          variant="navy"
          block
          disabled={!phone}
          onClick={() => {
            window.location.href = `tel:${phone}`;
          }}
        >
          <Icon name="phone" size={18} />
          전화하기
        </Button>
        {/* 핑 보내기는 아직 동작하지 않는다(UI만). */}
        <Button variant="outline" block>
          <Icon name="bell" size={18} />핑 보내기
        </Button>
      </div>
    </BottomSheet>
  );
}

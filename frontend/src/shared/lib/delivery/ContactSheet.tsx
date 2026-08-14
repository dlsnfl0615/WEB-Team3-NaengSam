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

/** 핑을 보낸 뒤 버튼을 다시 누를 수 있게 되기까지의 시간(ms). 상대 잠금화면에 알림이 연달아 쌓이는 것을 막는다. */
const PING_COOLDOWN_MS = 30_000;

/**
 * 배달 화면의 '연락' 시트. 열릴 때 상대방(부르미↔드리미) 연락처를 한 번 조회하고,
 * 전화하기 / 핑 보내기 버튼을 세로로 보여준다.
 *
 * 전화번호는 개인정보라 시트를 열 때만 받아온다(추적 상세 응답에는 실려 있지 않다).
 * 핑 보내기는 부르미 → 드리미 단방향이라 드리미에게는 버튼 자체를 감춘다(서버도 부르미만 허용한다).
 */
export function ContactSheet({ open, orderId, onClose }: ContactSheetProps) {
  // 결과와 에러를 한 상태로 묶어 두면 로딩 여부를 "둘 다 비어 있음"으로 계산할 수 있어,
  // 이펙트 본문에서 setState를 호출하지 않아도 된다(react-hooks/set-state-in-effect).
  const [fetched, setFetched] = useState<{
    contact: DeliveryContactDto | null;
    error: string | null;
  }>({ contact: null, error: null });
  // 핑 전송 상태: 진행 중 여부와 결과 문구(성공/실패)를 함께 들고 있는다.
  const [ping, setPing] = useState<{
    sending: boolean;
    sent: boolean;
    message: string | null;
  }>({ sending: false, sent: false, message: null });

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

  // 보낸 직후에는 쿨다운 동안 다시 못 누르게 잠갔다가 풀어 준다.
  useEffect(() => {
    if (!ping.sent) return;
    const timer = setTimeout(
      () => setPing({ sending: false, sent: false, message: null }),
      PING_COOLDOWN_MS,
    );
    return () => clearTimeout(timer);
  }, [ping.sent]);

  const { contact, error } = fetched;
  const loading = !!orderId && !contact && !error;
  const phone = contact?.counterpartPhoneNumber ?? "";
  const role = contact?.viewerIsDreami ? "부르미" : "드리미";
  // 핑은 부르미가 드리미를 깨우는 기능이라 드리미 화면에는 없다. 역할을 아직 모르는 동안(로딩·조회 실패)에도
  // 숨겨 둔다 — 드리미에게 잠깐이라도 보였다가 사라지는 편보다 낫다.
  const canPing = contact != null && !contact.viewerIsDreami;

  const sendPing = async () => {
    if (!orderId || ping.sending || ping.sent) return;
    setPing({ sending: true, sent: false, message: null });
    try {
      await api.sendPing(orderId);
      setPing({
        sending: false,
        sent: true,
        message: "핑을 보냈어요. 드리미가 확인하면 연락이 올 거예요.",
      });
    } catch (e) {
      // 서버 쿨다운(DELIVERY_034)에 걸렸다면 화면 쿨다운도 어긋난 것이므로, 다시 잠가 두고 안내만 바꾼다.
      const tooFrequent = isApiError(e) && e.code === "DELIVERY_034";
      setPing({
        sending: false,
        sent: tooFrequent,
        message: isApiError(e)
          ? e.message
          : "핑을 보내지 못했어요. 잠시 후 다시 시도해 주세요.",
      });
    }
  };

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
        {canPing && (
          <>
            <Button
              variant="outline"
              block
              disabled={ping.sending || ping.sent}
              onClick={() => void sendPing()}
            >
              <Icon name="bell" size={18} />
              {ping.sending
                ? "보내는 중…"
                : ping.sent
                  ? "핑을 보냈어요"
                  : "핑 보내기"}
            </Button>
            {ping.message && (
              <p
                className={
                  ping.sent
                    ? "text-center text-2xs text-muted"
                    : "text-center text-2xs text-status-danger"
                }
              >
                {ping.message}
              </p>
            )}
          </>
        )}
      </div>
    </BottomSheet>
  );
}

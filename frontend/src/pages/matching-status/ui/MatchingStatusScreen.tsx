import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Badge,
  Button,
  Card,
  ScreenShell,
  TextField,
  TopBar,
} from "@/shared/ui";
import {
  api,
  isApiError,
  type DreamiView,
  type MatchOfferDto,
  type OrderOfferGroupDto,
  type OrderView,
} from "@/shared/api";

const POLL_INTERVAL_MS = 2_000;

interface MatchingSnapshot {
  dreamis: DreamiView[];
  orders: OrderView[];
  groups: OrderOfferGroupDto[];
}

const EMPTY_SNAPSHOT: MatchingSnapshot = {
  dreamis: [],
  orders: [],
  groups: [],
};

function shortId(value?: string): string {
  if (!value) return "-";
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

function coords(location?: { latitude?: number; longitude?: number }): string {
  if (location?.latitude == null || location.longitude == null) return "위치 없음";
  return `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
}

function statusTone(status?: string): "info" | "warning" | "danger" | "neutral" {
  if (!status) return "neutral";
  if (status === "MATCHED") return "info";
  if (status.includes("REJECTED") || status.includes("EXPIRED")) return "danger";
  if (status === "CLOSED" || status === "WITHDRAWN") return "neutral";
  return "warning";
}

function OfferRow({ offer }: { offer: MatchOfferDto }) {
  return (
    <li className="rounded-sm bg-canvas px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-2xs text-navy-900" title={offer.offerId}>
          {shortId(offer.offerId)}
        </span>
        <Badge tone={statusTone(offer.status)}>{offer.status ?? "UNKNOWN"}</Badge>
      </div>
      <p className="pt-1 font-mono text-2xs text-muted" title={offer.dreamiId}>
        dreami {shortId(offer.dreamiId)}
      </p>
    </li>
  );
}

function GroupCard({ group, label }: { group: OrderOfferGroupDto; label?: string }) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          {label && <p className="text-2xs font-semibold text-teal-700">{label}</p>}
          <p className="truncate font-mono text-sm font-semibold" title={group.orderId}>
            {group.orderId ?? "orderId 없음"}
          </p>
        </div>
        <Badge tone={statusTone(group.status)}>{group.status ?? "UNKNOWN"}</Badge>
      </div>
      <div className="flex items-center justify-between text-2xs text-muted">
        <span>오퍼 {group.offers?.length ?? 0}개</span>
        <span>재매칭 {group.rematchRequired ? "필요" : "불필요"}</span>
      </div>
      {group.offers?.length ? (
        <ul className="flex flex-col gap-2">
          {group.offers.map((offer, index) => (
            <OfferRow key={offer.offerId ?? index} offer={offer} />
          ))}
        </ul>
      ) : (
        <p className="text-2xs text-muted">생성된 오퍼가 없습니다.</p>
      )}
    </Card>
  );
}

export function MatchingStatusScreen() {
  const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<MatchingSnapshot>(EMPTY_SNAPSHOT);
  const [trackedGroup, setTrackedGroup] = useState<OrderOfferGroupDto | null>(null);
  const [trackedOrderId, setTrackedOrderId] = useState("");
  const [orderIdInput, setOrderIdInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trackedError, setTrackedError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const refresh = useCallback(async (initial = false) => {
    if (initial) setLoading(true);
    else setRefreshing(true);

    try {
      const [dreamiResponse, orderResponse] = await Promise.all([
        api.waitingDreamis(),
        api.waitingOrders(),
      ]);
      const dreamis = dreamiResponse.result ?? [];
      const orders = orderResponse.result ?? [];
      const groupResults = await Promise.allSettled(
        orders.flatMap((order) =>
          order.orderId ? [api.getOrderOfferGroup(order.orderId)] : [],
        ),
      );
      const groups = groupResults.flatMap((result) =>
        result.status === "fulfilled" && result.value.result
          ? [result.value.result]
          : [],
      );

      setSnapshot({ dreamis, orders, groups });
      setUpdatedAt(new Date());
      setError(null);
    } catch (e) {
      setError(isApiError(e) ? e.message : "매칭 상태를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void refresh(true);
    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  useEffect(() => {
    if (!trackedOrderId) return;

    const refreshTrackedGroup = async () => {
      try {
        const { result } = await api.getOrderOfferGroup(trackedOrderId);
        setTrackedGroup(result ?? null);
        setTrackedError(result ? null : "매칭 그룹이 아직 생성되지 않았습니다.");
      } catch (e) {
        setTrackedGroup(null);
        setTrackedError(
          isApiError(e) ? e.message : "해당 주문의 매칭 그룹을 찾지 못했습니다.",
        );
      }
    };

    const timer = window.setInterval(
      () => void refreshTrackedGroup(),
      POLL_INTERVAL_MS,
    );
    return () => window.clearInterval(timer);
  }, [trackedOrderId]);

  const inspectOrder = async () => {
    const orderId = orderIdInput.trim();
    if (!orderId) return;
    setTrackedOrderId(orderId);
    setTrackedError(null);
    try {
      const { result } = await api.getOrderOfferGroup(orderId);
      setTrackedGroup(result ?? null);
      if (!result) setTrackedError("매칭 그룹이 아직 생성되지 않았습니다.");
    } catch (e) {
      setTrackedGroup(null);
      setTrackedError(
        isApiError(e) ? e.message : "해당 주문의 매칭 그룹을 찾지 못했습니다.",
      );
    }
  };

  return (
    <ScreenShell>
      <TopBar title="매칭 상태" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-5 pt-5">
        <section className="grid grid-cols-3 gap-2">
          <Card className="p-3 text-center">
            <p className="text-xl font-bold text-navy-900">{snapshot.dreamis.length}</p>
            <p className="text-2xs text-muted">대기 드리미</p>
          </Card>
          <Card className="p-3 text-center">
            <p className="text-xl font-bold text-navy-900">{snapshot.orders.length}</p>
            <p className="text-2xs text-muted">열린 주문</p>
          </Card>
          <Card className="p-3 text-center">
            <p className="text-xl font-bold text-navy-900">{snapshot.groups.length}</p>
            <p className="text-2xs text-muted">오퍼 그룹</p>
          </Card>
        </section>

        <section className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-navy-900">2초마다 자동 갱신</p>
            <p className="text-2xs text-muted">
              {updatedAt ? `${updatedAt.toLocaleTimeString("ko-KR")} 기준` : "조회 대기 중"}
            </p>
          </div>
          <Button size="sm" variant="outline" disabled={refreshing} onClick={() => void refresh()}>
            {refreshing ? "갱신 중" : "새로고침"}
          </Button>
        </section>

        {error && <p className="text-sm text-status-danger">{error}</p>}
        {loading && <p className="py-5 text-center text-sm text-muted">매칭 상태 조회 중…</p>}

        <section className="flex flex-col gap-3">
          <h2 className="text-md font-bold text-navy-900">주문 직접 조회</h2>
          <TextField
            label="orderId"
            placeholder="주문 UUID"
            value={orderIdInput}
            onChange={(event) => setOrderIdInput(event.target.value)}
          />
          <Button block disabled={!orderIdInput.trim()} onClick={() => void inspectOrder()}>
            그룹 조회
          </Button>
          {trackedError && (
            <p className="text-sm text-status-danger">
              {trackedOrderId ? `${shortId(trackedOrderId)}: ` : ""}
              {trackedError}
            </p>
          )}
          {trackedGroup && <GroupCard group={trackedGroup} label="직접 조회 결과" />}
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-md font-bold text-navy-900">매칭 그룹</h2>
          {snapshot.groups.length ? (
            snapshot.groups.map((group, index) => (
              <GroupCard key={group.orderId ?? index} group={group} />
            ))
          ) : (
            !loading && <Card className="text-sm text-muted">현재 열린 매칭 그룹이 없습니다.</Card>
          )}
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-md font-bold text-navy-900">드리미 상태</h2>
          {snapshot.dreamis.length ? (
            snapshot.dreamis.map((dreami, index) => (
              <Card key={dreami.dreamiId ?? index} className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-mono text-sm font-semibold" title={dreami.dreamiId}>
                    {dreami.dreamiId ?? "dreamiId 없음"}
                  </p>
                  <p className="pt-1 text-2xs text-muted">{coords(dreami.location)}</p>
                </div>
                <Badge tone={statusTone(dreami.status)}>{dreami.status ?? "UNKNOWN"}</Badge>
              </Card>
            ))
          ) : (
            !loading && <Card className="text-sm text-muted">등록된 대기 드리미가 없습니다.</Card>
          )}
        </section>
      </main>
    </ScreenShell>
  );
}

import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BottomNav, ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { useRoleSwitch } from "@/shared/lib/role/useRoleSwitch";
import { useRoleLocked } from "@/shared/lib/role/useRoleLocked";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import { useDreamiOrderStore } from "@/shared/store/dreamiOrderStore";
import { ActivityItem } from "./ActivityItem";
import { FilterChips } from "./FilterChips";
import {
  toActivityRecordFromDreamiOrder,
  toActivityRecordFromOrder,
  type ActivityFilter,
  type ActivityRecord,
} from "./records";

/**
 * 활동 내역 리스트 화면(Figma node 191:266, 191:1118).
 * 부르미 탭은 부르미 API(getBoormiOrders), 드리미 탭은 드리미 API(getDreamiOrders)를 사용한다.
 */
export function ActivityScreen() {
  const navigate = useNavigate();
  const { role } = useRole();
  const { onRoleChange, pending, error: roleError } = useRoleSwitch();
  const [filter, setFilter] = useState<ActivityFilter>("전체");
  const { locked: roleLocked, reason: roleLockReason } = useRoleLocked();
  const refreshUser = useSessionStore((s) => s.refreshUser);

  // 토글이 보이는 화면에 들어올 때마다 수행 중인 역할을 최신화해 잠금 상태를 맞춘다.
  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);
  const isDriver = role === "드리미";

  // 드리미(실제 API) 소스
  const deliveries = useDreamiOrderStore((s) => s.deliveries);
  const dreamiLoading = useDreamiOrderStore((s) => s.loading);
  const dreamiError = useDreamiOrderStore((s) => s.error);
  const loadDreami = useDreamiOrderStore((s) => s.load);

  // 부르미(실제 API) 소스
  const orders = useBoormiOrderStore((s) => s.orders);
  const loading = useBoormiOrderStore((s) => s.loading);
  const boormiError = useBoormiOrderStore((s) => s.error);
  const load = useBoormiOrderStore((s) => s.load);

  // 역할 탭 진입 시 각자의 전체 목록을 조회(필터가 클라이언트 필터링이라 일부만 있으면
  // 아직 안 불러온 항목이 누락돼 보일 수 있어 전체를 가져온다).
  useEffect(() => {
    if (isDriver) loadDreami();
    else load();
  }, [isDriver, load, loadDreami]);

  const records: ActivityRecord[] = useMemo(
    () =>
      isDriver
        ? deliveries.map(toActivityRecordFromDreamiOrder)
        : orders.map(toActivityRecordFromOrder),
    [isDriver, deliveries, orders],
  );

  const visible =
    filter === "전체" ? records : records.filter((r) => r.filter === filter);

  /**
   * 진행 중인 건은 실시간 상세로 보낸다(드리미는 실 추적 페이지, 부르미는 mock 상세).
   * 드리미의 완료/취소 건은 드림상세(activityDetailDriver)로 보낸다.
   */
  const detailPath = (record: ActivityRecord): string | null => {
    if (isDriver) {
      return record.filter === "진행중"
        ? `${ROUTES.deliveryTrack}?orderId=${record.id}`
        : `${ROUTES.activityDetailDriver}?id=${record.id}`;
    }
    if (record.filter === "진행중")
      return `${ROUTES.activityDetail}?status=진행중&id=${record.id}`;
    return `${ROUTES.activityDetail}?status=완료&id=${record.id}`;
  };

  return (
    <ScreenShell footer={<BottomNav />}>
      <TopBar title="활동" actions={["profile"]} />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={onRoleChange}
          disabled={roleLocked || pending}
        />

        {roleError && (
          <p className="text-2xs text-status-danger">{roleError}</p>
        )}
        {!roleError && roleLocked && roleLockReason && (
          <p className="text-2xs text-navy-500">{roleLockReason}</p>
        )}

        <FilterChips value={filter} onChange={setFilter} />

        <p className="text-xs text-muted">
          {isDriver ? "수행한 배달" : "요청한 배달"} · 총 {records.length}건
        </p>

        {isDriver && dreamiLoading && records.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted">불러오는 중…</p>
        ) : isDriver && dreamiError ? (
          <p className="py-10 text-center text-sm text-status-danger">
            {dreamiError}
          </p>
        ) : !isDriver && loading && records.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted">불러오는 중…</p>
        ) : !isDriver && boormiError ? (
          <p className="py-10 text-center text-sm text-status-danger">
            {boormiError}
          </p>
        ) : visible.length > 0 ? (
          <div className="flex flex-col gap-3">
            {visible.map((record) => {
              const path = detailPath(record);
              return (
                <ActivityItem
                  key={record.id}
                  record={record}
                  earned={isDriver}
                  onClick={path ? () => navigate(path) : undefined}
                />
              );
            })}
          </div>
        ) : (
          <p className="py-10 text-center text-sm text-muted">
            해당하는 내역이 없어요.
          </p>
        )}
      </main>
    </ScreenShell>
  );
}

import { useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { BottomNav, ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { useRoleSwitch } from "@/shared/lib/role/useRoleSwitch";
import { useRoleLocked } from "@/shared/lib/role/useRoleLocked";
import { useInfiniteScrollSentinel } from "@/shared/lib/scroll/useInfiniteScrollSentinel";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import { useDreamiOrderStore } from "@/shared/store/dreamiOrderStore";
import { MATCHING_ORDER_CDS } from "@/shared/store/boormiOrderAdapter";
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
 * 부르미 탭은 부르미 API(getBoormiOrders), 드리미 탭은 드리미 API(getDreamiOrders)를 커서 기반으로
 * 페이지네이션 조회한다. 필터 탭(전체/진행중/완료/취소)도 서버에 그대로 넘겨 서버가 걸러 반환하고,
 * 목록 맨 아래 sentinel이 보이면 다음 페이지를 이어 받는다(무한 스크롤).
 */
export function ActivityScreen() {
  const navigate = useNavigate();
  const { role } = useRole();
  const { onRoleChange, pending, error: roleError } = useRoleSwitch();
  const { locked: roleLocked, reason: roleLockReason } = useRoleLocked();
  const refreshUser = useSessionStore((s) => s.refreshUser);

  // 토글이 보이는 화면에 들어올 때마다 수행 중인 역할을 최신화해 잠금 상태를 맞춘다.
  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);
  const isDriver = role === "드리미";

  // 드리미(실제 API) 소스
  const deliveries = useDreamiOrderStore((s) => s.deliveries);
  const dreamiFilter = useDreamiOrderStore((s) => s.filter);
  const dreamiLoading = useDreamiOrderStore((s) => s.loading);
  const dreamiLoadingMore = useDreamiOrderStore((s) => s.loadingMore);
  const dreamiHasNext = useDreamiOrderStore((s) => s.hasNext);
  const dreamiError = useDreamiOrderStore((s) => s.error);
  const dreamiCounts = useDreamiOrderStore((s) => s.counts);
  const loadDreami = useDreamiOrderStore((s) => s.load);
  const loadMoreDreami = useDreamiOrderStore((s) => s.loadMore);
  const loadDreamiCounts = useDreamiOrderStore((s) => s.loadCounts);

  // 부르미(실제 API) 소스
  const orders = useBoormiOrderStore((s) => s.activityOrders);
  const boormiFilter = useBoormiOrderStore((s) => s.activityFilter);
  const loading = useBoormiOrderStore((s) => s.activityLoading);
  const loadingMore = useBoormiOrderStore((s) => s.activityLoadingMore);
  const boormiHasNext = useBoormiOrderStore((s) => s.activityHasNext);
  const boormiError = useBoormiOrderStore((s) => s.activityError);
  const boormiCounts = useBoormiOrderStore((s) => s.activityCounts);
  const load = useBoormiOrderStore((s) => s.loadActivityFirstPage);
  const loadMoreBoormi = useBoormiOrderStore((s) => s.loadActivityMore);
  const loadBoormiCounts = useBoormiOrderStore((s) => s.loadActivityCounts);

  const filter: ActivityFilter = isDriver ? dreamiFilter : boormiFilter;
  const hasNext = isDriver ? dreamiHasNext : boormiHasNext;
  const counts = isDriver ? dreamiCounts : boormiCounts;

  // 역할 탭 진입 시 "전체" 탭 첫 페이지 + 탭별 개수를 새로 받는다.
  useEffect(() => {
    if (isDriver) {
      void loadDreami("전체");
      void loadDreamiCounts();
    } else {
      void load("전체");
      void loadBoormiCounts();
    }
  }, [isDriver, load, loadBoormiCounts, loadDreami, loadDreamiCounts]);

  const onFilterChange = (next: ActivityFilter) => {
    if (isDriver) void loadDreami(next);
    else void load(next);
  };

  const onLoadMore = isDriver ? loadMoreDreami : loadMoreBoormi;
  const sentinelRef = useInfiniteScrollSentinel({ hasNext, onLoadMore });

  const records: ActivityRecord[] = useMemo(
    () =>
      isDriver
        ? deliveries.map(toActivityRecordFromDreamiOrder)
        : orders.map(toActivityRecordFromOrder),
    [isDriver, deliveries, orders],
  );

  // counts는 목록과 별개의 요청이라 늦게 도착할 수 있다(혹은 실패해 영구히 안 올 수도 있다) — 그 경우
  // 로드된 페이지 크기(records.length)를 총 개수인 것처럼 보여주면 실제보다 작게 표시되므로, 확정된
  // 값이 오기 전까진 "…"로 둔다.
  const totalCount = counts ? counts[filter] : null;

  /**
   * 진행 중인 건은 실시간 상세로 보낸다(드리미는 실 추적 페이지, 부르미도 실 추적 페이지).
   * 단, 부르미의 "진행중"은 아직 드리미가 매칭되지 않은 상태(MATCHING/PENDING_BOORMI_CONFIRMATION)도
   * 포함하는데, 이땐 실 추적 화면이 조회할 Delivery row 자체가 없어 DELIVERY_NOT_FOUND로 막힌다.
   * 홈 화면(SenderPanel)과 동일하게 매칭 전이면 매칭 대기 화면으로 보낸다.
   * 드리미의 완료/취소 건은 드림상세(activityDetailDriver)로 보낸다.
   */
  const detailPath = (record: ActivityRecord): string | null => {
    if (isDriver) {
      return record.filter === "진행중"
        ? `${ROUTES.deliveryTrack}?orderId=${record.id}`
        : `${ROUTES.activityDetailDriver}?id=${record.id}`;
    }
    if (record.filter === "진행중") {
      return MATCHING_ORDER_CDS.has(record.orderCd)
        ? `${ROUTES.matching}?orderId=${record.id}`
        : `${ROUTES.deliveryDetail}?orderId=${record.id}`;
    }
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

        <FilterChips value={filter} onChange={onFilterChange} />

        <p className="text-xs text-muted">
          {isDriver ? "수행한 배달" : "요청한 배달"} · 총 {totalCount ?? "…"}건
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
        ) : records.length > 0 ? (
          <div className="flex flex-col gap-3">
            {records.map((record) => {
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
            {/* 무한 스크롤 sentinel — 다음 페이지가 있을 때만 관찰 대상이 된다. */}
            <div ref={sentinelRef} />
            {(isDriver ? dreamiLoadingMore : loadingMore) && (
              <p className="py-3 text-center text-xs text-muted">
                더 불러오는 중…
              </p>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center py-10">
            <img
              src={isDriver ? "/dreami-no-dream.png" : "/boormi-no-boorm.png"}
              alt=""
              className="h-20 w-20 object-contain"
            />
            <p className="text-center text-sm text-muted">
              해당하는 내역이 없어요.
            </p>
          </div>
        )}
      </main>
    </ScreenShell>
  );
}

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { BottomNav, ScreenShell, SegmentedToggle, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import type { Role } from "@/shared/lib/role/RoleContext";
import { ActivityItem } from "./ActivityItem";
import { FilterChips } from "./FilterChips";
import { DRIVER_RECORDS, SENDER_RECORDS, type ActivityFilter } from "./records";

/**
 * 활동 내역 리스트 화면(Figma node 191:266, 191:1118).
 * 상단 토글로 부르미(요청한 배달)·드리미(수행한 배달) 내역을 같은 화면에서 전환합니다.
 */
export function ActivityScreen() {
  const navigate = useNavigate();
  const { role, setRole } = useRole();
  const [filter, setFilter] = useState<ActivityFilter>("전체");
  const [tab, setTab] = useState("activity");

  const isDriver = role === "드리미";
  const records = isDriver ? DRIVER_RECORDS : SENDER_RECORDS;
  const visible =
    filter === "전체" ? records : records.filter((r) => r.filter === filter);

  return (
    <ScreenShell>
      <TopBar title="활동" actions={["search", "profile"]} />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <SegmentedToggle
          options={["부르미", "드리미"]}
          value={role}
          onChange={(value) => setRole(value as Role)}
        />

        <FilterChips value={filter} onChange={setFilter} />

        <p className="text-xs text-muted">
          {isDriver ? "수행한 배달 · 총 8건" : "요청한 배달 · 총 12건"}
        </p>

        {visible.length > 0 ? (
          <div className="flex flex-col gap-3">
            {visible.map((record) => (
              <ActivityItem
                key={record.id}
                record={record}
                earned={isDriver}
                onClick={() =>
                  navigate(
                    `${ROUTES.activityDetail}?status=${record.filter === "진행중" ? "진행중" : "완료"}`,
                  )
                }
              />
            ))}
          </div>
        ) : (
          <p className="py-10 text-center text-sm text-muted">
            해당하는 내역이 없어요.
          </p>
        )}
      </main>

      <div className="pt-4">
        <BottomNav active={tab} onChange={setTab} />
      </div>
    </ScreenShell>
  );
}

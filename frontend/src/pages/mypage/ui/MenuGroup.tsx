import { Badge, Card, Icon } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { MenuItem } from "./menus";

export interface MenuGroupProps {
  title: string;
  items: MenuItem[];
}

/** 섹션 제목 + 구분선으로 이어진 메뉴 카드. */
export function MenuGroup({ title, items }: MenuGroupProps) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-2xs text-muted">{title}</p>
      <Card className="flex flex-col p-0">
        {items.map((item, index) => (
          <button
            key={item.label}
            type="button"
            onClick={item.onClick}
            className={cn(
              "flex items-center gap-2 px-4 py-3.5 text-left",
              index > 0 && "border-t border-line",
            )}
          >
            <span
              className={cn(
                "flex-1 text-md font-semibold",
                item.muted ? "text-muted" : "text-navy-900",
              )}
            >
              {item.label}
            </span>
            {item.badge ? (
              <Badge className="bg-teal-500 text-white">{item.badge}</Badge>
            ) : (
              <Icon name="back" size={14} className="rotate-180 text-muted" />
            )}
          </button>
        ))}
      </Card>
    </div>
  );
}

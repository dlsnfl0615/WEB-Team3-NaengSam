import { useNavigate } from "react-router-dom";
import { BottomNav, Button, Card, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useWalletStore } from "@/shared/store/walletStore";
import { HistoryItem } from "./HistoryItem";

/**
 * 내 지갑 화면(Figma node 191:1392).
 * 보유 포인트와 머니(드리미 수익) 잔액, 최근 입출금 내역을 보여줍니다.
 */
export function WalletScreen() {
  const navigate = useNavigate();
  const points = useWalletStore((s) => s.points);
  const money = useWalletStore((s) => s.money);
  const transactions = useWalletStore((s) => s.transactions);

  return (
    <ScreenShell footer={<BottomNav />}>
      <TopBar title="내 지갑" actions={["profile"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card variant="hero" className="flex flex-col gap-1">
          <div className="flex items-center justify-between">
            <span className="text-2xs opacity-70">보유 포인트</span>
            <span className="flex size-6 items-center justify-center rounded-pill bg-teal-50 text-2xs font-bold text-teal-700">
              P
            </span>
          </div>
          <p className="text-3xl font-bold tracking-[-0.6px]">
            {points.toLocaleString()} P
          </p>
          <p className="text-2xs opacity-70">배송 결제에 사용 가능</p>
          <Button
            block
            arrow
            className="mt-3"
            onClick={() => navigate(ROUTES.pointCharge)}
          >
            포인트 충전
          </Button>
        </Card>

        <Card className="flex flex-col gap-1">
          <div className="flex items-center justify-between">
            <span className="text-2xs text-muted">머니 (드리미 수익)</span>
            <span className="flex size-6 items-center justify-center rounded-pill bg-teal-50 text-2xs font-bold text-teal-700">
              ₩
            </span>
          </div>
          <p className="text-2xl font-bold tracking-[-0.5px] text-navy-900">
            ₩{money.toLocaleString()}
          </p>
          <div className="mt-2 flex gap-3">
            <Button
              variant="outline"
              block
              className="border-transparent bg-track"
            >
              출금하기
            </Button>
            <Button
              variant="navy"
              block
              arrow
              onClick={() => navigate(`${ROUTES.pointCharge}?mode=convert`)}
            >
              포인트로 전환
            </Button>
          </div>
        </Card>

        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <p className="text-2xs text-muted">최근 내역</p>
            <button
              type="button"
              className="text-sm text-teal-700"
              onClick={() => navigate(ROUTES.earnings)}
            >
              전체 보기
            </button>
          </div>
          <div className="flex flex-col gap-3">
            {transactions.map((history) => (
              <HistoryItem key={history.id} history={history} />
            ))}
          </div>
        </div>
      </main>
    </ScreenShell>
  );
}

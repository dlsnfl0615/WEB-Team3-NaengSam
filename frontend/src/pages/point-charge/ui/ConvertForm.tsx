import { Button, Card } from "@/shared/ui";

/** 머니를 포인트로 전환하는 본문. */
export function ConvertForm() {
  return (
    <>
      <h1 className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        머니를 포인트로
        <br />
        전환할까요?
      </h1>
      <span className="h-[9px] w-3/4 rounded-[5px] bg-track" />

      <Card className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted">보유 머니</span>
          <span className="text-md font-bold text-navy-900">₩58,500</span>
        </div>

        <div className="flex flex-col items-center gap-0.5 rounded-md bg-track py-4">
          <span className="text-2xs text-muted">전환 전 포인트</span>
          <span className="text-xl font-bold text-navy-900">0P</span>
        </div>

        <div className="flex flex-col items-center gap-0.5 rounded-md bg-teal-50 py-4">
          <span className="text-2xs text-muted">전환 후 포인트</span>
          <span className="text-xl font-bold text-teal-700">+10,000 P</span>
        </div>
      </Card>

      <Card className="flex flex-col items-center gap-0.5">
        <span className="text-2xs text-muted">전환 비율</span>
        <span className="text-base font-bold text-navy-900">
          1 : 1 (수수료 무료)
        </span>
      </Card>

      <p className="text-2xs text-muted">
        ※ 포인트는 배송 결제에만 사용 가능하며 머니로 되돌릴 수 없어요.
      </p>

      <Button variant="navy" block>
        10,000P로 전환하기
      </Button>
    </>
  );
}

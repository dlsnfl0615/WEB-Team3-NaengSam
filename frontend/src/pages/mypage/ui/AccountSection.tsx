import { Badge, Button, Card, IconChip, InfoRow } from "@/shared/ui";

export interface AccountSectionProps {
  /** 현금화 계좌 등록 여부(Figma node 191:1574 / 191:1657). */
  registered: boolean;
  onChange: () => void;
}

/** 현금화 계좌 영역. 등록된 계좌가 있을 때만 계좌 카드를 보여줍니다. */
export function AccountSection({
  registered,
  onChange,
}: AccountSectionProps) {
  if (!registered) return null;

  return (
    <div className="flex flex-col gap-2">
      <p className="text-2xs text-muted">현금화 계좌</p>
      <Card className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <IconChip name="bank" size={36} />
          <div className="flex-1">
            <p className="text-md font-bold text-navy-900">국민은행</p>
            <p className="text-2xs text-muted">예금주 김드림</p>
          </div>
          <Badge className="bg-teal-500 text-white">인증됨</Badge>
        </div>

        <div className="border-t border-track pt-3">
          <InfoRow label="계좌번호">1234-**-**7890</InfoRow>
        </div>

        <div className="flex gap-3">
          <Button
            variant="outline"
            block
            className="border-transparent bg-track"
            onClick={onChange}
          >
            계좌 변경
          </Button>
          <Button variant="navy" block>
            출금하기
          </Button>
        </div>
      </Card>
    </div>
  );
}

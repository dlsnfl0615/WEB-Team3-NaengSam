import { useNavigate } from 'react-router-dom'
import { Button, Card, Icon, ScreenShell, TopBar } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

/**
 * 본인인증 및 등록 화면(Figma node 21:41).
 * 드리미 등록을 위한 신분증/사원증 업로드 안내 + 본인인증 진입.
 */
export function VerifyScreen() {
  const navigate = useNavigate()

  return (
    <ScreenShell>
      <TopBar
        title="본인인증 및 등록"
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-2">
        {/* 브랜드/안내 이미지 자리표시 */}
        <div className="flex h-[138px] items-center justify-center rounded-md border border-dashed border-line text-sm text-muted">
          브랜드 / 드리미 안내 이미지
        </div>

        <h2 className="text-lg font-bold leading-snug tracking-[-0.4px] text-navy-900">
          지금 바로 드리미로
          <br />
          등록하시겠어요?
        </h2>

        {/* 본인인증 단계 카드 */}
        <Card variant="surface" className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <span className="h-7 w-7 shrink-0 rounded-pill bg-teal-50" />
            <div className="flex flex-col">
              <span className="text-base font-bold text-navy-900">본인인증</span>
              <span className="text-xs text-muted">1단계 / 2단계</span>
            </div>
          </div>

          {/* 사진 업로드 자리표시 */}
          <div className="flex h-32 flex-col items-center justify-center gap-1 rounded-md border border-dashed border-line text-center text-muted">
            <Icon name="camera" size={24} className="text-muted" />
            <span className="text-xs">사진 업로드</span>
            <span className="text-xs">신분증 / 사원증</span>
          </div>
        </Card>

        {/* 안내 카드 */}
        <Card variant="accent" className="flex flex-col gap-1.5">
          <p className="text-sm font-bold text-navy-900">등록 안내</p>
          <p className="text-xs text-muted">
            본인인증을 완료하면 드리미로 활동할 수 있어요.
          </p>
        </Card>

        <div className="mt-auto flex flex-col items-center gap-3 pt-2">
          <Button variant="navy" block onClick={() => navigate(ROUTES.home)}>
            드리미 등록을 위한 본인인증
          </Button>
          <button
            type="button"
            className="text-sm text-muted hover:text-navy-900"
            onClick={() => navigate(ROUTES.home)}
          >
            나중에 등록 (부르미로 시작)
          </button>
        </div>
      </main>
    </ScreenShell>
  )
}

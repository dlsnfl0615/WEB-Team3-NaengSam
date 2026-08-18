import { useNavigate } from 'react-router-dom'
import { useBackOrHome } from '@/shared/lib/navigation/useBackOrHome'
import { Button, Card, ScreenShell, TopBar } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

/**
 * 드리미 인증을 이미 신청했지만 아직 심사 중(REQUESTED)일 때 역할 토글에서 진입하는 안내 화면.
 * 최초 제출 직후의 토스트(HomeScreen)와 별개로, 그 이후 다시 드리미로 전환을 시도할 때마다 보여준다.
 */
export function DreamiPendingScreen() {
  const backOrHome = useBackOrHome()
  const navigate = useNavigate()

  return (
    <ScreenShell>
      <TopBar title="본인인증 진행 상황" onBack={backOrHome} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-2">
        <img
          src="/dreami-review-pending.png"
          alt="심사 접수 안내"
          className="mx-auto h-[160px] w-auto"
        />

        <h2 className="text-center text-lg font-bold leading-snug tracking-[-0.4px] text-navy-900">
          심사가 접수됐어요
        </h2>

        <Card variant="accent" className="flex flex-col gap-1.5">
          <p className="text-sm font-bold text-navy-900">심사 안내</p>
          <p className="text-xs text-muted">
            제출해주신 신분증·범죄이력회보서를 확인하고 있어요. 승인되면 드리미로 활동할 수 있어요.
          </p>
        </Card>

        <div className="mt-auto flex flex-col items-center gap-3 pt-2">
          <Button variant="navy" block onClick={() => navigate(ROUTES.verify)}>
            드리미 인증 사진 다시 제출하기
          </Button>
          <button
            type="button"
            className="text-sm text-muted hover:text-navy-900"
            onClick={() => navigate(ROUTES.home, { replace: true })}
          >
            부르미로 돌아가기
          </button>
        </div>
      </main>
    </ScreenShell>
  )
}

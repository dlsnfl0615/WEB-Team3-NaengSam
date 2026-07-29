import { useNavigate } from 'react-router-dom'
import { Button, Card, Icon, ScreenShell } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

/**
 * 온보딩 화면(Figma node 21:108).
 * 서비스 소개 + 역할(부르미/드리미) 안내 + 시작하기/로그인 진입.
 */
export function OnboardingScreen() {
  const navigate = useNavigate()

  return (
    <ScreenShell>
      <main className="flex flex-1 flex-col items-center pt-10">
        {/* 로고 자리표시 */}
        <div className="flex h-[62px] w-[128px] items-center justify-center rounded-md border border-dashed border-line text-sm text-muted">
          LOGO
        </div>

        {/* 타이틀 */}
        <h1 className="mt-6 text-center text-xl font-bold leading-snug tracking-[-0.4px] text-navy-900">
          사내 배송의
          <br />
          새로운 기준, <span className="text-muted">쉼부름</span>
        </h1>

        {/* 역할 카드 */}
        <div className="mt-8 grid w-full grid-cols-2 gap-3">
          <Card
            variant="accent"
            className="flex h-32 flex-col items-center justify-center gap-2"
          >
            <Icon name="transfer" size={28} className="text-teal-700" />
            <span className="text-base font-bold text-navy-900">부르미</span>
          </Card>
          <Card
            variant="accent"
            className="flex h-32 flex-col items-center justify-center gap-2"
          >
            <Icon name="package" size={28} className="text-teal-700" />
            <span className="text-base font-bold text-navy-900">드리미</span>
          </Card>
        </div>

        {/* CTA */}
        <div className="mt-8 flex w-full flex-col gap-3">
          <Button variant="navy" block onClick={() => navigate(ROUTES.signup)}>
            시작하기
          </Button>
          <Button
            variant="outline"
            block
            onClick={() => navigate(ROUTES.login)}
          >
            로그인
          </Button>
        </div>

        <p className="mt-4 text-sm text-muted">서비스 가이드 · 고객지원</p>
      </main>
    </ScreenShell>
  )
}

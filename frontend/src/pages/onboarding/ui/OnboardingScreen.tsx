import { useNavigate } from 'react-router-dom'
import { Button, Card, ScreenShell } from '@/shared/ui'
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
        <img
          src="/symboorm-logo.png"
          alt="쉼부름"
          className="h-[100px] w-[100px] object-contain"
        />

        {/* 타이틀 */}
        <h1 className="mt-6 text-center text-xl font-bold leading-snug tracking-[-0.4px] text-navy-900">
          간단한 심부름 <span className="text-muted">쉼,부름</span> 하나로!
        </h1>

        {/* 역할 카드 */}
        <div className="mt-8 grid w-full grid-cols-2 gap-3">
          <Card
            variant="accent"
            className="flex h-32 flex-col items-center justify-center gap-0"
          >
            <img
              src="/boormi-main.png"
              alt=""
              className="h-20 w-20 object-contain"
            />
            <span className="text-base font-bold text-navy-900">부르미</span>
          </Card>
          <Card
            variant="accent"
            className="flex h-32 flex-col items-center justify-center gap-0"
          >
            <img
              src="/dreami-main.png"
              alt=""
              className="h-20 w-20 object-contain"
            />
            <span className="text-base font-bold text-navy-900">드리미</span>
          </Card>
        </div>

        {/* CTA — 주 동선은 로그인 하나로 두고, 회원가입은 아래 링크로 내린다. */}
        <div className="mt-8 flex w-full flex-col gap-3">
          <Button variant="navy" block onClick={() => navigate(ROUTES.login)}>
            로그인
          </Button>
        </div>

        {/* 하단 링크(로그인 화면과 같은 패턴) */}
        <div className="mt-4 flex items-center justify-center gap-2 text-sm text-muted">
          <span>아직 회원이 아니신가요?</span>
          <button
            type="button"
            className="font-semibold hover:text-navy-900"
            onClick={() => navigate(ROUTES.signup)}
          >
            회원가입
          </button>
        </div>
      </main>
    </ScreenShell>
  )
}

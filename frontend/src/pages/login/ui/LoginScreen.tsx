import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, ScreenShell, TextField } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'
import { useRole } from '@/shared/lib/role/useRole'
import { resolveLandingRoute } from '@/shared/lib/role/resolveLandingRoute'
import { isApiError } from '@/shared/api'
import { isEmail, VALIDATION_MESSAGE } from '@/shared/lib/validation'
import { clearForcedLogout, hasForcedLogout } from '@/shared/lib'

/**
 * 로그인 화면(Figma node 21:91).
 * 이메일/비밀번호 로그인 + 회원가입 진입.
 */
export function LoginScreen() {
  const navigate = useNavigate()
  const login = useSessionStore((s) => s.login)
  const { setRole } = useRole()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 세션이 끊겨 밀려왔는지. 표식은 로그인 성공 시에만 지우므로 렌더 중 읽어도 안전하다.
  const forcedLogout = hasForcedLogout()

  const emailError =
    email.trim() && !isEmail(email) ? VALIDATION_MESSAGE.email : undefined
  const canSubmit = isEmail(email) && !!password.trim() && !submitting

  const onLogin = async () => {
    setError(null)
    setSubmitting(true)
    try {
      const user = await login({ email, password })
      clearForcedLogout()
      setRole(user.activeRole === 'DREAMI' ? '드리미' : '부르미')
      // 진행 중인 배달이 있으면 홈이 아니라 그 화면으로 곧바로 복귀시킨다.
      navigate(resolveLandingRoute(user), { replace: true })
    } catch (e) {
      setError(
        isApiError(e) ? e.message : '로그인에 실패했어요. 다시 시도해주세요.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ScreenShell>
      <main className="flex flex-1 flex-col pt-8">
        {/* 로고 + 타이틀 */}
        <div className="flex flex-col items-center">
          <div className="flex h-[82px] w-[106px] items-center justify-center rounded-md border border-dashed border-line text-sm text-muted">
            LOGO
          </div>
          <h1 className="mt-4 text-xl font-bold tracking-[-0.4px] text-navy-900">
            쉼, 부름
          </h1>
        </div>

        {/* 강제 로그아웃 안내 — 세션 교체와 유휴 만료를 백엔드가 구분해 주지 않아 두 원인을 함께 적는다. */}
        {forcedLogout && (
          <div
            role="status"
            className="mt-8 rounded-md bg-status-warning-50 px-4 py-3 text-status-warning"
          >
            <p className="text-sm font-bold">세션이 종료되어 로그아웃되었어요</p>
            <p className="mt-1 text-xs leading-5">
              다른 기기에서 로그인했거나, 오랫동안 사용하지 않아 세션이
              만료되었어요. 다시 로그인해 주세요.
            </p>
          </div>
        )}

        {/* 폼 */}
        <div className="mt-10 flex flex-col gap-4">
          <TextField
            label="이메일"
            type="email"
            placeholder="email@example.com"
            maxLength={254}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={emailError}
          />
          <TextField
            label="비밀번호"
            type="password"
            placeholder="비밀번호를 입력하세요"
            maxLength={20}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        {error && <p className="mt-4 text-sm text-status-danger">{error}</p>}

        <Button
          variant="navy"
          block
          className="mt-6"
          disabled={!canSubmit}
          onClick={onLogin}
        >
          {submitting ? '로그인 중…' : '로그인'}
        </Button>

        {/* 하단 링크 */}
        <div className="mt-4 flex items-center justify-center gap-2 text-sm text-muted">
          <button
            type="button"
            className="hover:text-navy-900"
            onClick={() => navigate(ROUTES.signup)}
          >
            회원가입
          </button>
        </div>
      </main>
    </ScreenShell>
  )
}

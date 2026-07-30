import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, ScreenShell, TextField } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'
import { isEmail, VALIDATION_MESSAGE } from '@/shared/lib/validation'

/**
 * 로그인 화면(Figma node 21:91).
 * 이메일/비밀번호 로그인 + 비밀번호 찾기/회원가입 진입.
 */
export function LoginScreen() {
  const navigate = useNavigate()
  const login = useSessionStore((s) => s.login)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const emailError =
    email.trim() && !isEmail(email) ? VALIDATION_MESSAGE.email : undefined
  const canSubmit = isEmail(email) && !!password.trim() && !submitting

  const onLogin = async () => {
    setSubmitting(true)
    try {
      await login({ email, password })
      navigate(ROUTES.home, { replace: true })
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

        {/* 폼 */}
        <div className="mt-10 flex flex-col gap-4">
          <TextField
            label="이메일"
            type="email"
            placeholder="email@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={emailError}
          />
          <TextField
            label="비밀번호"
            type="password"
            placeholder="비밀번호를 입력하세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

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
          <button type="button" className="hover:text-navy-900">
            비밀번호 찾기
          </button>
          <span aria-hidden>｜</span>
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

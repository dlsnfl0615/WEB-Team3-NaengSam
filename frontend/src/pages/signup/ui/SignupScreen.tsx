import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, ScreenShell, TextField, TopBar } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

/**
 * 회원가입 화면(Figma node 21:62).
 * 이름/생년월일/전화번호(인증)/이메일/비밀번호 입력 + 약관 동의.
 */
export function SignupScreen() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    name: '',
    birth: '',
    phone: '',
    code: '',
    email: '',
    password: '',
  })
  const [agreed, setAgreed] = useState(false)

  const set = (key: keyof typeof form) => (value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  return (
    <ScreenShell>
      <TopBar title="회원가입" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col pt-2">
        <h2 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          쉼, 부름 <span className="text-muted">시작하기</span>
        </h2>

        <div className="mt-6 flex flex-col gap-4">
          <TextField
            label="이름"
            placeholder="이름을 입력해 주세요"
            value={form.name}
            onChange={(e) => set('name')(e.target.value)}
          />
          <TextField
            label="생년월일"
            placeholder="2000.1.1"
            value={form.birth}
            onChange={(e) => set('birth')(e.target.value)}
          />

          {/* 전화번호 + 인증발송 */}
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <TextField
                label="전화번호"
                type="tel"
                placeholder="010-0000-0000"
                value={form.phone}
                onChange={(e) => set('phone')(e.target.value)}
              />
            </div>
            <Button variant="primary" size="sm" className="h-11 shrink-0">
              인증발송
            </Button>
          </div>

          <TextField
            label="인증번호"
            placeholder="문자로 받은 6자리 입력"
            value={form.code}
            onChange={(e) => set('code')(e.target.value)}
          />
          <TextField
            label="이메일 (인증 필요)"
            type="email"
            placeholder="company@email.com"
            value={form.email}
            onChange={(e) => set('email')(e.target.value)}
          />
          <TextField
            label="비밀번호"
            type="password"
            placeholder="8~16자 조합"
            value={form.password}
            onChange={(e) => set('password')(e.target.value)}
          />
        </div>

        {/* 약관 동의 */}
        <label className="mt-5 flex items-center gap-2">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="h-[22px] w-[22px] rounded-sm border border-line accent-navy-900"
          />
          <span className="text-sm text-muted">
            서비스 이용약관 및 개인정보 처리방침에 동의합니다.
          </span>
        </label>

        <Button
          variant="navy"
          block
          className="mt-6"
          disabled={!agreed}
          onClick={() => navigate(ROUTES.verify)}
        >
          가입 완료
        </Button>
      </main>
    </ScreenShell>
  )
}

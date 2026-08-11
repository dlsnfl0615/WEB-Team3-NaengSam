import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, ScreenShell, TextField, TopBar } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'
import { api, isApiError } from '@/shared/api'
import {
  isBirth,
  isCode,
  isEmail,
  isPassword,
  isPhone,
  VALIDATION_MESSAGE,
} from '@/shared/lib/validation'

/**
 * 회원가입 화면(Figma node 21:62).
 * 이름/생년월일/전화번호(인증)/이메일/비밀번호·재확인 입력 + 약관 동의.
 */
export function SignupScreen() {
  const navigate = useNavigate()
  const signup = useSessionStore((s) => s.signup)
  const [form, setForm] = useState({
    name: '',
    birth: '',
    phone: '',
    code: '',
    email: '',
    password: '',
    passwordConfirm: '',
  })
  const [agreed, setAgreed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [sending, setSending] = useState(false)
  /** 인증번호 발송 여부(발송 후에만 인증확인 가능). */
  const [codeSent, setCodeSent] = useState(false)
  /** 서버 휴대폰 인증 완료 여부(가입의 전제 조건). */
  const [phoneVerified, setPhoneVerified] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = (key: keyof typeof form) => (value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  /** 입력값이 있고 형식에 안 맞을 때만 메시지 반환(입력 전엔 undefined). */
  const errorOf = (value: string, ok: boolean, message: string) =>
    value.trim() && !ok ? message : undefined

  const errors = {
    birth: errorOf(form.birth, isBirth(form.birth), VALIDATION_MESSAGE.birth),
    phone: errorOf(form.phone, isPhone(form.phone), VALIDATION_MESSAGE.phone),
    code: errorOf(form.code, isCode(form.code), VALIDATION_MESSAGE.code),
    email: errorOf(form.email, isEmail(form.email), VALIDATION_MESSAGE.email),
    password: errorOf(
      form.password,
      isPassword(form.password),
      VALIDATION_MESSAGE.password,
    ),
    passwordConfirm: errorOf(
      form.passwordConfirm,
      form.passwordConfirm === form.password,
      '비밀번호가 일치하지 않아요',
    ),
  }

  const allValid =
    !!form.name.trim() &&
    isBirth(form.birth) &&
    isPhone(form.phone) &&
    isCode(form.code) &&
    isEmail(form.email) &&
    isPassword(form.password) &&
    form.passwordConfirm === form.password &&
    phoneVerified

  /** 인증번호 발송. */
  const onSendCode = async () => {
    setError(null)
    setSending(true)
    try {
      await api.sendVerificationCode({ phoneNumber: form.phone })
      setCodeSent(true)
      setPhoneVerified(false)
    } catch (e) {
      setError(isApiError(e) ? e.message : '인증번호 발송에 실패했어요.')
    } finally {
      setSending(false)
    }
  }

  /** 인증번호 검증(서버에서 휴대폰 인증 완료 처리). */
  const onVerifyCode = async () => {
    setError(null)
    try {
      await api.verifyCode({ phoneNumber: form.phone, code: form.code })
      setPhoneVerified(true)
    } catch (e) {
      setPhoneVerified(false)
      setError(isApiError(e) ? e.message : '인증번호 확인에 실패했어요.')
    }
  }

  const onSignup = async () => {
    setError(null)
    setSubmitting(true)
    try {
      await signup({
        name: form.name,
        birth: form.birth,
        phone: form.phone,
        email: form.email,
        password: form.password,
      })
      navigate(ROUTES.verify, { replace: true })
    } catch (e) {
      setError(isApiError(e) ? e.message : '가입에 실패했어요. 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

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
            maxLength={50}
            value={form.name}
            onChange={(e) => set('name')(e.target.value)}
          />
          <TextField
            label="생년월일"
            inputMode="numeric"
            placeholder="2000.01.01"
            maxLength={10}
            value={form.birth}
            onChange={(e) => {
              const digits = e.target.value.replace(/\D/g, '').slice(0, 8)
              const formatted = digits
                .replace(/^(\d{4})(\d)/, '$1.$2')
                .replace(/^(\d{4}\.\d{2})(\d)/, '$1.$2')

              set('birth')(formatted)
            }}
            error={errors.birth}
          />

          {/* 전화번호 + 인증발송 */}
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <TextField
                label="전화번호"
                type="tel"
                placeholder="010-0000-0000"
                maxLength={13}
                value={form.phone}
                onChange={(e) => set('phone')(e.target.value)}
                error={errors.phone}
              />
            </div>
            <Button
              variant="primary"
              size="sm"
              className="h-11 shrink-0"
              disabled={!isPhone(form.phone) || sending}
              onClick={onSendCode}
            >
              {sending ? '발송 중…' : codeSent ? '재발송' : '인증발송'}
            </Button>
          </div>

          {/* 인증번호 + 인증확인 */}
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <TextField
                label="인증번호"
                placeholder="문자로 받은 6자리 입력"
                maxLength={6}
                value={form.code}
                onChange={(e) => {
                  set('code')(e.target.value)
                  setPhoneVerified(false)
                }}
                error={errors.code}
              />
            </div>
            <Button
              variant="primary"
              size="sm"
              className="h-11 shrink-0"
              disabled={!codeSent || !isCode(form.code) || phoneVerified}
              onClick={onVerifyCode}
            >
              {phoneVerified ? '인증완료' : '인증확인'}
            </Button>
          </div>

          <TextField
            label="이메일"
            type="email"
            placeholder="company@email.com"
            maxLength={254}
            value={form.email}
            onChange={(e) => set('email')(e.target.value)}
            error={errors.email}
          />
          <TextField
            label="비밀번호"
            type="password"
            placeholder="5~20자 조합"
            maxLength={20}
            value={form.password}
            onChange={(e) => set('password')(e.target.value)}
            error={errors.password}
          />
          <TextField
            label="비밀번호 재확인"
            type="password"
            placeholder="비밀번호를 다시 입력해 주세요"
            maxLength={20}
            value={form.passwordConfirm}
            onChange={(e) => set('passwordConfirm')(e.target.value)}
            error={errors.passwordConfirm}
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

        {error && <p className="mt-4 text-sm text-status-danger">{error}</p>}

        <Button
          variant="navy"
          block
          className="mt-6"
          disabled={!agreed || submitting || !allValid}
          onClick={onSignup}
        >
          {submitting ? '가입 중…' : '가입 완료'}
        </Button>
      </main>
    </ScreenShell>
  )
}

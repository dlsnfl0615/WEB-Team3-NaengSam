import { useState } from 'react'
import { ScreenShell, TextField, Button } from '@/shared/ui'
import { isApiError } from '@/shared/api'
import { axiosInstance } from '@/shared/api/http/axiosInstance'

/** order-and-start 응답 result 형태. */
interface SeedResult {
  orderId: string
  dreamiId: string
  boormiId: string
}

/**
 * 배달(픽업) 플로우 확인용 임시 dev 테스트 화면.
 * boormiId/dreamiId 를 직접 입력해 POST /api/v1/delivery/test/order-and-start 를 호출하고,
 * 응답이 오면 "픽업 중" 상태로 전환한다(픽업 완료 버튼 노출까지만, 이후 동작은 미구현).
 */
export function DeliveryTestScreen() {
  const [boormiId, setBoormiId] = useState('')
  const [dreamiId, setDreamiId] = useState('')
  const [phase, setPhase] = useState<'input' | 'pickup'>('input')
  const [result, setResult] = useState<SeedResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const canSubmit = boormiId.trim() !== '' && dreamiId.trim() !== '' && !loading

  async function handleTest() {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosInstance.post(
        '/api/v1/delivery/test/order-and-start',
        null,
        { params: { boormiId: boormiId.trim(), dreamiId: dreamiId.trim() } },
      )
      setResult(res.data?.result ?? null)
      setPhase('pickup')
    } catch (e) {
      setError(isApiError(e) ? e.message : '요청에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  if (phase === 'pickup') {
    return (
      <ScreenShell>
        <div className="flex flex-1 flex-col gap-6">
          <h1 className="text-lg font-bold text-navy-900">픽업 중</h1>
          {result && (
            <div className="flex flex-col gap-1 rounded-md border border-line bg-surface p-3.5">
              <span className="text-2xs text-muted">orderId</span>
              <span className="break-all text-sm text-navy-900">
                {result.orderId}
              </span>
            </div>
          )}
          <div className="mt-auto">
            <Button block>픽업 완료</Button>
          </div>
        </div>
      </ScreenShell>
    )
  }

  return (
    <ScreenShell>
      <div className="flex flex-col gap-5">
        <h1 className="text-lg font-bold text-navy-900">배달 테스트</h1>
        <TextField
          label="boormiId"
          placeholder="부르미 UUID"
          value={boormiId}
          onChange={(e) => setBoormiId(e.target.value)}
        />
        <TextField
          label="dreamiId"
          placeholder="드리미 UUID"
          value={dreamiId}
          onChange={(e) => setDreamiId(e.target.value)}
        />
        {error && <p className="text-sm text-status-danger">{error}</p>}
        <Button block disabled={!canSubmit} onClick={handleTest}>
          {loading ? '요청 중…' : '테스트하기'}
        </Button>
      </div>
    </ScreenShell>
  )
}

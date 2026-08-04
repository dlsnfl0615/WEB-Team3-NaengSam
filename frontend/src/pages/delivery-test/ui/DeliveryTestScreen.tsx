import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ScreenShell, TextField, Button } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { isApiError } from '@/shared/api'
import { axiosInstance } from '@/shared/api/http/axiosInstance'

/**
 * 배달(픽업) 플로우 확인용 임시 dev 테스트 화면.
 *
 * boormiId/dreamiId 를 입력해 POST /api/v1/delivery/test/order-and-start 로 더미 주문을 만들고
 * 배달(PICKUP_NORMAL)을 시작한 뒤, 실제 orderId 를 들고 기존 드리미 배달 화면(/delivery-track)으로
 * 넘긴다. 이후 픽업 완료 → 사진 인증 → pickup-finish 는 그 화면들이 실제 백엔드로 처리한다.
 *
 * ⚠️ 픽업 완료(pickup-finish)는 로그인 세션의 드리미로 처리되므로, 여기 입력하는 dreamiId 는
 *    현재 로그인한 계정의 ID 여야 이후 픽업 완료가 성공한다(NOT_ASSIGNED_DREAMI 방지).
 */
export function DeliveryTestScreen() {
  const navigate = useNavigate()
  const [boormiId, setBoormiId] = useState('')
  const [dreamiId, setDreamiId] = useState('')
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
      const orderId: string | undefined = res.data?.result?.orderId
      if (!orderId) throw new Error('orderId 를 받지 못했습니다.')
      // 실제 orderId 를 들고 기존 드리미 배달 화면으로 핸드오프.
      navigate(`${ROUTES.deliveryTrack}?orderId=${orderId}`)
    } catch (e) {
      setError(isApiError(e) ? e.message : '요청에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <ScreenShell>
      <div className="flex flex-col gap-5">
        <h1 className="text-lg font-bold text-navy-900">배달 테스트</h1>
        <p className="text-2xs text-muted">
          주문을 강제로 만들고 배달을 시작합니다. dreamiId 는 로그인한 계정의 ID 여야
          이후 픽업 완료가 성공합니다.
        </p>
        <TextField
          label="boormiId"
          placeholder="부르미 UUID"
          value={boormiId}
          onChange={(e) => setBoormiId(e.target.value)}
        />
        <TextField
          label="dreamiId"
          placeholder="드리미 UUID (로그인 계정)"
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

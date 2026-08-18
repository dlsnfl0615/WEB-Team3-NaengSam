import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import { Button, Card, ScreenShell, TopBar } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useSessionStore } from '@/shared/store/sessionStore'
import { api, isApiError } from '@/shared/api'
import type { GetPresignedUrlPurpose } from '@/shared/api'
import { DocumentUploadSlot } from './DocumentUploadSlot'

/** presigned URL 발급 후 S3에 직접 PUT하고, 발급받은 key를 반환한다(공통 axios 인스턴스 미사용). */
async function uploadDocument(file: File, purpose: GetPresignedUrlPurpose): Promise<string> {
  const { result } = await api.getPresignedUrl({ fileName: file.name, purpose })
  if (!result?.url || !result?.key) throw new Error('업로드 URL을 받지 못했어요.')
  const res = await fetch(result.url, {
    method: 'PUT',
    credentials: 'include',
    body: file,
    headers: { 'Content-Type': file.type },
  })
  if (!res.ok) throw new Error('사진 업로드에 실패했어요.')
  return result.key
}

/**
 * 본인인증 및 등록 화면(Figma node 21:41).
 * 드리미 등록을 위한 신분증/범죄이력회보서 업로드 안내 + 본인인증 진입.
 */
export function VerifyScreen() {
  const backOrHome = useBackOrHome();
  const navigate = useNavigate()
  const verify = useSessionStore((s) => s.verify)
  const [idCardFile, setIdCardFile] = useState<File | null>(null)
  const [criminalRecordFile, setCriminalRecordFile] = useState<File | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const canVerify = Boolean(idCardFile && criminalRecordFile) && !verifying

  const onVerify = async () => {
    if (!idCardFile || !criminalRecordFile) return
    setError(null)
    setVerifying(true)
    try {
      // presigned URL 발급 → S3 PUT까지 두 파일을 병렬로 끝낸 뒤에만 checkUpload를 호출한다.
      const [idCardKey, criminalRecordKey] = await Promise.all([
        uploadDocument(idCardFile, 'DREAMI_ID_CARD'),
        uploadDocument(criminalRecordFile, 'DREAMI_CRIMINAL_RECORD'),
      ])
      const updated = await verify(idCardKey, criminalRecordKey)
      if (!updated) {
        // verify()는 세션이 없을 때만 null을 반환한다(업로드 확인 실패는 예외로 던져져 catch에서 처리됨).
        navigate(ROUTES.login, { replace: true })
        return
      }
      // 홈 화면에서 "심사 중" 토스트를 띄우도록 신호만 실어 보낸다(문구는 홈이 소유).
      navigate(ROUTES.home, { replace: true, state: { dreamiVerificationSubmitted: true } })
    } catch (err) {
      setError(
        isApiError(err)
          ? err.message
          : err instanceof Error
            ? err.message
            : '본인인증에 실패했어요. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setVerifying(false)
    }
  }

  return (
    <ScreenShell>
      <TopBar
        title="본인인증 및 등록"
        onBack={backOrHome}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-2">
        <img
          src="/dreami-review-pending.png"
          alt="드리미 등록 안내"
          className="mx-auto h-[138px] w-auto"
        />

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

          {/* 신분증 / 범죄이력회보서 업로드 */}
          <div className="grid grid-cols-2 gap-3">
            <DocumentUploadSlot
              label="신분증"
              onSelect={setIdCardFile}
              disabled={verifying}
            />
            <DocumentUploadSlot
              label="범죄이력회보서"
              onSelect={setCriminalRecordFile}
              disabled={verifying}
            />
          </div>
        </Card>

        {error && <p className="text-xs text-status-danger">{error}</p>}

        {/* 안내 카드 */}
        <Card variant="accent" className="flex flex-col gap-1.5">
          <p className="text-sm font-bold text-navy-900">등록 안내</p>
          <p className="text-xs text-muted">
            본인인증을 완료하면 드리미로 활동할 수 있어요.
          </p>
        </Card>

        <div className="mt-auto flex flex-col items-center gap-3 pt-2">
          <Button variant="navy" block onClick={onVerify} disabled={!canVerify}>
            {verifying ? '업로드 및 인증 확인 중…' : '드리미 등록을 위한 본인인증'}
          </Button>
          <button
            type="button"
            className="text-sm text-muted hover:text-navy-900"
            onClick={() => navigate(ROUTES.home, { replace: true })}
          >
            나중에 등록 (부르미로 시작)
          </button>
        </div>
      </main>
    </ScreenShell>
  )
}

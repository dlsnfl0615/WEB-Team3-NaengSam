import { useBackOrHome } from '@/shared/lib/navigation/useBackOrHome'
import { Card, ScreenShell, TopBar } from '@/shared/ui'
import { FAQ_ITEMS } from './faqItems'

/** 자주 묻는 질문 화면. */
export function FaqScreen() {
  const backOrHome = useBackOrHome()

  return (
    <ScreenShell>
      <TopBar title="자주 묻는 질문" onBack={backOrHome} actions={[]} />

      <main className="flex flex-1 flex-col gap-3 pt-2">
        {FAQ_ITEMS.map((item) => (
          <Card key={item.question} variant="surface" className="flex flex-col gap-1.5">
            <p className="text-sm font-bold text-navy-900">{item.question}</p>
            <p className="text-xs text-muted">{item.answer}</p>
          </Card>
        ))}
      </main>
    </ScreenShell>
  )
}

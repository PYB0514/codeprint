// 최초 방문 사용자 대상 온보딩 투어 — 페이지별 steps/storageKey를 props로 받는 범용 컴포넌트
import { useTranslation } from 'react-i18next'
import { Joyride, STATUS } from 'react-joyride'
import type { EventData, Step } from 'react-joyride'

interface OnboardingTourProps {
  run: boolean
  onFinish: () => void
  steps: Step[]
  storageKey: string
}

// 온보딩 투어 컴포넌트 — run=true 시 투어 시작, 완료/종료 시 onFinish 호출
export default function OnboardingTour({ run, onFinish, steps, storageKey }: OnboardingTourProps) {
  const { t } = useTranslation('workspace')
  // 투어 완료/종료 이벤트 처리
  const handleEvent = (data: EventData) => {
    if (data.status === STATUS.FINISHED || data.status === STATUS.SKIPPED) {
      localStorage.setItem(storageKey, '1')
      onFinish()
    }
  }

  return (
    <Joyride
      steps={steps}
      run={run}
      onEvent={handleEvent}
      continuous
      scrollToFirstStep
      options={{
        primaryColor: '#3b82f6',
        backgroundColor: '#1f2937',
        textColor: '#d1d5db',
        arrowColor: '#1f2937',
        overlayColor: 'rgba(0,0,0,0.55)',
        zIndex: 9999,
        buttons: ['back', 'close', 'primary', 'skip'],
        showProgress: true,
      }}
      locale={{
        back: t('graphShared.tour.back'),
        close: t('graphShared.tour.close'),
        last: t('graphShared.tour.last'),
        next: t('graphShared.tour.next'),
        open: t('graphShared.tour.open'),
        skip: t('graphShared.tour.skip'),
      }}
    />
  )
}

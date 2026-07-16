// 최초 방문 사용자 대상 온보딩 투어 — 페이지별 steps/storageKey를 props로 받는 범용 컴포넌트
import { useTranslation } from 'react-i18next'
import { Joyride, STATUS } from 'react-joyride'
import type { Step, EventData } from 'react-joyride'
import i18n from '../i18n'

export const GRAPH_TOUR_STORAGE_KEY = 'onboarding_tour_done'

// GraphPage 온보딩 투어 steps — 컴포넌트 밖(모듈 스코프)에서 쓰이므로 훅 대신 i18n 인스턴스 직접 사용
export function getGraphTourSteps(): Step[] {
  return [
    {
      target: 'body',
      placement: 'center',
      title: i18n.t('graphShared.tour.welcomeTitle', { ns: 'workspace' }),
      content: i18n.t('graphShared.tour.welcomeContent', { ns: 'workspace' }),
      skipBeacon: true,
    },
    {
      target: '#tour-layout',
      placement: 'bottom',
      title: i18n.t('graphShared.tour.layoutTitle', { ns: 'workspace' }),
      content: i18n.t('graphShared.tour.layoutContent', { ns: 'workspace' }),
      skipBeacon: true,
    },
    {
      target: '#tour-edges',
      placement: 'right',
      title: i18n.t('graphShared.tour.edgesTitle', { ns: 'workspace' }),
      content: i18n.t('graphShared.tour.edgesContent', { ns: 'workspace' }),
      skipBeacon: true,
    },
    {
      target: '.react-flow__node',
      placement: 'top',
      title: i18n.t('graphShared.tour.clickNodeTitle', { ns: 'workspace' }),
      content: i18n.t('graphShared.tour.clickNodeContent', { ns: 'workspace' }),
      skipBeacon: true,
    },
    {
      target: '#tour-export',
      placement: 'bottom',
      title: i18n.t('graphShared.tour.exportTitle', { ns: 'workspace' }),
      content: i18n.t('graphShared.tour.exportContent', { ns: 'workspace' }),
      skipBeacon: true,
    },
  ]
}

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

// 투어를 이미 완료했는지 확인
export function isTourDone(storageKey: string): boolean {
  return localStorage.getItem(storageKey) === '1'
}

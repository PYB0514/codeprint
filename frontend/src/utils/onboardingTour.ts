// GraphPage 온보딩 투어 스텝·완료 여부 유틸 — OnboardingTour.tsx에서 분리(react-refresh/only-export-components)
import type { Step } from 'react-joyride'
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

// 투어를 이미 완료했는지 확인
export function isTourDone(storageKey: string): boolean {
  return localStorage.getItem(storageKey) === '1'
}

// 그래프 페이지 최초 방문 사용자 대상 온보딩 투어
import { Joyride, STATUS } from 'react-joyride'
import type { Step, EventData } from 'react-joyride'

const STORAGE_KEY = 'onboarding_tour_done'

const STEPS: Step[] = [
  {
    target: 'body',
    placement: 'center',
    title: '👋 Codeprint에 오신 걸 환영합니다!',
    content: '프로젝트의 파일 구조와 함수 호출 흐름을 인터랙티브 회로도로 탐색할 수 있습니다. 주요 기능을 소개합니다.',
    skipBeacon: true,
  },
  {
    target: '#tour-layout',
    placement: 'right',
    title: '레이아웃 전환',
    content: '계층 모드는 DDD 레이어(interfaces → application → domain → infrastructure)를 기준으로 정렬합니다. 허브 모드는 핵심 노드 중심으로 방사형 배치합니다.',
    skipBeacon: true,
  },
  {
    target: '#tour-edges',
    placement: 'right',
    title: '엣지 필터',
    content: '콜 체인, DB 연결, API 호출 등 연결선 종류를 토글로 켜고 끌 수 있습니다. 복잡해 보이면 필요한 연결만 남겨보세요.',
    skipBeacon: true,
  },
  {
    target: '.react-flow__node',
    placement: 'top',
    title: '노드를 클릭해보세요',
    content: '함수 노드를 클릭하면 우측 사이드바에서 상세 정보와 호출 흐름(콜 체인)을 확인할 수 있습니다. 흐름 재생 버튼으로 실행 경로를 애니메이션으로 볼 수 있어요.',
    skipBeacon: true,
  },
  {
    target: '#tour-export',
    placement: 'right',
    title: '이미지로 저장',
    content: '현재 그래프를 PNG 이미지로 내보낼 수 있습니다. 문서나 팀 공유용으로 활용해보세요.',
    skipBeacon: true,
  },
]

interface OnboardingTourProps {
  run: boolean
  onFinish: () => void
}

// 온보딩 투어 컴포넌트 — run=true 시 투어 시작, 완료/종료 시 onFinish 호출
export default function OnboardingTour({ run, onFinish }: OnboardingTourProps) {
  // 투어 완료/종료 이벤트 처리
  const handleEvent = (data: EventData) => {
    if (data.status === STATUS.FINISHED || data.status === STATUS.SKIPPED) {
      localStorage.setItem(STORAGE_KEY, '1')
      onFinish()
    }
  }

  return (
    <Joyride
      steps={STEPS}
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
        back: '이전',
        close: '닫기',
        last: '완료',
        next: '다음',
        open: '열기',
        skip: '건너뛰기',
      }}
    />
  )
}

// 투어를 이미 완료했는지 확인
export function isTourDone(): boolean {
  return localStorage.getItem(STORAGE_KEY) === '1'
}

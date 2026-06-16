// Codeprint가 자기 코드의 구조 결함을 스스로 적발한 사례(도그푸딩)를 보여주는 페이지
import AppHeader from '../components/AppHeader'
import { WARNING_META } from '../components/WarningPanel'

const SEVERITY_STYLE: Record<string, string> = {
  HIGH: 'bg-red-900/40 text-red-400',
  MEDIUM: 'bg-yellow-900/30 text-yellow-400',
  LOW: 'bg-gray-800/60 text-gray-400',
}

interface DogfoodCase {
  type: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  ref: string
  title: string
  caught: string
  missed: string
  fix: string
  highlight?: boolean
}

// 컴파일·테스트·코드 리뷰를 통과했지만 Codeprint 분석 엔진만 잡은 실제 결함들 (오탐 제외)
const CASES: DogfoodCase[] = [
  {
    type: 'CROSS_DOMAIN_CALL',
    severity: 'MEDIUM',
    ref: 'PR #277',
    title: 'PR 리뷰 기능이 자기 자신의 DDD 위반을 적발',
    caught:
      'PR 자동 리뷰 기능을 새로 만들면서 PrReviewService(analysis 컨텍스트)가 graph 컨텍스트의 detectWarnings를 직접 호출 — 바운디드 컨텍스트 경계를 넘는 직접 호출.',
    missed:
      '컴파일·테스트 전부 통과했고 코드 작성·검토 단계에서 경계 위반을 아무도 인지하지 못함. 컴파일러는 패키지 간 호출을 막지 않는다.',
    fix:
      '갓 만든 PR 리뷰 기능을 자기 PR(#277)에 직접 실행하니 탐지기가 즉시 표시. 정석 포트 패턴(WarningDetectionPort + Adapter)으로 역전 → 재검증 1→0.',
    highlight: true,
  },
  {
    type: 'CROSS_CONTEXT_IMPORT',
    severity: 'HIGH',
    ref: '2026-06-13',
    title: '알림 핸들러의 타 컨텍스트 이벤트 직접 import 7건',
    caught:
      'application/notification 의 NotificationEventHandler가 타 컨텍스트 도메인 이벤트 4종(community·message·user)을 직접 import. UserPlan 공유 타입 포함 총 7건.',
    missed: '기능은 정상 동작했고 import는 컴파일 통과. "알림이 여러 이벤트를 듣는다"는 자연스러운 코드라 위반으로 보이지 않음.',
    fix: '통합 이벤트 도입 + UserPlan을 shared로 이동 → 7→2 (잔존 2건은 아래).',
  },
  {
    type: 'CROSS_CONTEXT_IMPORT',
    severity: 'HIGH',
    ref: '2026-06-14',
    title: 'community → graph 경계 위반 잔존 2건',
    caught:
      'CommunityFacade가 graph·project의 Application Service를 직접 주입하고 domain/graph 의 Edge·Node를 직접 import.',
    missed: '게시글에 그래프를 붙이는 기능이 정상 동작 → 의존 방향이 잘못됐음을 코드만으로 알아채기 어려움.',
    fix: '포트 역전으로 community가 graph를 직접 모르게 분리 → CROSS_CONTEXT_IMPORT 0 달성.',
  },
  {
    type: 'DB_LAYER_BYPASS',
    severity: 'HIGH',
    ref: 'PR #135',
    title: 'Repository 계층 우회 45건',
    caught: 'Application/Domain 계층이 Repository 인터페이스를 거치지 않고 영속성 계층을 직접 호출하는 경로 45건.',
    missed: '동작에는 문제가 없어 일반 개발·리뷰로는 누적된 우회를 한눈에 셀 수 없음.',
    fix: 'Repository 인터페이스(domain) + 구현체(infrastructure) 정착 → 45→0.',
  },
  {
    type: 'MISSING_CONVERTER_MIGRATION',
    severity: 'HIGH',
    ref: 'PR #143',
    title: '다중 컨텍스트 구조 경고 44건 일괄 해소',
    caught:
      'CROSS_CONTEXT_IMPORT·DB_LAYER_BYPASS + @Convert 컬럼에 대한 Flyway 마이그레이션 누락이 여러 컨텍스트에 누적된 44건.',
    missed: '각각은 작은 위반이라 개별 PR에서는 드러나지 않고 누적됨. 마이그레이션 누락은 런타임 전까지 보이지 않음.',
    fix: '타입별로 일괄 정리 → 44건 해소.',
  },
]

// 도그푸딩 사례 페이지 컴포넌트
export default function DogfoodingPage() {
  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">도그푸딩 🐶</h1>
          <p className="text-gray-500 text-sm">
            Codeprint 분석 엔진이 <span className="text-gray-300">자기 코드의 실제 구조 결함</span>을 스스로 잡은 기록.
          </p>
        </div>

        <section className="mb-10">
          <p className="text-gray-400 text-sm leading-relaxed">
            아래 사례는 모두 <span className="text-gray-200">컴파일·테스트·코드 리뷰를 통과한</span> 코드에서 발생했고,
            Codeprint만 구조 결함을 표시했습니다. 코드를 한 줄씩 읽어서는 보이지 않는 <span className="text-gray-200">의존 방향과
            경계 위반</span>을 그래프로 보기 때문입니다. 가장 강한 증거는 <span className="text-cyan-400">PR 리뷰 기능이 자기 자신의
            버그를 잡은</span> 케이스입니다.
          </p>
          <p className="text-gray-600 text-xs leading-relaxed mt-3">
            ※ 오탐(false positive)은 여기 싣지 않습니다. 실제로 수정으로 이어진 진짜 결함만 기록합니다.
          </p>
        </section>

        <div className="flex flex-col gap-4">
          {CASES.map((c, i) => {
            const meta = WARNING_META[c.type]
            const color = meta?.color ?? '#6b7280'
            return (
              <div
                key={i}
                style={{ borderLeft: `3px solid ${color}` }}
                className={`rounded-r-lg p-4 ${c.highlight ? 'bg-cyan-950/20 border border-cyan-900/40 border-l-[3px]' : 'bg-gray-900/40'}`}
              >
                <div className="flex items-center gap-2 mb-2 flex-wrap">
                  {c.highlight && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-cyan-900/50 text-cyan-300">★ 대표</span>}
                  <span className="font-semibold text-sm" style={{ color }}>{meta?.label ?? c.type}</span>
                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${SEVERITY_STYLE[c.severity]}`}>{c.severity}</span>
                  <span className="text-[10px] text-gray-600 ml-auto">{c.ref}</span>
                </div>
                <h2 className="text-base font-semibold text-gray-100 mb-2">{c.title}</h2>
                <div className="flex flex-col gap-1.5 text-sm">
                  <p className="text-gray-300 leading-relaxed">
                    <span className="text-gray-500">무엇을 잡았나. </span>{c.caught}
                  </p>
                  <p className="text-gray-400 leading-relaxed">
                    <span className="text-gray-500">왜 놓쳤나. </span>{c.missed}
                  </p>
                  <p className="text-gray-400 leading-relaxed">
                    <span className="text-gray-500">수정. </span>{c.fix}
                  </p>
                </div>
              </div>
            )
          })}
        </div>

        <p className="text-gray-600 text-xs text-center mt-10 leading-relaxed">
          "구조를 보는 도구"가 자기 자신에게 적용됐을 때 실제로 결함을 잡는다는 증거.
        </p>
      </main>
    </div>
  )
}

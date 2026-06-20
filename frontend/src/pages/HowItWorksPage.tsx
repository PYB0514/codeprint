// 분석 엔진 동작 원리(그래프 생성 + 경고 감지)와 한계를 사용자에게 설명하는 페이지
import AppHeader from '../components/AppHeader'
import { WARNING_META } from '../components/WarningPanel'

const SEVERITY_STYLE: Record<string, { label: string; cls: string }> = {
  HIGH:   { label: 'HIGH',   cls: 'bg-red-900/40 text-red-400' },
  MEDIUM: { label: 'MEDIUM', cls: 'bg-yellow-900/30 text-yellow-400' },
  LOW:    { label: 'LOW',    cls: 'bg-gray-800/60 text-gray-400' },
}

// 경고 타입별 추가 설명 — 메타(라벨/색/심각도/요약)는 WARNING_META 단일 소스 재사용
const WARNING_GUIDE: Record<string, { example: string; limitation: string }> = {
  CYCLIC_IMPORT: {
    example: 'A 파일이 B를 import하고, B가 다시 A를 import하는 순환. 초기화 순서 문제·강한 결합의 신호.',
    limitation: 'IMPORT 엣지 기반이라 신뢰도가 높음. 의도된 순환(드묾)이면 숨기면 됩니다.',
  },
  BROKEN_INTERFACE_CHAIN: {
    example: '인터페이스 메서드는 있는데 대응하는 구현체 호출 엣지가 없는 경우 — 런타임에 미구현으로 깨질 수 있음.',
    limitation: '정규식이 구현체를 못 잇는 경우 오탐 가능. 구현이 분명하면 숨기세요.',
  },
  ASYNC_SELF_CALL: {
    example: '같은 클래스 안에서 @Async 메서드를 직접 호출 — 스프링 프록시를 우회해 비동기가 무시됨(동기 실행).',
    limitation: '같은 파일 내 직접 호출만 감지. 실제 프록시 경유 여부는 정적으로 100% 확정 불가.',
  },
  DB_LAYER_BYPASS: {
    example: 'Controller/Service가 Repository를 거치지 않고 영속성 계층(JpaRepository 등)을 직접 호출 — 계층 경계 위반.',
    limitation: 'IMPORT 기반으로만 판정(FUNCTION_CALL은 정규식 오추적이라 제외)해 오탐을 최소화.',
  },
  CROSS_CONTEXT_IMPORT: {
    example: 'application/주문 이 domain/결제 를 직접 import — 다른 바운디드 컨텍스트는 ID로만 참조해야 함(DDD).',
    limitation: 'DDD 폴더 구조(/application/, /domain/)가 있는 프로젝트에서만 활성화.',
  },
  DOMAIN_IMPORTS_INFRA: {
    example: 'domain/ 이 infrastructure/ 를 직접 import — 의존 방향 역전 위반(도메인은 인프라를 몰라야 함).',
    limitation: 'shared/(공유 커널)는 허용. DDD 구조 프로젝트에서만 활성화.',
  },
  CROSS_DOMAIN_CALL: {
    example: '함수 호출이 도메인 경계를 직접 넘음 — port/ 인터페이스를 경유해야 함.',
    limitation: '정규식이 클래스 한정자 없는 호출을 추적하는 한계로, getter·JPA·JDK·동명 함수는 제외. 그래도 일부 오탐 가능.',
  },
  MISSING_CONVERTER_MIGRATION: {
    example: '@Convert(암호화 등) 컬럼이 있는데 기존 평문 데이터를 변환하는 Flyway 마이그레이션이 없을 가능성 — 조회 시 복호화 실패로 500.',
    limitation: '마이그레이션 존재 여부까지는 못 보고 "가능성"만 경고. 이미 처리했다면 숨기세요.',
  },
  DEAD_CODE: {
    example: '어떤 FUNCTION_CALL 엣지도 받지 않는 함수 — 아무 곳에서도 호출되지 않는 후보.',
    limitation: '정규식은 메서드 레퍼런스(::)·리플렉션(JPA 컨버터)·다형성 디스패치를 일부 못 잡아 오탐이 날 수 있음(상당수 제외 처리됨). 차후 기능용으로 남긴 함수면 숨기세요.',
  },
  HIGH_FAN_OUT: {
    example: '한 함수가 너무 많은 함수를 호출 — 단일 책임 원칙(SRP) 위반 가능성.',
    limitation: '오케스트레이터(분석 파이프라인, 응답 조립 등)는 팬아웃이 큰 게 정상. getter·라이브러리 호출까지 세어 숫자가 부풀 수 있어 참고용(LOW).',
  },
}

// 그래프 구성 요소 설명
const GRAPH_PARTS = [
  { k: '노드', v: 'FILE(파일) · FUNCTION(함수/메서드) · DB_TABLE(엔티티/테이블) · API_ENDPOINT(라우트)' },
  { k: '엣지', v: 'IMPORT(의존) · CONTAINS(파일→함수) · FUNCTION_CALL(호출) · INSTANTIATION(생성) · DB_READ/WRITE · API_CALL' },
  { k: '주석', v: '파일·함수 상단 한 줄 주석을 추출해 노드 라벨로 표시 (이름 ↔ 주석 전환)' },
]

export default function HowItWorksPage() {
  const sevOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  const types = Object.keys(WARNING_GUIDE).sort((a, b) => {
    const sa = sevOrder[WARNING_META[a]?.severity ?? 'LOW'] ?? 2
    const sb = sevOrder[WARNING_META[b]?.severity ?? 'LOW'] ?? 2
    return sa - sb
  })

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">작동 방식</h1>
          <p className="text-gray-500 text-sm">Codeprint가 코드를 그래프로 만들고 위험 패턴을 감지하는 원리와 한계</p>
        </div>

        {/* 1. 그래프 생성 */}
        <section className="mb-12">
          <h2 className="text-xl font-semibold mb-3">1. 그래프는 어떻게 만들어지나</h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-4">
            GitHub 레포를 클론한 뒤, <span className="text-gray-200">언어별 정규식 기반 정적 분석</span>으로
            각 소스 파일에서 함수·import·DB 엔티티·API 라우트·주석을 추출합니다. 13개 언어를 지원하며,
            컴파일이나 실행 없이 소스 텍스트만 읽습니다. 추출 결과를 노드와 엣지로 엮어 그래프를 만듭니다.
          </p>
          <div className="flex flex-col gap-2 mb-4">
            {GRAPH_PARTS.map((p) => (
              <div key={p.k} className="flex gap-3 text-sm">
                <span className="shrink-0 w-10 text-cyan-400 font-medium">{p.k}</span>
                <span className="text-gray-400 leading-relaxed">{p.v}</span>
              </div>
            ))}
          </div>
          <div className="text-xs text-gray-500 bg-gray-900/60 border border-gray-800 rounded p-3 leading-relaxed">
            <span className="text-gray-300 font-medium">한계.</span> 정규식 기반이라 동적 호출·리플렉션·
            메서드 레퍼런스(::) 같은 일부 연결은 추적하지 못합니다. 그래서 아래 경고 중 일부는 오탐일 수 있고,
            각 경고를 직접 <span className="text-gray-300">숨길 수 있게</span> 했습니다.
          </div>
        </section>

        {/* 2. 경고 감지 */}
        <section className="mb-12">
          <h2 className="text-xl font-semibold mb-3">2. 어떤 위험 패턴을 감지하나</h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-5">
            그래프 위에서 정적 분석으로 잡을 수 있는 런타임·아키텍처 위험 패턴을 감지합니다.
            각 경고에는 심각도(HIGH/MEDIUM/LOW)가 붙습니다.
          </p>
          <div className="flex flex-col gap-4">
            {types.map((type) => {
              const meta = WARNING_META[type]
              const guide = WARNING_GUIDE[type]
              const sev = SEVERITY_STYLE[meta?.severity ?? 'LOW'] ?? SEVERITY_STYLE.LOW
              return (
                <div key={type} style={{ borderLeft: `3px solid ${meta?.color ?? '#6b7280'}` }} className="pl-3 py-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-semibold text-sm" style={{ color: meta?.color }}>{meta?.label ?? type}</span>
                    <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${sev.cls}`}>{sev.label}</span>
                  </div>
                  <p className="text-gray-300 text-sm leading-relaxed mb-1">{guide.example}</p>
                  <p className="text-gray-500 text-xs leading-relaxed">
                    <span className="text-gray-400">한계·오탐.</span> {guide.limitation}
                  </p>
                </div>
              )
            })}
          </div>
        </section>

        {/* 3. 경고 숨기기 */}
        <section>
          <h2 className="text-xl font-semibold mb-3">3. 오탐이거나 의도된 경고 숨기기</h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            모든 경고는 휴리스틱입니다 — 컴파일러가 아닙니다. 오탐이거나 의도된 패턴(차후 기능용 함수,
            오케스트레이터의 높은 호출 수 등)은 그래프 화면의 경고 패널에서 각 항목의
            <span className="text-gray-200"> ✕ 버튼</span>으로 숨길 수 있습니다. 숨김은 프로젝트 단위로 저장되어
            재분석해도 유지되며, 패널 하단의 <span className="text-gray-200">"숨긴 경고"</span> 목록에서 언제든 복원할 수 있습니다.
          </p>
        </section>
      </main>
    </div>
  )
}

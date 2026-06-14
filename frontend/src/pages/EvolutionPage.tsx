// 주요 기능이 버전을 거쳐 어떻게 자랐는지를 서사+마일스톤 타임라인으로 보여주는 페이지
import AppHeader from '../components/AppHeader'
import { RELEASES, type Release } from './ChangelogPage'

interface Arc {
  id: string
  title: string
  color: string
  lede: string
  versions: string[]
}

// 플래그십 3개 기능 — 각 arc의 서사(거의 불변)와 마일스톤 버전 목록(RELEASES에서 필터)
const ARCS: Arc[] = [
  {
    id: 'warning',
    title: '런타임 위험 경고 엔진',
    color: '#f59e0b',
    lede: '끊긴 연결 하나를 잡는 것에서 시작했다. 컴파일러는 못 잡지만 런타임에 터지는 패턴 — 순환 의존, 비동기 프록시 우회, DB 레이어 우회, DDD 경계 위반, 마이그레이션 누락 — 을 정적 분석으로 하나씩 추가했다. 패턴이 늘자 오탐도 늘었고, 그때부터는 "더 많이 잡기"가 아니라 "믿을 수 있게 잡기"로 방향이 바뀌었다. 비DDD 프로젝트 게이팅, 심각도 구분, 오탐 정리, 사용자가 직접 끄는 숨기기, 모든 판정 기준을 공개하는 작동 방식 페이지까지 — 감지기에서 신뢰할 수 있는 진단 도구로.',
    versions: ['v0.10.0', 'v0.10.1', 'v0.10.2', 'v0.10.3', 'v0.32.0', 'v0.72.0', 'v0.72.2', 'v0.72.4', 'v0.73.0', 'v0.74.0'],
  },
  {
    id: 'analysis',
    title: '다국어 분석 엔진',
    color: '#22d3ee',
    lede: '처음엔 Java 한 언어의 함수와 import만 읽었다. 거기서 11개 언어로, 그리고 함수 호출·DB 테이블·API 라우트로 추출 대상을 넓혔다. 언어마다 호출 표기가 다르고(C# PascalCase, Go 대문자), DB 접근 방식이 다르고(JPA·TypeORM·Prisma·raw SQL), 프론트의 fetch까지 — 각 언어·프레임워크의 실제 코드를 그래프에 담기 위한 누적된 확장이다. 정규식 기반이라는 한계 안에서 커버리지를 한 칸씩 늘려온 기록.',
    versions: ['v0.1.0', 'v0.3.0', 'v0.58.0', 'v0.71.1', 'v0.71.4', 'v0.71.6', 'v0.71.7'],
  },
  {
    id: 'graph',
    title: '그래프 시각화·탐색',
    color: '#a78bfa',
    lede: '노드와 엣지를 화면에 그리는 것에서, 코드를 읽는 도구로. 흐름 자동 시각화로 실행 경로를 따라가게 했고, 계층형/도메인 이중 뷰로 같은 코드를 두 관점에서 보게 했다. 도메인 뷰는 바운디드 컨텍스트 단위로 재설계했고, 흐름 재생은 분기 트리와 뷰포트 전환으로 다시 짰다. 엣지는 전부 그리던 방식에서 탐색할 때만 드러나는 온디맨드로 — 복잡도를 줄이고 조작성을 높이는 방향의 연속.',
    versions: ['v0.4.0', 'v0.8.0', 'v0.14.1', 'v0.17.0', 'v0.51.0', 'v0.72.3'],
  },
]

// 버전 문자열을 숫자 배열로 파싱해 의미 순서로 비교 (v0.10.0 > v0.9.0)
function compareVersion(a: string, b: string): number {
  const pa = a.replace(/^v/, '').split('.').map(Number)
  const pb = b.replace(/^v/, '').split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) - (pb[i] ?? 0)
  }
  return 0
}

const RELEASE_BY_VERSION: Record<string, Release> = Object.fromEntries(
  RELEASES.map((r) => [r.version, r]),
)

export default function EvolutionPage() {
  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">발전사</h1>
          <p className="text-gray-500 text-sm">
            패치노트가 "무엇이 바뀌었나"라면, 발전사는 "한 기능이 어떻게 자랐나" — 주요 기능 셋이 버전을 거쳐 커온 과정
          </p>
        </div>

        {ARCS.map((arc) => {
          const milestones = arc.versions
            .map((v) => RELEASE_BY_VERSION[v])
            .filter((r): r is Release => Boolean(r))
            .sort((a, b) => compareVersion(a.version, b.version))

          return (
            <section key={arc.id} className="mb-14">
              <div className="flex items-center gap-2 mb-3">
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: arc.color }} />
                <h2 className="text-xl font-semibold">{arc.title}</h2>
                <span className="text-xs text-gray-600">{milestones.length}개 마일스톤</span>
              </div>
              <p className="text-gray-400 text-sm leading-relaxed mb-6">{arc.lede}</p>

              <ol className="relative border-l border-gray-800 ml-1">
                {milestones.map((r) => (
                  <li key={r.version} className="ml-5 pb-6 last:pb-0">
                    <span
                      className="absolute -left-[5px] w-2.5 h-2.5 rounded-full mt-1.5"
                      style={{ backgroundColor: arc.color }}
                    />
                    <div className="flex items-baseline gap-2 mb-1 flex-wrap">
                      <span className="text-sm font-semibold" style={{ color: arc.color }}>
                        {r.version}
                      </span>
                      <span className="text-[10px] text-gray-600">{r.date}</span>
                    </div>
                    <p className="text-sm text-gray-200 leading-relaxed">{r.title}</p>
                  </li>
                ))}
              </ol>
            </section>
          )
        })}

        <p className="text-xs text-gray-600 border-t border-gray-800 pt-5">
          여기 실린 건 각 기능의 핵심 마일스톤만 추린 것입니다. 모든 버전의 전체 변경 내역은{' '}
          <a href="/changelog" className="text-gray-400 underline hover:text-gray-200">
            패치노트
          </a>
          에서 볼 수 있습니다.
        </p>
      </main>
    </div>
  )
}

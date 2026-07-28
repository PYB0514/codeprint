// 랜딩 히어로용 — Codeprint가 만드는 그래프를 흉내낸 인터랙티브 미리보기(노드 등장+엣지 드로잉 애니메이션)
interface NodeDef {
  id: string
  x: number
  y: number
  w: number
  h: number
  label: string
  color: string
}

const NODES: NodeDef[] = [
  { id: 'a', x: 24, y: 40, w: 160, h: 38, label: 'ProjectService.java', color: '#22d3ee' },
  { id: 'b', x: 290, y: 16, w: 168, h: 38, label: 'UserRepository.java', color: '#34d399' },
  { id: 'c', x: 160, y: 130, w: 172, h: 38, label: 'GraphBuilder.java', color: '#a78bfa' },
  { id: 'd', x: 380, y: 160, w: 150, h: 38, label: 'ApiEndpoint.kt', color: '#f59e0b' },
]

const EDGES: [string, string][] = [
  ['a', 'b'],
  ['a', 'c'],
  ['c', 'd'],
]

function center(n: NodeDef) {
  return { x: n.x + n.w / 2, y: n.y + n.h / 2 }
}

function length(a: { x: number; y: number }, b: { x: number; y: number }) {
  return Math.hypot(b.x - a.x, b.y - a.y)
}

// 랜딩 히어로 그래프 미리보기 렌더링
export default function HeroGraphPreview() {
  const byId = Object.fromEntries(NODES.map((n) => [n.id, n]))

  return (
    <div className="relative w-full max-w-xl">
      {/* 은은한 배경 글로우 */}
      <div className="absolute -inset-8 bg-cyan-500/10 blur-3xl rounded-full" aria-hidden />
      <div
        className="relative w-full bg-blueprint-950/90 border border-blueprint-border rounded-2xl overflow-hidden shadow-2xl shadow-black/40"
        style={{ height: 230 }}
        role="img"
        aria-label="파일과 함수 사이의 의존 관계를 노드와 화살표로 표시한 예시 그래프"
      >
        <svg viewBox="0 0 560 230" className="absolute inset-0 w-full h-full">
          {EDGES.map(([fromId, toId], i) => {
            const from = center(byId[fromId])
            const to = center(byId[toId])
            const len = length(from, to)
            return (
              <line
                key={`${fromId}-${toId}`}
                x1={from.x} y1={from.y} x2={to.x} y2={to.y}
                stroke="#22d3ee" strokeOpacity={0.45} strokeWidth={1.5}
                className="hero-graph-line"
                style={{ ['--line-length' as string]: len, ['--line-delay' as string]: `${0.5 + i * 0.15}s` }}
              />
            )
          })}
        </svg>
        {NODES.map((n, i) => (
          <div
            key={n.id}
            className={`hero-graph-node absolute rounded-lg border flex items-center px-3 text-[12px] font-mono ${i === 0 ? 'hero-graph-node--live' : ''}`}
            style={{
              left: n.x, top: n.y, width: n.w, height: n.h,
              borderColor: `${n.color}80`, backgroundColor: '#0d1f30e6', color: n.color,
              ['--pop-delay' as string]: `${i * 0.15}s`,
            }}
          >
            {n.label}
          </div>
        ))}
      </div>
    </div>
  )
}

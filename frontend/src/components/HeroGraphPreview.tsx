// 랜딩 히어로용 — Codeprint가 만드는 그래프를 흉내낸 작은 인터랙티브 미리보기(노드 등장+엣지 드로잉 애니메이션)
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
  { id: 'a', x: 16, y: 24, w: 132, h: 34, label: 'ProjectService.java', color: '#22d3ee' },
  { id: 'b', x: 232, y: 8, w: 136, h: 34, label: 'UserRepository.java', color: '#34d399' },
  { id: 'c', x: 120, y: 92, w: 140, h: 34, label: 'GraphBuilder.java', color: '#a78bfa' },
]

const EDGES: [string, string][] = [
  ['a', 'b'],
  ['a', 'c'],
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
    <div
      className="relative w-full max-w-md bg-gray-900/60 border border-gray-800 rounded-xl overflow-hidden"
      style={{ height: 150 }}
      role="img"
      aria-label="파일과 함수 사이의 의존 관계를 노드와 화살표로 표시한 예시 그래프"
    >
      <svg viewBox="0 0 380 150" className="absolute inset-0 w-full h-full">
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
          className={`hero-graph-node absolute rounded-md border flex items-center px-2.5 text-[11px] font-mono ${i === 0 ? 'hero-graph-node--live' : ''}`}
          style={{
            left: n.x, top: n.y, width: n.w, height: n.h,
            borderColor: `${n.color}80`, backgroundColor: '#0f172acc', color: n.color,
            ['--pop-delay' as string]: `${i * 0.15}s`,
          }}
        >
          {n.label}
        </div>
      ))}
    </div>
  )
}

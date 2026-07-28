// 랜딩 "주요 기능" 섹션용 — 실제 화면 스타일을 그대로 축소 재현한 라이브 미니 컴포넌트 5종(가짜 목업 이미지 대신)

// 1. 파일·함수 구조 시각화 — import 관계 하나를 확대해 보여주는 2노드 그래프
export function GraphMiniVisual() {
  return (
    <div className="relative w-full h-48 bg-blueprint-950 border border-blueprint-border rounded-xl overflow-hidden">
      <svg viewBox="0 0 320 180" className="absolute inset-0 w-full h-full">
        <line x1={90} y1={90} x2={230} y2={90} stroke="#22d3ee" strokeOpacity={0.5} strokeWidth={1.5}
          className="hero-graph-line" style={{ ['--line-length' as string]: 140, ['--line-delay' as string]: '0.3s' }} />
        <text x={160} y={82} textAnchor="middle" fontSize="9" fill="#67e8f9" fontFamily="monospace">IMPORT</text>
      </svg>
      <div className="hero-graph-node absolute rounded-md border flex items-center px-2.5 text-[11px] font-mono"
        style={{ left: 16, top: 74, width: 130, height: 32, borderColor: '#22d3ee80', backgroundColor: '#0f172acc', color: '#22d3ee', ['--pop-delay' as string]: '0s' }}>
        OrderService.java
      </div>
      <div className="hero-graph-node absolute rounded-md border flex items-center px-2.5 text-[11px] font-mono"
        style={{ left: 174, top: 74, width: 130, height: 32, borderColor: '#34d39980', backgroundColor: '#0f172acc', color: '#34d399', ['--pop-delay' as string]: '0.2s' }}>
        Inventory.java
      </div>
    </div>
  )
}

// 2. 전체 흐름 추적 — 노드 3개를 잇는 경로를 따라 하이라이트 점이 이동
export function FlowTraceMiniVisual() {
  const pathD = 'M 30 130 L 150 40 L 280 100'
  return (
    <div className="relative w-full h-48 bg-blueprint-950 border border-blueprint-border rounded-xl overflow-hidden">
      <svg viewBox="0 0 320 180" className="absolute inset-0 w-full h-full">
        <path d={pathD} fill="none" stroke="#a78bfa" strokeOpacity={0.35} strokeWidth={1.5} />
        <circle r={4} fill="#a78bfa" className="flow-travel-dot" style={{ ['--flow-path' as string]: `path('${pathD}')` }} />
      </svg>
      {[
        { x: 12, y: 112, label: 'ApiController' },
        { x: 108, y: 22, label: 'OrderService' },
        { x: 224, y: 82, label: 'PaymentClient' },
      ].map((n, i) => (
        <div key={n.label} className="hero-graph-node absolute rounded-md border flex items-center px-2 text-[10px] font-mono"
          style={{ left: n.x, top: n.y, width: 96, height: 28, borderColor: '#a78bfa80', backgroundColor: '#0f172acc', color: '#c4b5fd', ['--pop-delay' as string]: `${i * 0.15}s` }}>
          {n.label}
        </div>
      ))}
    </div>
  )
}

// 3. 구조 경고 감지 — 실제 경고 패널 카드 스타일을 그대로 축소 재현
export function WarningPanelMiniVisual() {
  const rows = [
    { severity: 'HIGH', cls: 'bg-red-900/40 text-red-400', label: 'CROSS_CONTEXT_IMPORT', flash: true },
    { severity: 'MEDIUM', cls: 'bg-yellow-900/30 text-yellow-400', label: 'DB_LAYER_BYPASS', flash: false },
    { severity: 'LOW', cls: 'bg-gray-800/60 text-gray-400', label: 'HIGH_FAN_OUT', flash: false },
  ]
  return (
    <div className="w-full h-48 bg-blueprint-950 border border-blueprint-border rounded-xl p-4 flex flex-col gap-2.5 justify-center">
      {rows.map((r) => (
        <div key={r.label} className="flex items-center gap-2 bg-blueprint-900/60 border border-blueprint-border rounded-lg px-3 py-2">
          <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded shrink-0 ${r.cls} ${r.flash ? 'badge-flash' : ''}`}>{r.severity}</span>
          <span className="text-[11px] font-mono text-gray-300 truncate">{r.label}</span>
        </div>
      ))}
    </div>
  )
}

// 4. 실시간 협업·커뮤니티 — 미니 캔버스 위를 떠다니는 협업 커서 2개
export function CollabMiniVisual() {
  return (
    <div className="relative w-full h-48 bg-blueprint-950 border border-blueprint-border rounded-xl overflow-hidden">
      <div className="absolute inset-6 border border-dashed border-blueprint-border rounded-lg opacity-40" />
      <div className="cursor-float absolute flex flex-col items-start gap-1" style={{ left: 60, top: 60, ['--float-delay' as string]: '0s' }}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="#22d3ee"><path d="M0 0 L16 6 L9 8 L6 15 Z" /></svg>
        <span className="text-[9px] bg-cyan-500 text-black px-1.5 py-0.5 rounded font-medium">지윤</span>
      </div>
      <div className="cursor-float absolute flex flex-col items-start gap-1" style={{ left: 180, top: 100, ['--float-delay' as string]: '1.2s' }}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="#f472b6"><path d="M0 0 L16 6 L9 8 L6 15 Z" /></svg>
        <span className="text-[9px] bg-pink-500 text-white px-1.5 py-0.5 rounded font-medium">민호</span>
      </div>
    </div>
  )
}

// 5. 이미지·AI 컨텍스트 내보내기 — PNG 저장 + .md 스니펫 미리보기, 완료 체크
export function ExportMiniVisual() {
  return (
    <div className="w-full h-48 bg-blueprint-950 border border-blueprint-border rounded-xl p-4 flex items-center justify-center gap-4">
      <div className="flex-1 bg-blueprint-900/60 border border-blueprint-border rounded-lg p-3 flex flex-col gap-1.5">
        <span className="text-[9px] text-gray-500 font-mono">graph.png</span>
        <div className="grid grid-cols-3 gap-1">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-3 rounded-sm bg-cyan-500/20 border border-cyan-500/30" />
          ))}
        </div>
      </div>
      <div className="check-pop w-8 h-8 rounded-full bg-emerald-500/20 border border-emerald-500/50 flex items-center justify-center shrink-0" style={{ ['--pop-delay' as string]: '0.4s' }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#34d399" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" /></svg>
      </div>
      <div className="flex-1 bg-blueprint-900/60 border border-blueprint-border rounded-lg p-3 flex flex-col gap-1 font-mono text-[9px] text-gray-400">
        <span className="text-gray-300"># ProjectService</span>
        <span>// 프로젝트 생성 로직</span>
        <span className="text-gray-600">createProject()</span>
      </div>
    </div>
  )
}

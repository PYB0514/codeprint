// 프로젝트 코드 구조를 React Flow로 시각화하는 그래프 페이지
import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import axios from 'axios'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  useReactFlow,
  ReactFlowProvider,
} from '@xyflow/react'
import type { Edge, EdgeMouseHandler, Node } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { toPng } from 'html-to-image'
import { buildLayout, downloadTreeText } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset } from '../utils/graphLayout'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

interface EdgeModalInfo {
  edgeIdentifier: string
  type: string
  sourceId: string
  targetId: string
  sourceNodeId: string
  targetNodeId: string
  funcLabel: string
  funcNodeId: string | null
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// 그래프 페이지 내부 컴포넌트 (ReactFlow 훅 사용)
function GraphPageInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [rawNodes, setRawNodes] = useState<RawNode[]>([])
  const [counts, setCounts] = useState({ files: 0, funcs: 0, edges: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [edgeModal, setEdgeModal] = useState<EdgeModalInfo | null>(null)
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [showIsoGroups, setShowIsoGroups] = useState(true)
  const [showEdges, setShowEdges] = useState(true)
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const { getNodes, fitView } = useReactFlow()

  // 서버에서 그래프 데이터를 불러와 React Flow 레이아웃으로 변환
  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { graphId: gid, nodes: rn, edges: re } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }
      setGraphId(gid)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset)
      setRawNodes(rn)
      setRawEdgesCache(re)
      setNodes(layoutNodes)
      setEdges(layoutEdges)
      setCounts({
        files: rn.filter((n) => n.type === 'FILE').length,
        funcs: rn.filter((n) => n.type === 'FUNCTION').length,
        edges: re.length,
      })
    } catch {
      setError('그래프를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, setNodes, setEdges])

  useEffect(() => { fetchGraph() }, [fetchGraph])

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = useCallback(() => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, next, layoutPreset)
      setNodes(layoutNodes)
      setEdges(layoutEdges)
    }
  }, [labelMode, layoutPreset, rawNodes, rawEdgesCache, setNodes, setEdges])

  // 엣지 전체 표시/숨김 토글
  const toggleEdges = useCallback(() => {
    setShowEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) => ({ ...e, hidden: !next })))
      return next
    })
  }, [setEdges])

  // 고립 그룹(연결 없는 그룹) 표시/숨김 토글
  const toggleIsoGroups = useCallback(() => {
    setShowIsoGroups((prev) => {
      const next = !prev
      setNodes((nds) => nds.map((n) =>
        n.data?.isIso || n.id === '__iso-section__'
          ? { ...n, hidden: !next }
          : n
      ))
      return next
    })
  }, [setNodes])

  // 레이아웃 프리셋 전환 — 그래프를 재계산하여 적용
  const toggleLayoutPreset = useCallback(() => {
    const next: LayoutPreset = layoutPreset === 'layer' ? 'hub' : 'layer'
    setLayoutPreset(next)
    setShowIsoGroups(true)
    setShowEdges(true)
    if (rawNodes.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodes, rawEdgesCache, labelMode, next)
      setNodes(ln)
      setEdges(le)
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }, [layoutPreset, rawNodes, rawEdgesCache, labelMode, setNodes, setEdges, fitView])

  // 전체 그래프를 원본 크기 PNG로 다운로드
  const handleExportImage = useCallback(async () => {
    const flowEl = flowRef.current?.querySelector('.react-flow__viewport') as HTMLElement | null
    if (!flowEl) return

    setExporting(true)
    try {
      // 전체 노드 바운딩 박스 계산
      const allNodes = getNodes()
      if (allNodes.length === 0) return

      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      allNodes.forEach((n) => {
        const w = (n.style?.width as number) ?? 150
        const h = (n.style?.height as number) ?? 40
        minX = Math.min(minX, n.position.x)
        minY = Math.min(minY, n.position.y)
        maxX = Math.max(maxX, n.position.x + w)
        maxY = Math.max(maxY, n.position.y + h)
      })

      const PAD = 40
      const fullW = maxX - minX + PAD * 2
      const fullH = maxY - minY + PAD * 2

      await fitView({ padding: 0.05, duration: 0 })

      const dataUrl = await toPng(flowEl, {
        backgroundColor: '#030712',
        width: fullW,
        height: fullH,
        style: {
          transform: `translate(${PAD - minX}px, ${PAD - minY}px)`,
        },
      })

      const a = document.createElement('a')
      a.href = dataUrl
      a.download = `codeprint-graph.png`
      a.click()
    } finally {
      setExporting(false)
    }
  }, [getNodes, fitView])

  // 노드 드래그 완료 시 서버에 위치를 저장
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleNodeDragStop = useCallback((_event: any, node: Node) => {
    if (!graphId) return
    axios.put(
      `/api/graphs/${graphId}/nodes/${node.id}/position`,
      { x: node.position.x, y: node.position.y },
      { headers: authHeaders() }
    ).catch(() => {})
  }, [graphId])

  // 엣지 마우스 진입 — 두껍고 밝게 강조
  const handleEdgeMouseEnter: EdgeMouseHandler<Edge> = useCallback((_evt, edge) => {
    const broken = (edge.data as { broken?: boolean })?.broken
    setEdges((es) => es.map((e) =>
      e.id === edge.id
        ? { ...e, style: { ...e.style, strokeWidth: broken ? 3.5 : 3, stroke: broken ? '#fca5a5' : '#a1a1aa' } }
        : e
    ))
  }, [setEdges])

  // 엣지 마우스 이탈 — 원래 스타일 복원
  const handleEdgeMouseLeave: EdgeMouseHandler<Edge> = useCallback((_evt, edge) => {
    const broken = (edge.data as { broken?: boolean })?.broken
    setEdges((es) => es.map((e) =>
      e.id === edge.id
        ? { ...e, style: { ...e.style, strokeWidth: broken ? 2 : 1.5, stroke: broken ? '#ef4444' : '#4b5563' } }
        : e
    ))
  }, [setEdges])

  // 엣지 클릭 시 상세 정보 모달을 표시
  const handleEdgeClick: EdgeMouseHandler<Edge> = useCallback((_event, edge) => {
    const data = edge.data as { edgeIdentifier?: string; type?: string } | undefined
    const sourceNode = rawNodes.find((n) => n.id === edge.source && n.type === 'FILE')
    const targetNode = rawNodes.find((n) => n.id === edge.target && n.type === 'FILE')
    const edgeId = data?.edgeIdentifier ?? edge.id
    const edgeType = data?.type ?? 'IMPORT'
    // FUNCTION_CALL만 edgeIdentifier에서 함수명 파싱 — IMPORT 등은 파일명 기반이라 파싱 불가
    const funcName = edgeType === 'FUNCTION_CALL' ? (edgeId.split('-').pop() ?? '') : ''
    const funcNode = funcName
      ? rawNodes.find((n) => n.type === 'FUNCTION' && n.filePath === sourceNode?.filePath && n.name === funcName)
      : null
    const funcLabel = funcName
      ? (labelMode === 'comment' && funcNode?.comment ? funcNode.comment : funcName)
      : '—'
    setEdgeModal({
      edgeIdentifier: edgeId,
      type: data?.type ?? 'IMPORT',
      sourceId: sourceNode?.name ?? edge.source,
      targetId: targetNode?.name ?? edge.target,
      sourceNodeId: edge.source,
      targetNodeId: edge.target,
      funcLabel,
      funcNodeId: funcNode?.id ?? null,
    })
  }, [rawNodes, labelMode])

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center text-gray-400">
        로딩 중...
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-950 flex flex-col items-center justify-center gap-4 text-gray-400">
        <p>{error}</p>
        <button onClick={() => navigate('/dashboard')} className="underline text-sm">대시보드로</button>
      </div>
    )
  }

  return (
    <div ref={flowRef} style={{ width: '100vw', height: '100vh', background: '#030712' }}>
      {/* 상단 바 */}
      <div className="absolute top-4 left-4 z-10 flex items-center gap-3">
        <button
          onClick={() => navigate('/dashboard')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          ← 대시보드
        </button>
        <span className="text-gray-400 text-sm">
          파일 {counts.files}개 · 함수 {counts.funcs}개 · 엣지 {counts.edges}개
        </span>
        <button
          onClick={toggleLayoutPreset}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700"
          title={layoutPreset === 'layer' ? 'DDD 레이어별 컬럼 배치' : '연결 많은 그룹이 중앙으로'}
        >
          <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>계층</span>
          <span className="text-gray-600">/</span>
          <span className={layoutPreset === 'hub' ? 'text-white' : 'text-gray-500'}>허브</span>
        </button>
        {layoutPreset === 'hub' && (
          <button
            onClick={toggleIsoGroups}
            className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700"
            title="연결 없는 그룹 표시/숨김"
          >
            <span className={showIsoGroups ? 'text-white' : 'text-gray-500'}>고립 그룹</span>
          </button>
        )}
        <button
          onClick={toggleLabelMode}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700"
        >
          <span className={labelMode === 'name' ? 'text-white' : 'text-gray-500'}>이름</span>
          <span className="text-gray-600">/</span>
          <span className={labelMode === 'comment' ? 'text-white' : 'text-gray-500'}>주석</span>
        </button>
        <button
          onClick={toggleEdges}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700"
          title="연결선 표시/숨김"
        >
          <span className={showEdges ? 'text-white' : 'text-gray-500'}>연결선</span>
        </button>
        <button
          onClick={() => downloadTreeText(rawNodes)}
          disabled={rawNodes.length === 0}
          className="bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700 disabled:opacity-40"
          title="파일명 — 한국어 주석 형태의 AI 컨텍스트용 트리 다운로드"
        >
          ↓ AI 컨텍스트
        </button>
        <button
          onClick={handleExportImage}
          disabled={exporting || rawNodes.length === 0}
          className="bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700 disabled:opacity-40"
          title="전체 그래프를 원본 크기 PNG로 저장"
        >
          {exporting ? '저장 중...' : '↓ 이미지'}
        </button>
      </div>

      {/* 범례 */}
      <div className="absolute top-4 right-4 z-10 bg-gray-900/90 rounded-xl p-3 flex flex-col gap-2 text-xs border border-gray-700/50 backdrop-blur-sm">
        <p className="text-gray-500 font-semibold text-[10px] uppercase tracking-widest mb-0.5">Legend</p>
        <div className="flex flex-col gap-1 border-b border-gray-700/50 pb-2 mb-0.5">
          <p className="text-gray-500 text-[9px] uppercase tracking-wider">DDD 레이어</p>
          {[
            { label: 'Domain',         color: '#3b82f6' },
            { label: 'Application',    color: '#eab308' },
            { label: 'Infrastructure', color: '#a855f7' },
            { label: 'Interfaces',     color: '#10b981' },
            { label: 'Pages/Components', color: '#06b6d4' },
          ].map(({ label, color }) => (
            <div key={label} className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-sm flex-shrink-0" style={{ background: `${color}22`, border: `1.5px solid ${color}` }} />
              <span className="text-gray-400">{label}</span>
            </div>
          ))}
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#1e3a5f', border: '1.5px solid #3b82f6' }} />
          <span className="text-gray-400">FILE</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#064e3b', border: '1px solid #10b981' }} />
          <span className="text-gray-400">FUNCTION</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-0.5 flex-shrink-0" style={{ background: '#4b5563' }} />
          <span className="text-gray-400">IMPORT</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-0.5 flex-shrink-0" style={{ background: '#ef4444' }} />
          <span className="text-gray-400">끊긴 연결</span>
        </div>
      </div>

      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onEdgeMouseEnter={handleEdgeMouseEnter}
        onEdgeMouseLeave={handleEdgeMouseLeave}
        onEdgeClick={handleEdgeClick}
        onNodeDragStop={handleNodeDragStop}
        fitView
        fitViewOptions={{ padding: 0.1 }}
        minZoom={0.05}
        maxZoom={2}
      >
        <Background color="#1f2937" gap={20} />
        <Controls />
        <MiniMap
          nodeColor={(n) => {
            const bg = n.style?.background as string
            return bg ?? '#374151'
          }}
          style={{ background: '#111827' }}
        />
      </ReactFlow>

      {/* 엣지 클릭 모달 */}
      {edgeModal && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
          onClick={() => setEdgeModal(null)}
        >
          <div
            className="bg-gray-900 rounded-2xl p-6 w-80 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-sm">연결 상세</h3>
              <button onClick={() => setEdgeModal(null)} className="text-gray-500 hover:text-white text-xs">✕</button>
            </div>
            <div className="flex flex-col gap-3 text-sm">
              {/* 함수 — 핵심 정보 */}
              <div className="bg-gray-800 rounded-xl p-4 flex flex-col items-center gap-1">
                <p className="text-xs text-gray-500">호출 함수</p>
                <p
                  className="text-white font-mono text-sm font-semibold text-center break-all cursor-pointer hover:text-blue-300 underline decoration-gray-600"
                  title="클릭하여 해당 함수로 이동"
                  onClick={() => {
                    if (!edgeModal.funcNodeId) return
                    setEdgeModal(null)
                    setTimeout(() => fitView({ nodes: [{ id: edgeModal.funcNodeId! }], duration: 500, padding: 0.4 }), 50)
                  }}
                >{edgeModal.funcLabel}</p>
                <span className="text-xs bg-blue-900 text-blue-300 px-2 py-0.5 rounded mt-1">{edgeModal.type}</span>
              </div>
              {/* 출발 → 도착 */}
              <div className="bg-gray-800/60 rounded-lg p-3 flex flex-col gap-2">
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">출발 파일</p>
                  <p
                    className="text-gray-300 font-mono text-xs cursor-pointer hover:text-white underline decoration-gray-600"
                    title="클릭하여 해당 파일로 이동"
                    onClick={() => {
                      setEdgeModal(null)
                      setTimeout(() => fitView({ nodes: [{ id: edgeModal.sourceNodeId }], duration: 500, padding: 0.3 }), 50)
                    }}
                  >{edgeModal.sourceId}</p>
                </div>
                <div className="text-gray-600 text-xs text-center">↓</div>
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">도착 파일</p>
                  <p
                    className="text-gray-300 font-mono text-xs cursor-pointer hover:text-white underline decoration-gray-600"
                    title="클릭하여 해당 파일로 이동"
                    onClick={() => {
                      setEdgeModal(null)
                      setTimeout(() => fitView({ nodes: [{ id: edgeModal.targetNodeId }], duration: 500, padding: 0.3 }), 50)
                    }}
                  >{edgeModal.targetId}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// useReactFlow은 ReactFlowProvider 안에서만 동작하므로 래핑
export default function GraphPage() {
  return (
    <ReactFlowProvider>
      <GraphPageInner />
    </ReactFlowProvider>
  )
}

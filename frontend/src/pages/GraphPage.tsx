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
import type { RawNode, RawEdge, LabelMode, LayoutPreset, FileSidebarData, ConnEntry, FuncCallEntry } from '../utils/graphLayout'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

interface FuncCallChainEntry {
  funcName: string
  funcComment: string | null
  funcNodeId: string
  fileName: string
  fileNodeId: string
}

// 사이드바 콘텐츠 — 엣지 클릭 / 파일 연결 보기 / 함수 노드 클릭 세 종류
type SidebarContent =
  | { kind: 'edge'; sourceId: string; targetId: string; sourceNodeId: string; targetNodeId: string; callChain: ConnEntry['callChain'] }
  | { kind: 'file'; data: FileSidebarData }
  | { kind: 'func'; funcName: string; funcComment: string | null; parentFileName: string; parentFileNodeId: string; callers: FuncCallChainEntry[]; callees: FuncCallChainEntry[] }
  | { kind: 'func-call'; callerName: string; callerComment: string | null; callerNodeId: string; callerFile: string; callerFileNodeId: string; calleeName: string; calleeComment: string | null; calleeNodeId: string; calleeFile: string; calleeFileNodeId: string }
  | { kind: 'instantiation'; sourceFile: string; sourceNodeId: string; targetClass: string; targetNodeId: string }

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
  const [sidebar, setSidebar] = useState<SidebarContent | null>(null)
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [showIsoGroups, setShowIsoGroups] = useState(true)
  const [showEdges, setShowEdges] = useState(true)
  const [showCallEdges, setShowCallEdges] = useState(true)
  const [showInstEdges, setShowInstEdges] = useState(true)
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const { getNodes, fitView } = useReactFlow()

  // 파일 연결 보기 — 사이드바 오픈 콜백
  const openFileSidebar = useCallback((data: FileSidebarData) => {
    setSidebar({ kind: 'file', data })
  }, [])

  // 서버에서 그래프 데이터를 불러와 React Flow 레이아웃으로 변환
  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { graphId: gid, nodes: rn, edges: re } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }
      setGraphId(gid)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset, openFileSidebar)
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
  }, [projectId, setNodes, setEdges, openFileSidebar])

  useEffect(() => { fetchGraph() }, [fetchGraph])

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = useCallback(() => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, next, layoutPreset, openFileSidebar)
      setNodes(layoutNodes)
      setEdges(layoutEdges)
    }
  }, [labelMode, layoutPreset, rawNodes, rawEdgesCache, setNodes, setEdges, openFileSidebar])

  // IMPORT 엣지 표시/숨김 토글
  const toggleEdges = useCallback(() => {
    setShowEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) =>
        (e.data as { type?: string })?.type === 'IMPORT'
          ? { ...e, hidden: !next }
          : e
      ))
      return next
    })
  }, [setEdges])

  // FUNCTION_CALL 엣지 표시/숨김 토글
  const toggleCallEdges = useCallback(() => {
    setShowCallEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) =>
        (e.data as { type?: string })?.type === 'FUNCTION_CALL'
          ? { ...e, hidden: !next }
          : e
      ))
      return next
    })
  }, [setEdges])

  // INSTANTIATION 엣지 표시/숨김 토글
  const toggleInstEdges = useCallback(() => {
    setShowInstEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) =>
        (e.data as { type?: string })?.type === 'INSTANTIATION'
          ? { ...e, hidden: !next }
          : e
      ))
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
      const { nodes: ln, edges: le } = buildLayout(rawNodes, rawEdgesCache, labelMode, next, openFileSidebar)
      setNodes(ln)
      setEdges(le)
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }, [layoutPreset, rawNodes, rawEdgesCache, labelMode, setNodes, setEdges, fitView, openFileSidebar])

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
    const data = edge.data as { broken?: boolean; type?: string } | undefined
    const broken = data?.broken
    const isCall = data?.type === 'FUNCTION_CALL'
    const isInst = data?.type === 'INSTANTIATION'
    const hoverColor = broken ? '#fca5a5' : isCall ? '#fcd34d' : isInst ? '#d8b4fe' : '#a1a1aa'
    setEdges((es) => es.map((e) =>
      e.id === edge.id
        ? { ...e, style: { ...e.style, strokeWidth: (isCall || isInst) ? 2.5 : broken ? 3.5 : 3, stroke: hoverColor } }
        : e
    ))
  }, [setEdges])

  // 엣지 마우스 이탈 — 원래 스타일 복원
  const handleEdgeMouseLeave: EdgeMouseHandler<Edge> = useCallback((_evt, edge) => {
    const data = edge.data as { broken?: boolean; type?: string } | undefined
    const broken = data?.broken
    const isCall = data?.type === 'FUNCTION_CALL'
    const isInst = data?.type === 'INSTANTIATION'
    const baseColor = broken ? '#ef4444' : isCall ? '#f59e0b' : isInst ? '#a855f7' : '#4b5563'
    setEdges((es) => es.map((e) =>
      e.id === edge.id
        ? { ...e, style: { ...e.style, strokeWidth: (isCall || isInst) ? 1.2 : broken ? 2 : 1.5, stroke: baseColor } }
        : e
    ))
  }, [setEdges])

  // 엣지 클릭 시 사이드바에 연결 상세 표시
  const handleEdgeClick: EdgeMouseHandler<Edge> = useCallback((_event, edge) => {
    const data = edge.data as { type?: string } | undefined

    if (data?.type === 'FUNCTION_CALL') {
      const srcFunc = rawNodes.find((n) => n.id === edge.source && n.type === 'FUNCTION')
      const tgtFunc = rawNodes.find((n) => n.id === edge.target && n.type === 'FUNCTION')
      const srcFile = rawNodes.find((n) => n.type === 'FILE' && n.filePath === srcFunc?.filePath)
      const tgtFile = rawNodes.find((n) => n.type === 'FILE' && n.filePath === tgtFunc?.filePath)
      setSidebar({
        kind: 'func-call',
        callerName: srcFunc?.name ?? edge.source,
        callerComment: srcFunc?.comment ?? null,
        callerNodeId: edge.source,
        callerFile: srcFile?.name ?? srcFunc?.filePath ?? '',
        callerFileNodeId: srcFile?.id ?? '',
        calleeName: tgtFunc?.name ?? edge.target,
        calleeComment: tgtFunc?.comment ?? null,
        calleeNodeId: edge.target,
        calleeFile: tgtFile?.name ?? tgtFunc?.filePath ?? '',
        calleeFileNodeId: tgtFile?.id ?? '',
      })
      return
    }

    if (data?.type === 'INSTANTIATION') {
      const srcFile = rawNodes.find((n) => n.id === edge.source && n.type === 'FILE')
      const tgtFile = rawNodes.find((n) => n.id === edge.target && n.type === 'FILE')
      setSidebar({
        kind: 'instantiation',
        sourceFile: srcFile?.name ?? edge.source,
        sourceNodeId: edge.source,
        targetClass: tgtFile?.name ?? edge.target,
        targetNodeId: edge.target,
      })
      return
    }

    const sourceFile = rawNodes.find((n) => n.id === edge.source && n.type === 'FILE')
    const targetFile = rawNodes.find((n) => n.id === edge.target && n.type === 'FILE')

    const callChain: ConnEntry['callChain'] = rawEdgesCache
      .filter((e) => e.type === 'FUNCTION_CALL')
      .flatMap((e) => {
        const srcFunc = rawNodes.find((n) => n.id === e.source && n.type === 'FUNCTION')
        const tgtFunc = rawNodes.find((n) => n.id === e.target && n.type === 'FUNCTION')
        if (!srcFunc || !tgtFunc) return []
        if (srcFunc.filePath !== sourceFile?.filePath || tgtFunc.filePath !== targetFile?.filePath) return []
        return [{ callerName: srcFunc.name, callerLabel: srcFunc.comment ?? srcFunc.name, callerNodeId: srcFunc.id, calleeName: tgtFunc.name, calleeLabel: tgtFunc.comment ?? tgtFunc.name, calleeNodeId: tgtFunc.id }] as FuncCallEntry[]
      })

    setSidebar({
      kind: 'edge',
      sourceId: sourceFile?.name ?? edge.source,
      targetId: targetFile?.name ?? edge.target,
      sourceNodeId: edge.source,
      targetNodeId: edge.target,
      callChain,
    })
  }, [rawNodes, rawEdgesCache])

  // 함수 노드 클릭 시 사이드바에 콜 체인 표시
  const handleNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    if (node.type === 'fileNode' || node.type === 'groupNode' || node.type === 'sectionNode') return
    // FUNCTION 노드만 처리
    const rawFunc = rawNodes.find((n) => n.id === node.id && n.type === 'FUNCTION')
    if (!rawFunc) return

    const parentFile = rawNodes.find((n) => n.type === 'FILE' && n.filePath === rawFunc.filePath)

    const toEntry = (funcNode: RawNode): FuncCallChainEntry => {
      const file = rawNodes.find((n) => n.type === 'FILE' && n.filePath === funcNode.filePath)
      return {
        funcName: funcNode.name,
        funcComment: funcNode.comment ?? null,
        funcNodeId: funcNode.id,
        fileName: file?.name ?? funcNode.filePath,
        fileNodeId: file?.id ?? '',
      }
    }

    const callers: FuncCallChainEntry[] = rawEdgesCache
      .filter((e) => e.type === 'FUNCTION_CALL' && e.target === rawFunc.id)
      .map((e) => rawNodes.find((n) => n.id === e.source && n.type === 'FUNCTION'))
      .filter((n): n is RawNode => !!n && n.filePath !== rawFunc.filePath)
      .map(toEntry)

    const callees: FuncCallChainEntry[] = rawEdgesCache
      .filter((e) => e.type === 'FUNCTION_CALL' && e.source === rawFunc.id)
      .map((e) => rawNodes.find((n) => n.id === e.target && n.type === 'FUNCTION'))
      .filter((n): n is RawNode => !!n && n.filePath !== rawFunc.filePath)
      .map(toEntry)

    setSidebar({
      kind: 'func',
      funcName: rawFunc.name,
      funcComment: rawFunc.comment ?? null,
      parentFileName: parentFile?.name ?? rawFunc.filePath,
      parentFileNodeId: parentFile?.id ?? '',
      callers,
      callees,
    })
  }, [rawNodes, rawEdgesCache])

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
          onClick={toggleCallEdges}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-amber-800/50"
          title="함수 호출 체인 표시/숨김"
        >
          <span className={showCallEdges ? 'text-amber-400' : 'text-gray-500'}>콜 체인</span>
        </button>
        <button
          onClick={toggleInstEdges}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-purple-800/50"
          title="인스턴스화 관계 표시/숨김"
        >
          <span className={showInstEdges ? 'text-purple-400' : 'text-gray-500'}>생성</span>
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
          <svg width="12" height="4" className="flex-shrink-0">
            <line x1="0" y1="2" x2="12" y2="2" stroke="#f59e0b" strokeWidth="1.5" strokeDasharray="4 3" />
          </svg>
          <span className="text-amber-400">FUNCTION_CALL</span>
        </div>
        <div className="flex items-center gap-2">
          <svg width="12" height="4" className="flex-shrink-0">
            <line x1="0" y1="2" x2="12" y2="2" stroke="#a855f7" strokeWidth="1.5" strokeDasharray="3 4" />
          </svg>
          <span className="text-purple-400">INSTANTIATION</span>
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
        onNodeClick={handleNodeClick}
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

      {/* 우측 사이드바 */}
      {sidebar && (
        <aside className="fixed right-0 top-0 h-full w-80 bg-gray-950 border-l border-gray-800 z-40 flex flex-col shadow-2xl">
          {/* 사이드바 헤더 */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 flex-shrink-0">
            <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
              {sidebar.kind === 'edge' ? '연결 상세' : sidebar.kind === 'file' ? '파일 연결' : sidebar.kind === 'func' ? '함수 상세' : sidebar.kind === 'func-call' ? '함수 호출' : '인스턴스화'}
            </span>
            <button onClick={() => setSidebar(null)} className="text-gray-600 hover:text-white text-sm">✕</button>
          </div>

          <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">

            {/* ── FUNCTION_CALL 엣지 클릭 ── */}
            {sidebar.kind === 'func-call' && (
              <div className="flex flex-col gap-3">
                <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                  <div>
                    <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">호출하는 함수</p>
                    <p className="text-emerald-400 font-mono text-sm font-semibold cursor-pointer hover:text-emerald-200"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.callerNodeId }], duration: 500, padding: 0.4 }), 50) }}
                    >{sidebar.callerComment ?? sidebar.callerName}</p>
                    <p className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-300 mt-0.5"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.callerFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                    >{sidebar.callerFile}</p>
                  </div>
                  <div className="text-amber-500 text-sm text-center">↓</div>
                  <div>
                    <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">호출받는 함수</p>
                    <p className="text-emerald-400 font-mono text-sm font-semibold cursor-pointer hover:text-emerald-200"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.calleeNodeId }], duration: 500, padding: 0.4 }), 50) }}
                    >{sidebar.calleeComment ?? sidebar.calleeName}</p>
                    <p className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-300 mt-0.5"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.calleeFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                    >{sidebar.calleeFile}</p>
                  </div>
                </div>
                <span className="text-xs bg-amber-900/40 text-amber-400 px-2 py-0.5 rounded self-start">FUNCTION_CALL</span>
              </div>
            )}

            {/* ── INSTANTIATION 엣지 클릭 ── */}
            {sidebar.kind === 'instantiation' && (
              <div className="flex flex-col gap-3">
                <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                  <div>
                    <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">생성하는 파일</p>
                    <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.sourceNodeId }], duration: 500, padding: 0.3 }), 50) }}
                    >{sidebar.sourceFile}</p>
                  </div>
                  <div className="text-purple-400 text-sm text-center">↓ new</div>
                  <div>
                    <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">인스턴스화 대상</p>
                    <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                      onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.targetNodeId }], duration: 500, padding: 0.3 }), 50) }}
                    >{sidebar.targetClass}</p>
                  </div>
                </div>
                <span className="text-xs bg-purple-900/40 text-purple-400 px-2 py-0.5 rounded self-start">INSTANTIATION</span>
              </div>
            )}

            {/* ── 엣지 클릭: 파일→파일 + 콜 체인 ── */}
            {sidebar.kind === 'edge' && (
              <>
                <div className="bg-gray-800/60 rounded-lg p-3 flex items-center gap-2">
                  <span
                    className="text-blue-300 font-mono text-xs cursor-pointer hover:text-white truncate flex-1"
                    onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.sourceNodeId }], duration: 500, padding: 0.3 }), 50) }}
                  >{sidebar.sourceId}</span>
                  <span className="text-gray-600 text-xs flex-shrink-0">→</span>
                  <span
                    className="text-blue-300 font-mono text-xs cursor-pointer hover:text-white truncate flex-1 text-right"
                    onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.targetNodeId }], duration: 500, padding: 0.3 }), 50) }}
                  >{sidebar.targetId}</span>
                </div>
                <SidebarSection title={`함수 호출 체인${sidebar.callChain.length > 0 ? ` (${sidebar.callChain.length})` : ''}`}>
                  {sidebar.callChain.length === 0
                    ? <p className="text-gray-700 text-xs">분석된 함수 호출 없음</p>
                    : sidebar.callChain.map((e, i) => (
                      <CallChainRow key={i}
                        leftLabel={e.callerLabel} leftNodeId={e.callerNodeId}
                        rightLabel={e.calleeLabel} rightNodeId={e.calleeNodeId}
                        onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                      />
                    ))
                  }
                </SidebarSection>
              </>
            )}

            {/* ── 파일 연결 보기 ── */}
            {sidebar.kind === 'file' && (
              <>
                <div>
                  <p className="text-white font-mono font-semibold text-sm">{sidebar.data.name}</p>
                  {sidebar.data.comment && <p className="text-gray-500 text-xs mt-0.5">{sidebar.data.comment}</p>}
                </div>
                <SidebarSection title={`들어오는 연결 (${sidebar.data.incoming.length})`}>
                  {sidebar.data.incoming.length === 0
                    ? <p className="text-gray-700 text-xs">없음</p>
                    : sidebar.data.incoming.map((c, i) => (
                      <FileConnGroup key={i} entry={c} direction="in"
                        onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                      />
                    ))
                  }
                </SidebarSection>
                <SidebarSection title={`나가는 연결 (${sidebar.data.outgoing.length})`}>
                  {sidebar.data.outgoing.length === 0
                    ? <p className="text-gray-700 text-xs">없음</p>
                    : sidebar.data.outgoing.map((c, i) => (
                      <FileConnGroup key={i} entry={c} direction="out"
                        onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                      />
                    ))
                  }
                </SidebarSection>
              </>
            )}

            {/* ── 함수 노드 클릭 ── */}
            {sidebar.kind === 'func' && (
              <>
                <div>
                  <p className="text-white font-mono font-semibold text-sm">{sidebar.funcName}</p>
                  {sidebar.funcComment && <p className="text-gray-500 text-xs mt-0.5">{sidebar.funcComment}</p>}
                  <p
                    className="text-blue-400 font-mono text-xs mt-1 cursor-pointer hover:text-blue-300 underline decoration-gray-700"
                    onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.parentFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                  >{sidebar.parentFileName}</p>
                </div>
                <SidebarSection title={`호출하는 함수${sidebar.callers.length > 0 ? ` (${sidebar.callers.length})` : ''}`}>
                  {sidebar.callers.length === 0
                    ? <p className="text-gray-700 text-xs">없음</p>
                    : sidebar.callers.map((c, i) => (
                      <FuncChainRow key={i} entry={c} direction="caller"
                        onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                      />
                    ))
                  }
                </SidebarSection>
                <SidebarSection title={`호출받는 함수${sidebar.callees.length > 0 ? ` (${sidebar.callees.length})` : ''}`}>
                  {sidebar.callees.length === 0
                    ? <p className="text-gray-700 text-xs">없음</p>
                    : sidebar.callees.map((c, i) => (
                      <FuncChainRow key={i} entry={c} direction="callee"
                        onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                      />
                    ))
                  }
                </SidebarSection>
              </>
            )}

          </div>
        </aside>
      )}
    </div>
  )
}

// 섹션 헤더
function SidebarSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-2">{title}</p>
      <div className="flex flex-col gap-1">{children}</div>
    </div>
  )
}

// 파일 연결 항목 — 파일명 + 콜 체인
function FileConnGroup({ entry, direction, onNav }: {
  entry: ConnEntry; direction: 'in' | 'out'; onNav: (id: string) => void
}) {
  const accent = direction === 'in' ? '#3b82f6' : '#10b981'
  return (
    <div className="bg-gray-900 rounded-lg p-2.5 mb-1">
      <div className="flex items-center gap-1.5 mb-1.5">
        <span className="text-[9px]" style={{ color: accent }}>{direction === 'in' ? '◀' : '▶'}</span>
        <span
          className="text-blue-300 font-mono text-xs font-semibold cursor-pointer hover:text-white truncate"
          onClick={() => onNav(entry.nodeId)}
        >{entry.name}</span>
      </div>
      {entry.callChain.length > 0 ? (
        <div className="flex flex-col gap-1 pl-3">
          {entry.callChain.map((c, i) => (
            <CallChainRow key={i}
              leftLabel={c.callerLabel} leftNodeId={c.callerNodeId}
              rightLabel={c.calleeLabel} rightNodeId={c.calleeNodeId}
              onNav={onNav}
            />
          ))}
        </div>
      ) : (
        <p className="text-gray-700 text-[11px] pl-3">함수 호출 없음</p>
      )}
    </div>
  )
}

// 콜 체인 한 줄 — 출발함수 → 도착함수
function CallChainRow({ leftLabel, leftNodeId, rightLabel, rightNodeId, onNav }: {
  leftLabel: string; leftNodeId: string; rightLabel: string; rightNodeId: string
  onNav: (id: string) => void
}) {
  return (
    <div className="flex items-center gap-1.5 bg-gray-800 rounded px-2.5 py-1.5 text-[11px]">
      <span className="text-emerald-400 font-mono cursor-pointer hover:text-emerald-200 truncate flex-1"
        onClick={() => onNav(leftNodeId)}>{leftLabel}</span>
      <span className="text-amber-500 flex-shrink-0">→</span>
      <span className="text-emerald-400 font-mono cursor-pointer hover:text-emerald-200 truncate flex-1 text-right"
        onClick={() => onNav(rightNodeId)}>{rightLabel}</span>
    </div>
  )
}

// 함수 콜 체인 한 줄
function FuncChainRow({ entry, direction, onNav }: {
  entry: FuncCallChainEntry; direction: 'caller' | 'callee'; onNav: (id: string) => void
}) {
  return (
    <div className="flex items-center gap-2 bg-gray-900 rounded-lg px-2.5 py-2 text-xs">
      <span className="text-amber-500 flex-shrink-0">{direction === 'caller' ? '←' : '→'}</span>
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-emerald-400 font-mono cursor-pointer hover:text-emerald-200 truncate"
          onClick={() => onNav(entry.funcNodeId)}
        >{entry.funcComment ?? entry.funcName}</span>
        <span className="text-gray-600 font-mono text-[10px] cursor-pointer hover:text-gray-400 truncate"
          onClick={() => onNav(entry.fileNodeId)}
        >{entry.fileName}</span>
      </div>
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

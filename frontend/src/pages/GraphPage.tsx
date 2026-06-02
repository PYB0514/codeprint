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

// 전체 흐름 추적 — 각 단계 노드 정보
interface FlowStep {
  nodeId: string
  label: string        // 함수 주석 또는 파일명
  subLabel?: string    // 함수일 경우 소속 파일명
  subNodeId?: string   // 함수일 경우 파일 노드 ID
  isSource: boolean    // 클릭한 엣지의 출발 노드
  isTarget: boolean    // 클릭한 엣지의 도착 노드
  altCount?: number    // 이 지점에서 다른 분기 수
}

// 사이드바 콘텐츠 — 엣지 클릭 / 파일 연결 보기 / 함수 노드 클릭 세 종류
type SidebarContent =
  | { kind: 'edge'; sourceId: string; targetId: string; sourceNodeId: string; targetNodeId: string; callChain: ConnEntry['callChain']; flowChain: FlowStep[] }
  | { kind: 'file'; data: FileSidebarData }
  | { kind: 'func'; funcName: string; funcComment: string | null; parentFileName: string; parentFileNodeId: string; callers: FuncCallChainEntry[]; callees: FuncCallChainEntry[] }
  | { kind: 'func-call'; callerName: string; callerComment: string | null; callerNodeId: string; callerFile: string; callerFileNodeId: string; calleeName: string; calleeComment: string | null; calleeNodeId: string; calleeFile: string; calleeFileNodeId: string; flowChain: FlowStep[] }
  | { kind: 'instantiation'; sourceFile: string; sourceNodeId: string; targetClass: string; targetNodeId: string; flowChain: FlowStep[] }

// 동일 타입 엣지를 따라 upstream·downstream을 추적하여 전체 흐름 반환
function traceFlow(
  sourceId: string,
  targetId: string,
  edgeType: string,
  rawEdges: RawEdge[],
  rawNodes: RawNode[],
): FlowStep[] {
  const MAX_DEPTH = 15

  const makeStep = (id: string, isSource: boolean, isTarget: boolean, altCount?: number): FlowStep => {
    const node = rawNodes.find((n) => n.id === id)
    if (!node) return { nodeId: id, label: id, isSource, isTarget }
    if (node.type === 'FUNCTION') {
      const file = rawNodes.find((n) => n.type === 'FILE' && n.filePath === node.filePath)
      return { nodeId: id, label: node.comment ?? node.name, subLabel: file?.name, subNodeId: file?.id, isSource, isTarget, altCount }
    }
    return { nodeId: id, label: node.comment ?? node.name, isSource, isTarget, altCount }
  }

  // 역방향 추적 — source에서 root로
  const upstream: FlowStep[] = []
  const visitedUp = new Set<string>([sourceId])
  let cur = sourceId
  for (let i = 0; i < MAX_DEPTH; i++) {
    const incoming = rawEdges.filter((e) => e.type === edgeType && e.target === cur)
    if (incoming.length === 0) break
    const nextId = incoming[0].source
    if (visitedUp.has(nextId)) break
    visitedUp.add(nextId)
    upstream.unshift(makeStep(nextId, false, false, incoming.length > 1 ? incoming.length - 1 : undefined))
    cur = nextId
  }

  // 순방향 추적 — target에서 leaf로
  const downstream: FlowStep[] = []
  const visitedDown = new Set<string>([targetId])
  cur = targetId
  for (let i = 0; i < MAX_DEPTH; i++) {
    const outgoing = rawEdges.filter((e) => e.type === edgeType && e.source === cur)
    if (outgoing.length === 0) break
    const nextId = outgoing[0].target
    if (visitedDown.has(nextId)) break
    visitedDown.add(nextId)
    downstream.push(makeStep(nextId, false, false, outgoing.length > 1 ? outgoing.length - 1 : undefined))
    cur = nextId
  }

  return [...upstream, makeStep(sourceId, true, false), makeStep(targetId, false, true), ...downstream]
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
  const [sidebar, setSidebar] = useState<SidebarContent | null>(null)
  const [rightCollapsed, setRightCollapsed] = useState(false)
  const [leftOpen, setLeftOpen] = useState(true)
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [opaqueLayerSet, setOpaqueLayerSet] = useState<Set<string>>(new Set())
  const [showEdges, setShowEdges] = useState(false)
  const [showCallEdges, setShowCallEdges] = useState(false)
  const [showInstEdges, setShowInstEdges] = useState(false)
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const { getNodes, fitView } = useReactFlow()

  // 엣지 타입별 초기 hidden 상태 적용
  const applyEdgeVisibility = useCallback((edges: Edge[], se: boolean, sc: boolean, si: boolean) =>
    edges.map((e) => {
      const t = (e.data as { type?: string })?.type
      const hidden = t === 'IMPORT' ? !se : t === 'FUNCTION_CALL' ? !sc : t === 'INSTANTIATION' ? !si : false
      return { ...e, hidden }
    }), [])

  // 레이어 섹션 박스를 해당 색상으로 덮어 내용을 가리는 토글
  const toggleLayerOpaque = useCallback((layer: string) => {
    setOpaqueLayerSet((prev) => {
      const next = new Set(prev)
      if (next.has(layer)) next.delete(layer)
      else next.add(layer)
      const isOpaque = next.has(layer)
      setNodes((nds) => nds.map((n) => {
        if (n.id !== `layer-section-${layer}`) return n
        return {
          ...n,
          zIndex: isOpaque ? 9999 : -20,
          data: { ...n.data, opaque: isOpaque },
        }
      }))
      return next
    })
  }, [setNodes])

  // 파일 연결 보기 — 사이드바 오픈 콜백
  const openFileSidebar = useCallback((data: FileSidebarData) => {
    setSidebar({ kind: 'file', data })
    setRightCollapsed(false)
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
      setEdges(applyEdgeVisibility(layoutEdges, false, false, false))
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
  }, [projectId, setNodes, setEdges, openFileSidebar, applyEdgeVisibility])

  useEffect(() => { fetchGraph() }, [fetchGraph])

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = useCallback(() => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, next, layoutPreset, openFileSidebar)
      setNodes(layoutNodes)
      setEdges(applyEdgeVisibility(layoutEdges, showEdges, showCallEdges, showInstEdges))
    }
  }, [labelMode, layoutPreset, rawNodes, rawEdgesCache, setNodes, setEdges, openFileSidebar, showEdges, showCallEdges, showInstEdges, applyEdgeVisibility])

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

  // 레이아웃 프리셋 전환 — 그래프를 재계산하여 적용
  const toggleLayoutPreset = useCallback(() => {
    const next: LayoutPreset = layoutPreset === 'layer' ? 'hub' : 'layer'
    setLayoutPreset(next)
    if (rawNodes.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodes, rawEdgesCache, labelMode, next, openFileSidebar)
      setNodes(ln)
      setEdges(applyEdgeVisibility(le, showEdges, showCallEdges, showInstEdges))
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }, [layoutPreset, rawNodes, rawEdgesCache, labelMode, setNodes, setEdges, fitView, openFileSidebar, showEdges, showCallEdges, showInstEdges, applyEdgeVisibility])

  // 전체 그래프를 원본 크기 PNG로 다운로드
  const handleExportImage = useCallback(async () => {
    const flowEl = flowRef.current?.querySelector('.react-flow__viewport') as HTMLElement | null
    if (!flowEl) return

    setExporting(true)
    try {
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
        style: { transform: `translate(${PAD - minX}px, ${PAD - minY}px)` },
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
    setRightCollapsed(false)
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
        flowChain: traceFlow(edge.source, edge.target, 'FUNCTION_CALL', rawEdgesCache, rawNodes),
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
        flowChain: traceFlow(edge.source, edge.target, 'INSTANTIATION', rawEdgesCache, rawNodes),
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
      flowChain: traceFlow(edge.source, edge.target, 'IMPORT', rawEdgesCache, rawNodes),
    })
  }, [rawNodes, rawEdgesCache])

  // 함수 노드 클릭 시 사이드바에 콜 체인 표시
  const handleNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    if (node.type === 'fileNode' || node.type === 'groupNode' || node.type === 'sectionNode') return
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
    setRightCollapsed(false)
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

      {/* 상단 바 — 내비 + 통계만 */}
      <div className="absolute top-4 z-10 flex items-center gap-3" style={{ left: leftOpen ? '228px' : '20px' }}>
        <button
          onClick={() => navigate('/dashboard')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          ← 대시보드
        </button>
        <span className="text-gray-500 text-sm">
          파일 {counts.files} · 함수 {counts.funcs} · 엣지 {counts.edges}
        </span>
      </div>

      {/* 왼쪽 사이드바 여는 탭 — 닫혔을 때만 표시 */}
      {!leftOpen && (
        <button
          onClick={() => setLeftOpen(true)}
          className="absolute left-0 top-1/2 -translate-y-1/2 z-20 bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white rounded-r-lg px-1 py-3 text-xs"
        >
          ›
        </button>
      )}

      {/* 왼쪽 사이드바 */}
      {leftOpen && (
        <aside className="absolute left-0 top-0 h-full z-20 flex flex-col bg-gray-950 border-r border-gray-800 shadow-xl overflow-y-auto" style={{ width: '220px' }}>

          {/* 사이드바 헤더 */}
          <div className="flex items-center justify-between px-3 py-3 border-b border-gray-800 flex-shrink-0">
            <button
              onClick={() => navigate('/dashboard')}
              className="text-xs font-bold text-gray-300 hover:text-white tracking-widest uppercase transition-colors"
            >
              Codeprint
            </button>
            <button onClick={() => setLeftOpen(false)} className="text-gray-600 hover:text-white text-sm leading-none" title="사이드바 접기">‹</button>
          </div>

          <div className="flex flex-col gap-0 flex-1">

            {/* 레이아웃 */}
            <LeftSection title="레이아웃">
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">프리셋</span>
                <button
                  onClick={toggleLayoutPreset}
                  className="flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2 py-1 rounded border border-gray-700"
                >
                  <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>계층</span>
                  <span className="text-gray-600">/</span>
                  <span className={layoutPreset === 'hub' ? 'text-white' : 'text-gray-500'}>허브</span>
                </button>
              </div>
            </LeftSection>

            {/* 라벨 */}
            <LeftSection title="라벨">
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">표시 모드</span>
                <button
                  onClick={toggleLabelMode}
                  className="flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2 py-1 rounded border border-gray-700"
                >
                  <span className={labelMode === 'name' ? 'text-white' : 'text-gray-500'}>이름</span>
                  <span className="text-gray-600">/</span>
                  <span className={labelMode === 'comment' ? 'text-white' : 'text-gray-500'}>주석</span>
                </button>
              </div>
            </LeftSection>

            {/* 엣지 토글 — 범례와 연동 */}
            <LeftSection title="엣지">
              <button
                onClick={toggleEdges}
                className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded hover:bg-gray-800/60 group"
              >
                <span className="w-4 flex-shrink-0">
                  <span className="block w-4 h-0.5" style={{ background: showEdges ? '#4b5563' : '#374151' }} />
                </span>
                <span className={`text-xs flex-1 ${showEdges ? 'text-gray-300' : 'text-gray-600'}`}>IMPORT</span>
                <ToggleChip active={showEdges} onClick={toggleEdges} stopPropagation />
              </button>
              <button
                onClick={toggleCallEdges}
                className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded hover:bg-gray-800/60"
              >
                <span className="w-4 flex-shrink-0">
                  <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showCallEdges ? '#f59e0b' : '#78350f'} strokeWidth="1.5" strokeDasharray="4 3" /></svg>
                </span>
                <span className={`text-xs flex-1 ${showCallEdges ? 'text-amber-400' : 'text-gray-600'}`}>콜 체인</span>
                <ToggleChip active={showCallEdges} onClick={toggleCallEdges} stopPropagation />
              </button>
              <button
                onClick={toggleInstEdges}
                className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded hover:bg-gray-800/60"
              >
                <span className="w-4 flex-shrink-0">
                  <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showInstEdges ? '#a855f7' : '#4c1d95'} strokeWidth="1.5" strokeDasharray="3 4" /></svg>
                </span>
                <span className={`text-xs flex-1 ${showInstEdges ? 'text-purple-400' : 'text-gray-600'}`}>생성</span>
                <ToggleChip active={showInstEdges} onClick={toggleInstEdges} stopPropagation />
              </button>
            </LeftSection>

            {/* 내보내기 */}
            <LeftSection title="내보내기">
              <button
                onClick={() => downloadTreeText(rawNodes)}
                disabled={rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed"
                title="파일명 — 한국어 주석 형태의 AI 컨텍스트용 트리 다운로드"
              >
                ↓ AI 컨텍스트
              </button>
              <button
                onClick={handleExportImage}
                disabled={exporting || rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed mt-1"
                title="전체 그래프를 원본 크기 PNG로 저장"
              >
                {exporting ? '저장 중...' : '↓ 이미지'}
              </button>
            </LeftSection>

            {/* 범례 */}
            <LeftSection title="범례">
              <p className="text-[9px] text-gray-600 uppercase tracking-wider mb-1">DDD 레이어</p>
              {[
                { label: 'Domain',           color: '#3b82f6', key: 'domain' },
                { label: 'Application',      color: '#eab308', key: 'application' },
                { label: 'Infrastructure',   color: '#a855f7', key: 'infrastructure' },
                { label: 'Interfaces',       color: '#10b981', key: 'interfaces' },
                { label: 'Pages/Components', color: '#06b6d4', key: 'pages' },
              ].map(({ label, color, key }) => {
                const active = opaqueLayerSet.has(key)
                return (
                  <div key={key} className="flex items-center gap-2 py-0.5">
                    <button
                      onClick={() => toggleLayerOpaque(key)}
                      title={active ? '내용 표시' : '내용 가리기'}
                      style={{
                        width: 18, height: 18, borderRadius: 4,
                        border: `1px solid ${color}88`,
                        background: active ? color : `${color}22`,
                        color: active ? '#fff' : color,
                        fontSize: 10, cursor: 'pointer',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      {active ? '◑' : '○'}
                    </button>
                    <span className="text-gray-400 text-xs">{label}</span>
                  </div>
                )
              })}
              <div className="border-t border-gray-800 my-2" />
              <p className="text-[9px] text-gray-600 uppercase tracking-wider mb-1">노드</p>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#1e3a5f', border: '1.5px solid #3b82f6' }} />
                <span className="text-gray-400 text-xs">FILE</span>
              </div>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#064e3b', border: '1px solid #10b981' }} />
                <span className="text-gray-400 text-xs">FUNCTION</span>
              </div>
              <div className="border-t border-gray-800 my-2" />
              <p className="text-[9px] text-gray-600 uppercase tracking-wider mb-1">엣지</p>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-4 h-0.5 flex-shrink-0" style={{ background: '#4b5563' }} />
                <span className="text-gray-400 text-xs">IMPORT</span>
              </div>
              <div className="flex items-center gap-2 py-0.5">
                <svg width="16" height="4" className="flex-shrink-0"><line x1="0" y1="2" x2="16" y2="2" stroke="#f59e0b" strokeWidth="1.5" strokeDasharray="4 3" /></svg>
                <span className="text-amber-400 text-xs">FUNCTION_CALL</span>
              </div>
              <div className="flex items-center gap-2 py-0.5">
                <svg width="16" height="4" className="flex-shrink-0"><line x1="0" y1="2" x2="16" y2="2" stroke="#a855f7" strokeWidth="1.5" strokeDasharray="3 4" /></svg>
                <span className="text-purple-400 text-xs">INSTANTIATION</span>
              </div>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-4 h-0.5 flex-shrink-0" style={{ background: '#ef4444' }} />
                <span className="text-gray-400 text-xs">끊긴 연결</span>
              </div>
            </LeftSection>
          </div>
        </aside>
      )}

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
        <aside
          className="fixed right-0 top-0 h-full bg-gray-950 border-l border-gray-800 z-40 flex flex-col shadow-2xl transition-all duration-200"
          style={{ width: rightCollapsed ? '40px' : '320px' }}
        >
          {/* 사이드바 collapse 핸들 */}
          <button
            onClick={() => setRightCollapsed((v) => !v)}
            className="absolute -left-3 top-1/2 -translate-y-1/2 bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white rounded-full w-6 h-6 flex items-center justify-center text-xs z-10"
            title={rightCollapsed ? '사이드바 펼치기' : '사이드바 접기'}
          >
            {rightCollapsed ? '‹' : '›'}
          </button>

          {!rightCollapsed && (
            <>
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
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#f59e0b"
                      onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                    />
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
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#a855f7"
                      onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
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
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#4b5563"
                      onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
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
            </>
          )}
        </aside>
      )}
    </div>
  )
}

// 왼쪽 사이드바 섹션
function LeftSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="px-3 py-3 border-b border-gray-800/60">
      <p className="text-[9px] font-semibold text-gray-600 uppercase tracking-widest mb-2">{title}</p>
      {children}
    </div>
  )
}

// on/off 상태 표시 칩
function ToggleChip({ active, onClick, stopPropagation }: { active: boolean; onClick: () => void; stopPropagation?: boolean }) {
  return (
    <button
      onClick={(e) => { if (stopPropagation) e.stopPropagation(); onClick() }}
      className={`text-[10px] px-1.5 py-0.5 rounded font-mono flex-shrink-0 ${active ? 'bg-emerald-900/60 text-emerald-400' : 'bg-gray-800 text-gray-600'}`}
    >
      {active ? 'ON' : 'OFF'}
    </button>
  )
}

// 우측 사이드바 섹션 헤더
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

// 전체 흐름 — 업스트림·다운스트림 포함 체인 세로 목록
function FlowChainSection({ steps, edgeColor, onNav }: {
  steps: FlowStep[]
  edgeColor: string
  onNav: (id: string) => void
}) {
  if (steps.length <= 2) return null  // source·target만 있으면 표시 의미 없음
  return (
    <div>
      <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-2">전체 흐름</p>
      <div className="flex flex-col">
        {steps.map((step, i) => {
          const isLast = i === steps.length - 1
          const highlighted = step.isSource || step.isTarget
          return (
            <div key={step.nodeId} className="flex flex-col">
              {/* 노드 카드 */}
              <div
                className={`flex flex-col px-2.5 py-1.5 rounded-lg cursor-pointer transition-colors ${
                  highlighted
                    ? 'bg-gray-700/80 border border-gray-600'
                    : 'bg-gray-900/60 hover:bg-gray-800/60'
                }`}
                onClick={() => onNav(step.nodeId)}
              >
                <span className={`font-mono text-xs truncate ${highlighted ? 'text-white font-semibold' : 'text-gray-300'}`}>
                  {step.label}
                  {highlighted && (
                    <span className="ml-1.5 text-[9px] font-normal" style={{ color: edgeColor }}>
                      {step.isSource ? '▶' : '◀'}
                    </span>
                  )}
                </span>
                {step.subLabel && (
                  <span
                    className="font-mono text-[10px] text-gray-600 truncate cursor-pointer hover:text-gray-400"
                    onClick={(e) => { e.stopPropagation(); if (step.subNodeId) onNav(step.subNodeId) }}
                  >
                    {step.subLabel}
                  </span>
                )}
                {step.altCount && (
                  <span className="text-[9px] text-gray-600 mt-0.5">+{step.altCount}개 다른 경로</span>
                )}
              </div>
              {/* 화살표 */}
              {!isLast && (
                <div className="flex items-center gap-1 py-0.5 pl-3">
                  <span className="text-xs" style={{ color: edgeColor }}>↓</span>
                </div>
              )}
            </div>
          )
        })}
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

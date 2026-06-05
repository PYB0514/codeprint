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
import { buildLayout, downloadTreeText, getGroupKey, findCommonPrefix } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset, FileSidebarData, ConnEntry, FuncCallEntry, ColumnInfo } from '../utils/graphLayout'
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
  | { kind: 'db-table'; tableName: string; nodeId: string; columns: ColumnInfo[]; repos: { name: string; id: string; crudTypes: string[] }[] }
  | { kind: 'db-edge'; crudType: string; repoFile: string; repoFileNodeId: string; tableName: string; tableNodeId: string; flowChain: FlowStep[] }
  | { kind: 'api-call'; frontFile: string; frontFileNodeId: string; ctrlFile: string; ctrlFileNodeId: string; flowChain: FlowStep[] }

// DB 엣지 타입 판별 — 신규 CRUD 타입 + 레거시 DB_WRITE 포함
const DB_EDGE_TYPES = new Set(['DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE'])
function isDbEdgeType(t: string | undefined): boolean {
  return DB_EDGE_TYPES.has(t ?? '')
}

// CRUD 타입별 색상 — graphLayout.ts의 DB_EDGE_COLORS와 동기화
const DB_CRUD_COLOR: Record<string, string> = {
  DB_READ:   '#22d3ee',
  DB_CREATE: '#4ade80',
  DB_UPDATE: '#facc15',
  DB_DELETE: '#f87171',
  DB_WRITE:  '#f97316',
}

// 엣지 타입 경계를 넘어 전체 데이터 흐름을 추적 (프론트엔드 API_CALL ~ FUNCTION_CALL ~ DB까지)
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

  // FUNCTION 노드의 부모 FILE 반환
  const parentFileOf = (nodeId: string): RawNode | undefined => {
    const n = rawNodes.find((n) => n.id === nodeId)
    if (!n || n.type !== 'FUNCTION') return undefined
    return rawNodes.find((f) => f.type === 'FILE' && f.filePath === n.filePath)
  }

  // FUNCTION_CALL 역방향 추적 — 끝에서 부모 FILE에 API_CALL이 있으면 프론트엔드까지 연결
  const traceFuncCallUp = (startId: string, seed: Set<string>): FlowStep[] => {
    const steps: FlowStep[] = []
    const visited = new Set(seed)
    let cur = startId
    for (let i = 0; i < MAX_DEPTH; i++) {
      const incoming = rawEdges.filter((e) => e.type === 'FUNCTION_CALL' && e.target === cur)
      if (incoming.length === 0) {
        const pf = parentFileOf(cur)
        if (pf) {
          const apiEdge = rawEdges.find((e) => e.type === 'API_CALL' && e.target === pf.id)
          if (apiEdge && !visited.has(apiEdge.source)) steps.unshift(makeStep(apiEdge.source, false, false))
        }
        break
      }
      const nextId = incoming[0].source
      if (visited.has(nextId)) break
      visited.add(nextId)
      steps.unshift(makeStep(nextId, false, false, incoming.length > 1 ? incoming.length - 1 : undefined))
      cur = nextId
    }
    return steps
  }

  // FUNCTION_CALL 순방향 추적 — 끝에서 부모 FILE에 DB 엣지가 있으면 DB_TABLE까지 연결
  const traceFuncCallDown = (startId: string, seed: Set<string>): FlowStep[] => {
    const steps: FlowStep[] = []
    const visited = new Set(seed)
    let cur = startId
    for (let i = 0; i < MAX_DEPTH; i++) {
      const outgoing = rawEdges.filter((e) => e.type === 'FUNCTION_CALL' && e.source === cur)
      if (outgoing.length === 0) {
        const pf = parentFileOf(cur)
        if (pf) {
          const dbEdge = rawEdges.find((e) => isDbEdgeType(e.type) && e.source === pf.id)
          if (dbEdge && !visited.has(dbEdge.target)) steps.push(makeStep(dbEdge.target, false, false))
        }
        break
      }
      const nextId = outgoing[0].target
      if (visited.has(nextId)) break
      visited.add(nextId)
      steps.push(makeStep(nextId, false, false, outgoing.length > 1 ? outgoing.length - 1 : undefined))
      cur = nextId
    }
    return steps
  }

  // DB 엣지: sourceId=Repository FILE → 이 파일 함수의 FUNCTION_CALL 호출자를 역추적
  if (isDbEdgeType(edgeType)) {
    const repoFile = rawNodes.find((n) => n.id === sourceId && n.type === 'FILE')
    const upstreamSteps: FlowStep[] = []
    if (repoFile) {
      const repoFuncIds = new Set(
        rawNodes.filter((n) => n.type === 'FUNCTION' && n.filePath === repoFile.filePath).map((n) => n.id)
      )
      const callerEdge = rawEdges.find((e) => e.type === 'FUNCTION_CALL' && repoFuncIds.has(e.target))
      if (callerEdge) {
        const seed = new Set([sourceId, targetId, callerEdge.target, callerEdge.source])
        const above = traceFuncCallUp(callerEdge.source, seed)
        upstreamSteps.push(...above, makeStep(callerEdge.source, false, false), makeStep(callerEdge.target, false, false))
      }
    }
    return [...upstreamSteps, makeStep(sourceId, true, false), makeStep(targetId, false, true)]
  }

  // FUNCTION_CALL: upstream은 API_CALL 경계까지, downstream은 DB 경계까지
  if (edgeType === 'FUNCTION_CALL') {
    const upstream = traceFuncCallUp(sourceId, new Set([sourceId, targetId]))
    const downstream = traceFuncCallDown(targetId, new Set([sourceId, targetId]))
    return [...upstream, makeStep(sourceId, true, false), makeStep(targetId, false, true), ...downstream]
  }

  // IMPORT / INSTANTIATION / API_CALL / 기타: 동일 타입 엣지만 따라감
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

// 노드를 중심으로 upstream·downstream 흐름 경로를 탐색하여 순서 배열로 반환
function buildFlowPath(
  nodeId: string,
  rawEdges: RawEdge[],
): { items: { type: 'node' | 'edge'; id: string }[] } {
  const FLOW_TYPES = ['FUNCTION_CALL', 'DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE', 'API_CALL', 'CONTAINS']
  const MAX_DEPTH = 15

  // upstream 역방향 추적
  const upstreamNodes: string[] = []
  const upstreamEdgeIds: string[] = []
  const visitedUp = new Set([nodeId])
  let cur = nodeId
  for (let i = 0; i < MAX_DEPTH; i++) {
    const edge = rawEdges.find((e) => FLOW_TYPES.includes(e.type) && e.target === cur)
    if (!edge || visitedUp.has(edge.source)) break
    visitedUp.add(edge.source)
    upstreamNodes.unshift(edge.source)
    upstreamEdgeIds.unshift(edge.id)
    cur = edge.source
  }

  // downstream 순방향 추적
  const downstreamNodes: string[] = []
  const downstreamEdgeIds: string[] = []
  const visitedDown = new Set([nodeId])
  cur = nodeId
  for (let i = 0; i < MAX_DEPTH; i++) {
    const edge = rawEdges.find((e) => FLOW_TYPES.includes(e.type) && e.source === cur)
    if (!edge || visitedDown.has(edge.target)) break
    visitedDown.add(edge.target)
    downstreamNodes.push(edge.target)
    downstreamEdgeIds.push(edge.id)
    cur = edge.target
  }

  const nodeIds = [...upstreamNodes, nodeId, ...downstreamNodes]
  const edgeIds = [...upstreamEdgeIds, ...downstreamEdgeIds]

  // 노드와 엣지를 교차 배치: [node, edge, node, edge, ..., node]
  const items: { type: 'node' | 'edge'; id: string }[] = []
  for (let i = 0; i < nodeIds.length; i++) {
    items.push({ type: 'node', id: nodeIds[i] })
    if (i < edgeIds.length) items.push({ type: 'edge', id: edgeIds[i] })
  }
  return { items }
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
  const [leftWidth, setLeftWidth] = useState(220)
  const [rightWidth, setRightWidth] = useState(320)
  const leftResizing = useRef(false)
  const rightResizing = useRef(false)
  const dragStartX = useRef(0)
  const dragStartWidth = useRef(0)
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [opaqueLayerSet, setOpaqueLayerSet] = useState<Set<string>>(new Set())
  const [showEdges, setShowEdges] = useState(false)
  const [showCallEdges, setShowCallEdges] = useState(false)
  const [showInstEdges, setShowInstEdges] = useState(false)
  const [showBrokenEdges, setShowBrokenEdges] = useState(true)
  const [showDbEdges, setShowDbEdges] = useState(false)
  const [showApiCallEdges, setShowApiCallEdges] = useState(true)
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [showVersions, setShowVersions] = useState(false)
  const [versions, setVersions] = useState<{ graphId: string; createdAt: string; branch: string }[]>([])
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [outdated, setOutdated] = useState<{ branch: string; lastAnalyzedAt: string } | null>(null)
  const [showShareModal, setShowShareModal] = useState(false)
  const [shareTitle, setShareTitle] = useState('')
  const [shareContent, setShareContent] = useState('')
  const [shareFeedbackType, setShareFeedbackType] = useState('GENERAL')
  const [shareHiddenLayers, setShareHiddenLayers] = useState<Set<string>>(new Set())
  const [shareHiddenGroups, setShareHiddenGroups] = useState<Set<string>>(new Set())
  const [shareHiddenNodes, setShareHiddenNodes] = useState<Set<string>>(new Set())
  const [shareSubmitting, setShareSubmitting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const { getNodes, fitView } = useReactFlow()

  // 흐름 재생 상태
  const [playbackItems, setPlaybackItems] = useState<{ type: 'node' | 'edge'; id: string }[]>([])
  const [playbackCursor, setPlaybackCursor] = useState(-1)
  const [playbackPlaying, setPlaybackPlaying] = useState(false)
  const [playbackSpeed, setPlaybackSpeed] = useState(600)
  const playbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 노드 코멘트 상태
  const [nodeComments, setNodeComments] = useState<{ id: string; userId: string; content: string; createdAt: number }[]>([])
  const [commentInput, setCommentInput] = useState('')
  const [commentNodeId, setCommentNodeId] = useState<string | null>(null)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)

  // 엣지 타입별 초기 hidden 상태 적용
  const applyEdgeVisibility = useCallback((edges: Edge[], se: boolean, sc: boolean, si: boolean, sb: boolean, sdb: boolean, sapi: boolean) =>
    edges.map((e) => {
      const d = e.data as { type?: string; broken?: boolean } | undefined
      const t = d?.type
      const broken = d?.broken
      const hidden =
        t === 'IMPORT' && broken ? !sb :
        t === 'IMPORT' ? !se :
        t === 'FUNCTION_CALL' ? !sc :
        t === 'INSTANTIATION' ? !si :
        isDbEdgeType(t) ? !sdb :
        t === 'API_CALL' ? !sapi :
        false
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

  // 사이드바 드래그 리사이즈 — 전역 mousemove/mouseup 처리
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (leftResizing.current) {
        const delta = e.clientX - dragStartX.current
        setLeftWidth(Math.min(420, Math.max(160, dragStartWidth.current + delta)))
      }
      if (rightResizing.current) {
        const delta = dragStartX.current - e.clientX
        setRightWidth(Math.min(520, Math.max(240, dragStartWidth.current + delta)))
      }
    }
    const onUp = () => { leftResizing.current = false; rightResizing.current = false }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    return () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  }, [])

  // 흐름 재생 — 커서 이동 시 노드/엣지 하이라이트 적용
  useEffect(() => {
    if (playbackItems.length === 0) return

    // 커서까지 지나온 항목만 하이라이트 (미도달 항목은 숨김 유지)
    const visitedItems = playbackItems.slice(0, playbackCursor + 1)
    const pathNodeIds = new Set(visitedItems.filter((it) => it.type === 'node').map((it) => it.id))
    const activeNodeId = playbackItems[playbackCursor]?.type === 'node' ? playbackItems[playbackCursor].id : null
    const pathEdgeIds = new Set(visitedItems.filter((it) => it.type === 'edge').map((it) => it.id))
    const activeEdgeId = playbackItems[playbackCursor]?.type === 'edge' ? playbackItems[playbackCursor].id : null

    // 노드 스타일 업데이트 — FileNode(data prop) + 기본 노드(style 직접)
    setNodes((nds) => nds.map((n) => {
      if (!pathNodeIds.has(n.id)) {
        return { ...n, data: { ...n.data, playbackActive: false, playbackInPath: false } }
      }
      const isActive = n.id === activeNodeId
      const isInPath = !isActive
      // 기본 React Flow 노드 (FUNCTION) — style로 직접 테두리 적용
      const baseStyle = n.style ?? {}
      const playbackStyle = isActive
        ? { ...baseStyle, outline: '2px solid #fbbf24', outlineOffset: '2px', boxShadow: '0 0 10px #fbbf2488' }
        : isInPath
        ? { ...baseStyle, outline: '1px solid #22d3ee66', outlineOffset: '2px' }
        : baseStyle
      return { ...n, style: playbackStyle, data: { ...n.data, playbackActive: isActive, playbackInPath: isInPath } }
    }))

    // 엣지 — 경로 엣지는 hidden 해제 + 색상 강조
    setEdges((eds) => eds.map((e) => {
      const d = e.data as { type?: string; broken?: boolean } | undefined
      const isCall = d?.type === 'FUNCTION_CALL'
      const isInst = d?.type === 'INSTANTIATION'
      const broken = d?.broken
      const isDb = isDbEdgeType(d?.type)
      const isApiCall = d?.type === 'API_CALL'
      const baseStyle = { strokeWidth: (isCall || isInst) ? 1.2 : broken ? 2 : 1.5, stroke: broken ? '#ef4444' : isCall ? '#f59e0b' : isInst ? '#a855f7' : isApiCall ? '#e879f9' : isDb ? (DB_CRUD_COLOR[d?.type ?? ''] ?? '#22d3ee') : '#4b5563' }
      if (!pathEdgeIds.has(e.id)) {
        return { ...e, animated: false, style: baseStyle }
      }
      const isActive = e.id === activeEdgeId
      return {
        ...e,
        hidden: false, // 경로 엣지는 숨김 해제
        animated: isActive,
        style: {
          strokeWidth: isActive ? 3.5 : 2,
          stroke: isActive ? '#fbbf24' : '#22d3ee',
        },
      }
    }))
  }, [playbackCursor, playbackItems, setNodes, setEdges])

  // 흐름 재생 — 자동 진행 타이머
  useEffect(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    if (!playbackPlaying || playbackCursor >= playbackItems.length - 1) {
      if (playbackCursor >= playbackItems.length - 1 && playbackPlaying) setPlaybackPlaying(false)
      return
    }
    playbackTimerRef.current = setTimeout(() => {
      setPlaybackCursor((c) => c + 1)
    }, playbackSpeed)
    return () => { if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current) }
  }, [playbackPlaying, playbackCursor, playbackItems.length, playbackSpeed])

  // 흐름 재생 시작 — 선택된 노드 기준으로 경로 계산 후 첫 스텝으로 이동
  const startPlayback = useCallback((nodeId: string) => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    const { items } = buildFlowPath(nodeId, rawEdgesCache)
    setPlaybackItems(items)
    setPlaybackCursor(0)
    setPlaybackPlaying(false)
    // 경로 엣지 on/off 상태 무관하게 즉시 표시
    const pathEdgeIds = new Set(items.filter((it) => it.type === 'edge').map((it) => it.id))
    setEdges((eds) => eds.map((e) => pathEdgeIds.has(e.id) ? { ...e, hidden: false } : e))
    // 경로 노드 전체가 화면에 들어오도록 맞춤
    const pathNodeIds = items.filter((it) => it.type === 'node').map((it) => ({ id: it.id }))
    if (pathNodeIds.length > 0) {
      setTimeout(() => fitView({ nodes: pathNodeIds, duration: 400, padding: 0.2 }), 50)
    }
  }, [rawEdgesCache, setEdges, fitView])

  // 흐름 재생 초기화
  const resetPlayback = useCallback(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    setPlaybackPlaying(false)
    setPlaybackCursor(-1)
    setPlaybackItems([])
    setNodes((nds) => nds.map((n) => ({ ...n, style: { ...n.style, outline: 'none', boxShadow: 'none' }, data: { ...n.data, playbackActive: false, playbackInPath: false } })))
    setEdges((eds) => applyEdgeVisibility(eds.map((e) => {
      const d = e.data as { type?: string; broken?: boolean } | undefined
      const isCall = d?.type === 'FUNCTION_CALL'
      const isInst = d?.type === 'INSTANTIATION'
      const isApiCall = d?.type === 'API_CALL'
      const isDb = isDbEdgeType(d?.type)
      const broken = d?.broken
      return { ...e, animated: false, style: { strokeWidth: (isCall || isInst) ? 1.2 : broken ? 2 : 1.5, stroke: broken ? '#ef4444' : isCall ? '#f59e0b' : isInst ? '#a855f7' : isApiCall ? '#e879f9' : isDb ? (DB_CRUD_COLOR[d?.type ?? ''] ?? '#22d3ee') : '#4b5563' } }
    }), showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges))
  }, [setNodes, setEdges, applyEdgeVisibility, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges])

  // 서버에서 그래프 데이터를 불러와 React Flow 레이아웃으로 변환
  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { graphId: gid, nodes: rn, edges: re } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }
      setGraphId(gid)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset, openFileSidebar)
      setRawNodes(rn)
      setRawEdgesCache(re)
      setNodes(layoutNodes.filter((n, i, arr) => arr.findIndex(x => x.id === n.id) === i))
      setEdges(applyEdgeVisibility(layoutEdges.filter((e, i, arr) => arr.findIndex(x => x.id === e.id) === i), false, false, false, true, false, true))
      setCounts({
        files: rn.filter((n) => n.type === 'FILE').length,
        funcs: rn.filter((n) => n.type === 'FUNCTION').length,
        edges: re.length,
      })
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)
    } catch {
      setError('그래프를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, setNodes, setEdges, openFileSidebar, applyEdgeVisibility, fitView])

  useEffect(() => {
    axios.get<{ id: string }>('/api/auth/me', { headers: authHeaders() })
      .then((res) => setCurrentUserId(res.data.id))
      .catch(() => {})
  }, [])

  useEffect(() => {
    fetchGraph().then(() => {
      axios.get(`/api/projects/${projectId}/freshness`, { headers: authHeaders() })
        .then((res) => {
          if (res.data.isOutdated) {
            setOutdated({ branch: res.data.branch, lastAnalyzedAt: res.data.lastAnalyzedAt })
          }
        })
        .catch(() => {})
    })
  }, [fetchGraph, projectId])

  // 현재 그래프에서 그룹 키 목록 추출
  const availableGroups = (() => {
    const fileNodes = rawNodes.filter((n) => n.type === 'FILE' && n.filePath)
    const commonPrefix = findCommonPrefix(fileNodes.map((n) => n.filePath))
    const groups = new Set(fileNodes.map((n) => getGroupKey(n.filePath, commonPrefix)))
    return Array.from(groups).sort()
  })()

  const availableLayers = ['domain', 'application', 'infrastructure', 'interfaces', 'pages', 'components', 'hooks', 'utils']

  // 커뮤니티에 그래프 첨부 게시글 제출
  const handleShareSubmit = async () => {
    if (!shareTitle.trim() || !shareContent.trim() || !graphId) return
    setShareSubmitting(true)
    try {
      await axios.post(
        '/api/community/posts',
        {
          title: shareTitle,
          content: shareContent,
          feedbackType: shareFeedbackType,
          graphId,
          hiddenLayers: Array.from(shareHiddenLayers),
          hiddenGroups: Array.from(shareHiddenGroups),
          hiddenNodeNames: Array.from(shareHiddenNodes),
        },
        { headers: authHeaders() }
      )
      setShowShareModal(false)
      setShareTitle('')
      setShareContent('')
      setShareHiddenLayers(new Set())
      setShareHiddenGroups(new Set())
      setShareHiddenNodes(new Set())
      alert('커뮤니티에 게시글이 등록되었습니다.')
    } catch {
      alert('게시 실패. 다시 시도해주세요.')
    } finally {
      setShareSubmitting(false)
    }
  }

  // 버전 목록을 서버에서 불러오는 함수
  const handleLoadVersions = useCallback(async () => {
    if (showVersions) { setShowVersions(false); return }
    setLoadingVersions(true)
    setShowVersions(true)
    try {
      const res = await axios.get(`/api/projects/${projectId}/graphs`, { headers: authHeaders() })
      setVersions(res.data)
    } finally {
      setLoadingVersions(false)
    }
  }, [projectId, showVersions])

  // 특정 버전의 그래프를 로드
  const handleLoadVersion = useCallback(async (targetGraphId: string) => {
    setLoading(true)
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph?graphId=${targetGraphId}`, { headers: authHeaders() })
      const { graphId: gid, nodes: rn, edges: re } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }
      setGraphId(gid)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset, openFileSidebar)
      setRawNodes(rn)
      setRawEdgesCache(re)
      setNodes(layoutNodes.filter((n, i, arr) => arr.findIndex(x => x.id === n.id) === i))
      setEdges(applyEdgeVisibility(layoutEdges.filter((e, i, arr) => arr.findIndex(x => x.id === e.id) === i), showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges))
      setCounts({
        files: rn.filter((n) => n.type === 'FILE').length,
        funcs: rn.filter((n) => n.type === 'FUNCTION').length,
        edges: re.length,
      })
      setShowVersions(false)
    } catch {
      setError('버전을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, labelMode, layoutPreset, openFileSidebar, setNodes, setEdges, applyEdgeVisibility, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges])

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = useCallback(() => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, next, layoutPreset, openFileSidebar)
      setNodes(layoutNodes)
      setEdges(applyEdgeVisibility(layoutEdges, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges))
    }
  }, [labelMode, layoutPreset, rawNodes, rawEdgesCache, setNodes, setEdges, openFileSidebar, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, applyEdgeVisibility])

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

  // 끊긴 연결 엣지 표시/숨김 토글
  const toggleBrokenEdges = useCallback(() => {
    setShowBrokenEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) =>
        (e.data as { broken?: boolean })?.broken
          ? { ...e, hidden: !next }
          : e
      ))
      return next
    })
  }, [setEdges])

  // DB_READ / DB_WRITE 엣지 표시/숨김 토글
  const toggleDbEdges = useCallback(() => {
    setShowDbEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) => {
        const t = (e.data as { type?: string })?.type
        return isDbEdgeType(t) ? { ...e, hidden: !next } : e
      }))
      return next
    })
  }, [setEdges])

  // API_CALL 엣지 표시/숨김 토글
  const toggleApiCallEdges = useCallback(() => {
    setShowApiCallEdges((prev) => {
      const next = !prev
      setEdges((eds) => eds.map((e) => {
        const t = (e.data as { type?: string })?.type
        return t === 'API_CALL' ? { ...e, hidden: !next } : e
      }))
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
      setEdges(applyEdgeVisibility(le, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges))
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }, [layoutPreset, rawNodes, rawEdgesCache, labelMode, setNodes, setEdges, fitView, openFileSidebar, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, applyEdgeVisibility])

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

  // 노드 드래그 완료 시 서버에 위치를 저장 — sectionNode/groupNode는 DB 노드 아니므로 제외
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleNodeDragStop = useCallback((_event: any, node: Node) => {
    if (!graphId) return
    if (!rawNodes.some((n) => n.id === node.id)) return
    axios.put(
      `/api/graphs/${graphId}/nodes/${node.id}/position`,
      { x: node.position.x, y: node.position.y },
      { headers: authHeaders() }
    ).catch(() => {})
  }, [graphId, rawNodes])

  // 엣지 마우스 진입 — 두껍고 밝게 강조
  const handleEdgeMouseEnter: EdgeMouseHandler<Edge> = useCallback((_evt, edge) => {
    const data = edge.data as { broken?: boolean; type?: string } | undefined
    const broken = data?.broken
    const isCall = data?.type === 'FUNCTION_CALL'
    const isInst = data?.type === 'INSTANTIATION'
    const isApiCall = data?.type === 'API_CALL'
    const isDb = isDbEdgeType(data?.type)
    const dbBaseColor = DB_CRUD_COLOR[data?.type ?? ''] ?? '#22d3ee'
    const hoverColor = broken ? '#fca5a5' : isCall ? '#fcd34d' : isInst ? '#d8b4fe' : isApiCall ? '#f0abfc' : isDb ? dbBaseColor + 'cc' : '#a1a1aa'
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
    const isDb = isDbEdgeType(data?.type)
    const isApiCall = data?.type === 'API_CALL'
    const baseColor = broken ? '#ef4444' : isCall ? '#f59e0b' : isInst ? '#a855f7'
      : isApiCall ? '#e879f9' : isDb ? (DB_CRUD_COLOR[data?.type ?? ''] ?? '#22d3ee') : '#4b5563'
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

    if (isDbEdgeType(data?.type)) {
      const repoFile = rawNodes.find((n) => n.id === edge.source && n.type === 'FILE')
      const dbTable = rawNodes.find((n) => n.id === edge.target && n.type === 'DB_TABLE')
      setSidebar({
        kind: 'db-edge',
        crudType: data?.type ?? 'DB_READ',
        repoFile: repoFile?.name ?? edge.source,
        repoFileNodeId: edge.source,
        tableName: dbTable?.name ?? edge.target,
        tableNodeId: edge.target,
        flowChain: traceFlow(edge.source, edge.target, data?.type ?? 'DB_READ', rawEdgesCache, rawNodes),
      })
      return
    }

    if (data?.type === 'API_CALL') {
      const frontFile = rawNodes.find((n) => n.id === edge.source && n.type === 'FILE')
      const ctrlFile = rawNodes.find((n) => n.id === edge.target && n.type === 'FILE')
      setSidebar({
        kind: 'api-call',
        frontFile: frontFile?.name ?? edge.source,
        frontFileNodeId: edge.source,
        ctrlFile: ctrlFile?.name ?? edge.target,
        ctrlFileNodeId: edge.target,
        flowChain: traceFlow(edge.source, edge.target, 'API_CALL', rawEdgesCache, rawNodes),
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
      flowChain: traceFlow(edge.source, edge.target, data?.type ?? 'IMPORT', rawEdgesCache, rawNodes),
    })
  }, [rawNodes, rawEdgesCache])

  // 함수 노드 클릭 시 사이드바에 콜 체인 표시
  const handleNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    if (node.type === 'fileNode' || node.type === 'groupNode' || node.type === 'sectionNode') return

    // DB_TABLE 노드 클릭 — 칼럼 목록 + 연결된 Repository 표시
    const rawTable = rawNodes.find((n) => n.id === node.id && n.type === 'DB_TABLE')
    if (rawTable) {
      const repoEdges = rawEdgesCache.filter((e) => isDbEdgeType(e.type) && e.target === node.id)
      const repoMap = new Map<string, string[]>()
      repoEdges.forEach((e) => {
        if (!repoMap.has(e.source)) repoMap.set(e.source, [])
        repoMap.get(e.source)!.push(e.type)
      })
      const repos = Array.from(repoMap.entries()).map(([srcId, types]) => {
        const n = rawNodes.find((n) => n.id === srcId)
        return { name: n?.name ?? srcId, id: srcId, crudTypes: types }
      })
      setSidebar({
        kind: 'db-table',
        tableName: rawTable.name,
        nodeId: node.id,
        columns: rawTable.columns ?? [],
        repos,
      })
      setRightCollapsed(false)
      return
    }

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
    startPlayback(rawFunc.id)
    setCommentNodeId(rawFunc.id)
    setCommentInput('')
    if (graphId) {
      axios.get(`/api/graphs/${graphId}/nodes/${rawFunc.id}/comments`, { headers: authHeaders() })
        .then((res) => setNodeComments(res.data))
        .catch(() => setNodeComments([]))
    }
    setRightCollapsed(false)
  }, [rawNodes, rawEdgesCache, graphId])

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

      {/* 최신 커밋 감지 배너 */}
      {outdated && (
        <div className="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-4 py-2 bg-yellow-900/80 border-b border-yellow-700 text-yellow-300 text-xs backdrop-blur-sm">
          <span>
            ⚠️ <strong>{outdated.branch}</strong> 브랜치에 새 커밋이 있습니다. 마지막 분석: {new Date(outdated.lastAnalyzedAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate(`/dashboard`)}
              className="underline hover:text-yellow-100"
            >
              재분석하기
            </button>
            <button onClick={() => setOutdated(null)} className="text-yellow-500 hover:text-yellow-200">✕</button>
          </div>
        </div>
      )}

      {/* 상단 바 — 내비 + 통계만 */}
      <div className="absolute z-10 flex items-center gap-3" style={{ top: outdated ? '44px' : '16px', left: leftOpen ? `${leftWidth + 8}px` : '20px' }}>
        <button
          onClick={() => navigate('/dashboard')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          ← 대시보드
        </button>
        <span className="text-gray-500 text-sm">
          파일 {counts.files} · 함수 {counts.funcs} · 엣지 {counts.edges}
        </span>
        {graphId && (
          <button
            onClick={() => setShowShareModal(true)}
            className="bg-blue-600 hover:bg-blue-500 text-white text-sm px-3 py-1.5 rounded-lg"
          >
            커뮤니티에 공유
          </button>
        )}
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
        <aside className="absolute left-0 top-0 h-full z-20 flex flex-col bg-gray-950 border-r border-gray-800 shadow-xl overflow-y-auto" style={{ width: `${leftWidth}px` }}>

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

            {/* 내보내기 — 최상단 */}
            <LeftSection title="내보내기">
              <button onClick={() => downloadTreeText(rawNodes)} disabled={rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed">
                ↓ AI 컨텍스트
              </button>
              <button onClick={handleExportImage} disabled={exporting || rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed mt-1">
                {exporting ? '저장 중...' : '↓ 이미지'}
              </button>
            </LeftSection>

            {/* 버전 기록 */}
            <LeftSection title="버전 기록">
              <button
                onClick={handleLoadVersions}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300"
              >
                {showVersions ? '▲ 닫기' : '▼ 버전 목록 보기'}
              </button>
              {showVersions && (
                <div className="mt-1 flex flex-col gap-1 max-h-48 overflow-y-auto">
                  {loadingVersions ? (
                    <p className="text-xs text-gray-500 px-1">불러오는 중...</p>
                  ) : versions.length === 0 ? (
                    <p className="text-xs text-gray-500 px-1">버전 없음</p>
                  ) : (
                    versions.map((v, i) => (
                      <button
                        key={v.graphId}
                        onClick={() => handleLoadVersion(v.graphId)}
                        className={`w-full text-left text-xs px-2 py-1.5 rounded hover:bg-gray-700 transition-colors ${
                          v.graphId === graphId ? 'bg-gray-700 text-white' : 'bg-gray-800/40 text-gray-400'
                        }`}
                      >
                        <span className="text-gray-300">{i === 0 ? '최신 ' : ''}</span>
                        <span className="text-blue-400">{v.branch}</span>
                        <br />
                        <span className="text-gray-500">
                          {new Date(v.createdAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </button>
                    ))
                  )}
                </div>
              )}
            </LeftSection>

            {/* 버전 비교 */}
            <LeftSection title="버전 비교">
              <button
                onClick={() => navigate(`/projects/${projectId}/diff`)}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300"
              >
                🔀 버전 diff 보기
              </button>
            </LeftSection>

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

            {/* 엣지 — 색인 + 토글 통합 */}
            <LeftSection title="엣지">
              {[
                { key: 'import',  icon: <span className="block w-4 h-0.5" style={{ background: showEdges ? '#4b5563' : '#374151' }} />,                                                                                              label: '의존성',       textCls: showEdges ? 'text-gray-300' : 'text-gray-600',   active: showEdges,        onToggle: toggleEdges },
                { key: 'call',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showCallEdges ? '#f59e0b' : '#78350f'} strokeWidth="1.5" strokeDasharray="5 4" /></svg>,                                label: '콜 체인',      textCls: showCallEdges ? 'text-amber-400' : 'text-gray-600', active: showCallEdges,    onToggle: toggleCallEdges },
                { key: 'inst',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showInstEdges ? '#a855f7' : '#4c1d95'} strokeWidth="1.5" strokeDasharray="3 4" /></svg>,                                label: '생성',         textCls: showInstEdges ? 'text-purple-400' : 'text-gray-600', active: showInstEdges,  onToggle: toggleInstEdges },
                { key: 'broken',  icon: <span className="block w-4 h-0.5" style={{ background: showBrokenEdges ? '#ef4444' : '#450a0a' }} />,                                                                                        label: '끊긴 연결',    textCls: showBrokenEdges ? 'text-red-400' : 'text-gray-600', active: showBrokenEdges, onToggle: toggleBrokenEdges },
                { key: 'db',      icon: <svg width="16" height="4"><line x1="0" y1="2" x2="3.5" y2="2" stroke={showDbEdges ? '#22d3ee' : '#374151'} strokeWidth="1.5"/><line x1="4.5" y1="2" x2="8" y2="2" stroke={showDbEdges ? '#4ade80' : '#374151'} strokeWidth="1.5"/><line x1="9" y1="2" x2="12.5" y2="2" stroke={showDbEdges ? '#facc15' : '#374151'} strokeWidth="1.5"/><line x1="13.5" y1="2" x2="16" y2="2" stroke={showDbEdges ? '#f87171' : '#374151'} strokeWidth="1.5"/></svg>, label: 'DB 연결',      textCls: showDbEdges ? 'text-cyan-400' : 'text-gray-600',    active: showDbEdges,     onToggle: toggleDbEdges },
                { key: 'api',     icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showApiCallEdges ? '#e879f9' : '#701a75'} strokeWidth="1.5" strokeDasharray="6 3" /></svg>,                              label: 'API 호출',     textCls: showApiCallEdges ? 'text-fuchsia-400' : 'text-gray-600', active: showApiCallEdges, onToggle: toggleApiCallEdges },
              ].map(({ key, icon, label, textCls, active, onToggle }) => (
                <div key={key} onClick={onToggle} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onToggle()}
                  className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded hover:bg-gray-800/60 cursor-pointer">
                  <span className="w-4 flex-shrink-0">{icon}</span>
                  <span className={`text-xs flex-1 ${textCls}`}>{label}</span>
                  <ToggleChip active={active} onClick={onToggle} stopPropagation />
                </div>
              ))}
            </LeftSection>

            {/* 범례 — DDD 레이어 + 노드 */}
            <LeftSection title="범례">
              <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">DDD 레이어</p>
              {[
                { label: 'Domain',           color: '#3b82f6', key: 'domain' },
                { label: 'Application',      color: '#eab308', key: 'application' },
                { label: 'Infrastructure',   color: '#a855f7', key: 'infrastructure' },
                { label: 'Interfaces',       color: '#10b981', key: 'interfaces' },
                { label: 'Pages/Components', color: '#06b6d4', key: 'pages' },
                { label: 'Database',         color: '#ef4444', key: 'database' },
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
              <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">노드</p>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#1e3a5f', border: '1.5px solid #3b82f6' }} />
                <span className="text-gray-400 text-xs">FILE</span>
              </div>
              <div className="flex items-center gap-2 py-0.5">
                <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#064e3b', border: '1px solid #10b981' }} />
                <span className="text-gray-400 text-xs">FUNCTION</span>
              </div>
            </LeftSection>
          </div>

          {/* 왼쪽 사이드바 리사이즈 핸들 */}
          <div
            onMouseDown={(e) => { leftResizing.current = true; dragStartX.current = e.clientX; dragStartWidth.current = leftWidth; e.preventDefault() }}
            className="absolute top-0 right-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />
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

      {/* 우측 사이드바 — 항상 표시 */}
      <aside
        className="fixed right-0 top-0 h-full bg-gray-950 border-l border-gray-800 z-40 flex flex-col shadow-2xl transition-all duration-200"
        style={{ width: rightCollapsed ? '40px' : `${rightWidth}px` }}
      >
        {/* collapse 핸들 */}
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
                {!sidebar ? '상세 정보'
                  : sidebar.kind === 'edge' ? '연결 상세'
                  : sidebar.kind === 'file' ? '파일 연결'
                  : sidebar.kind === 'func' ? '함수 상세'
                  : sidebar.kind === 'func-call' ? '함수 호출'
                  : sidebar.kind === 'db-table' ? 'DB 테이블'
                  : sidebar.kind === 'db-edge' ? 'DB 연결'
                  : sidebar.kind === 'api-call' ? 'API 호출'
                  : '인스턴스화'}
              </span>
              {sidebar && <button onClick={() => setSidebar(null)} className="text-gray-600 hover:text-white text-sm">✕</button>}
            </div>

              <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">

                {/* ── 기본 상태 — 아무것도 선택되지 않은 경우 ── */}
                {!sidebar && (
                  <div className="flex flex-col items-center justify-center h-full gap-3 text-center pb-10">
                    <span className="text-3xl opacity-20">↗</span>
                    <p className="text-gray-600 text-xs leading-relaxed">
                      엣지나 노드를 클릭하면<br />상세 정보가 여기에 표시됩니다.
                    </p>
                  </div>
                )}

                {sidebar && (<>

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
                    <SidebarSection title={`의존하는 파일 — import${sidebar.data.outgoing.length > 0 ? ` (${sidebar.data.outgoing.length})` : ''}`}>
                      {sidebar.data.outgoing.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
                        : sidebar.data.outgoing.map((c, i) => (
                          <FileConnGroup key={i} entry={c} direction="out"
                            onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>
                    <SidebarSection title={`이 파일을 import하는 곳${sidebar.data.incoming.length > 0 ? ` (${sidebar.data.incoming.length})` : ''}`}>
                      {sidebar.data.incoming.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
                        : sidebar.data.incoming.map((c, i) => (
                          <FileConnGroup key={i} entry={c} direction="in"
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
                    {/* 흐름 재생 컨트롤 */}
                    {playbackItems.length > 1 && (
                      <div className="bg-gray-800/60 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] text-gray-400 uppercase tracking-wider">흐름 재생</span>
                          <span className="text-[10px] text-gray-600">
                            {playbackCursor < 0 ? '-' : `${playbackCursor + 1} / ${playbackItems.length}`}
                          </span>
                        </div>
                        {/* 진행 바 */}
                        <div className="w-full h-1 bg-gray-700 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-amber-400 rounded-full transition-all duration-300"
                            style={{ width: playbackCursor < 0 ? '0%' : `${((playbackCursor + 1) / playbackItems.length) * 100}%` }}
                          />
                        </div>
                        {/* 컨트롤 버튼 */}
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setPlaybackCursor((c) => Math.max(0, c - 1))}
                            disabled={playbackCursor <= 0}
                            className="text-xs text-gray-400 hover:text-white disabled:opacity-30 px-1"
                          >⏮</button>
                          <button
                            onClick={() => {
                              if (playbackCursor >= playbackItems.length - 1) {
                                setPlaybackCursor(0)
                                setPlaybackPlaying(true)
                              } else {
                                setPlaybackPlaying((p) => !p)
                              }
                            }}
                            className="flex-1 text-xs bg-amber-500/20 hover:bg-amber-500/30 text-amber-400 border border-amber-700/40 rounded px-2 py-1"
                          >
                            {playbackPlaying ? '⏸ 일시정지' : playbackCursor >= playbackItems.length - 1 ? '↺ 다시 재생' : '▶ 재생'}
                          </button>
                          <button
                            onClick={() => setPlaybackCursor((c) => Math.min(playbackItems.length - 1, c + 1))}
                            disabled={playbackCursor >= playbackItems.length - 1}
                            className="text-xs text-gray-400 hover:text-white disabled:opacity-30 px-1"
                          >⏭</button>
                          <button onClick={resetPlayback} className="text-xs text-gray-600 hover:text-gray-400 px-1" title="초기화">✕</button>
                        </div>
                        {/* 속도 조절 */}
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] text-gray-600">속도</span>
                          {[['빠름', 300], ['보통', 600], ['느림', 1000]].map(([label, ms]) => (
                            <button
                              key={ms}
                              onClick={() => setPlaybackSpeed(ms as number)}
                              className={`text-[10px] px-1.5 py-0.5 rounded ${playbackSpeed === ms ? 'bg-gray-600 text-white' : 'text-gray-500 hover:text-gray-300'}`}
                            >{label}</button>
                          ))}
                        </div>
                      </div>
                    )}
                    {playbackItems.length <= 1 && (
                      <div className="bg-gray-800/40 rounded-lg px-3 py-2">
                        <p className="text-[10px] text-gray-600">이 함수는 연결된 흐름이 없습니다.</p>
                      </div>
                    )}

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

                    {/* 노드 코멘트 */}
                    <SidebarSection title={`코멘트${nodeComments.length > 0 ? ` (${nodeComments.length})` : ''}`}>
                      <div className="flex flex-col gap-2">
                        {nodeComments.map((c) => (
                          <div key={c.id} className="bg-gray-800 rounded-lg px-3 py-2 flex flex-col gap-1">
                            <p className="text-gray-200 text-xs leading-relaxed">{c.content}</p>
                            <div className="flex items-center justify-between">
                              <span className="text-gray-600 text-[10px]">{new Date(c.createdAt).toLocaleDateString('ko-KR')}</span>
                              {currentUserId === c.userId && (
                                <button
                                  onClick={() => {
                                    if (!graphId || !commentNodeId) return
                                    axios.delete(`/api/graphs/${graphId}/nodes/${commentNodeId}/comments/${c.id}`, { headers: authHeaders() })
                                      .then(() => setNodeComments((prev) => prev.filter((x) => x.id !== c.id)))
                                      .catch(() => {})
                                  }}
                                  className="text-gray-600 hover:text-red-400 text-[10px]"
                                >삭제</button>
                              )}
                            </div>
                          </div>
                        ))}
                        {nodeComments.length === 0 && <p className="text-gray-700 text-xs">코멘트 없음</p>}
                        <div className="flex gap-2 mt-1">
                          <input
                            value={commentInput}
                            onChange={(e) => setCommentInput(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' && !e.shiftKey) {
                                e.preventDefault()
                                if (!commentInput.trim() || !graphId || !commentNodeId) return
                                axios.post(
                                  `/api/graphs/${graphId}/nodes/${commentNodeId}/comments`,
                                  { content: commentInput.trim() },
                                  { headers: authHeaders() }
                                ).then((res) => {
                                  setNodeComments((prev) => [...prev, res.data])
                                  setCommentInput('')
                                }).catch(() => {})
                              }
                            }}
                            placeholder="코멘트 입력 후 Enter"
                            className="flex-1 bg-gray-800 text-white text-xs px-2 py-1.5 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500"
                          />
                        </div>
                      </div>
                    </SidebarSection>
                  </>
                )}

                {/* ── DB_TABLE 노드 클릭 ── */}
                {sidebar.kind === 'db-table' && (
                  <div className="flex flex-col gap-3">
                    <div>
                      <p className="text-white font-mono font-semibold text-sm">{sidebar.tableName}</p>
                      <p className="text-gray-500 text-xs mt-0.5">DB 테이블</p>
                    </div>

                    {/* 연결된 Repository */}
                    <SidebarSection title={`Repository (${sidebar.repos.length})`}>
                      {sidebar.repos.length === 0
                        ? <p className="text-gray-700 text-xs">연결 없음</p>
                        : sidebar.repos.map((r, i) => (
                          <div key={i} className="flex items-center gap-2">
                            <span
                              className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-200 flex-1 truncate"
                              onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: r.id }], duration: 500, padding: 0.3 }), 50) }}
                            >{r.name}</span>
                            <div className="flex gap-1 flex-shrink-0">
                              {r.crudTypes.map((t) => (
                                <span key={t} className="text-[9px] px-1 py-0.5 rounded font-bold"
                                  style={{ background: (DB_CRUD_COLOR[t] ?? '#22d3ee') + '33', color: DB_CRUD_COLOR[t] ?? '#22d3ee' }}
                                >{t.replace('DB_', '')}</span>
                              ))}
                            </div>
                          </div>
                        ))
                      }
                    </SidebarSection>

                    {/* 칼럼 목록 */}
                    <SidebarSection title={`칼럼${sidebar.columns.length > 0 ? ` (${sidebar.columns.length})` : ''}`}>
                      {sidebar.columns.length === 0
                        ? <p className="text-gray-700 text-xs">칼럼 정보 없음</p>
                        : <div className="flex flex-col gap-0.5">
                            {sidebar.columns.map((col, i) => (
                              <div key={i} className="flex items-baseline gap-2 py-0.5">
                                <span className="text-cyan-400 font-mono text-xs flex-shrink-0">{col.columnName}</span>
                                <span className="text-gray-600 text-[10px] truncate">{col.javaType}</span>
                              </div>
                            ))}
                          </div>
                      }
                    </SidebarSection>
                  </div>
                )}

                {/* ── DB 엣지 클릭 ── */}
                {sidebar.kind === 'db-edge' && (
                  <div className="flex flex-col gap-3">
                    <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">Repository</p>
                        <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.repoFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.repoFile}</p>
                      </div>
                      <div className="text-center text-sm" style={{ color: DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee' }}>
                        ↓ {sidebar.crudType.replace('DB_', '')}
                      </div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">DB 테이블</p>
                        <p className="text-cyan-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.tableNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.tableName}</p>
                      </div>
                    </div>
                    <span
                      className="text-xs px-2 py-0.5 rounded self-start font-semibold"
                      style={{ background: (DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee') + '22', color: DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee' }}
                    >{sidebar.crudType}</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor={DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee'}
                      onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                  </div>
                )}

                {/* ── API 호출 엣지 상세 ── */}
                {sidebar?.kind === 'api-call' && (
                  <div className="flex flex-col gap-3">
                    <div className="flex gap-4 items-start">
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">프론트 파일</p>
                        <p className="text-fuchsia-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.frontFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.frontFile}</p>
                      </div>
                      <div className="text-gray-500 mt-5">→</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">컨트롤러</p>
                        <p className="text-fuchsia-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id: sidebar.ctrlFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.ctrlFile}</p>
                      </div>
                    </div>
                    <span className="text-xs px-2 py-0.5 rounded self-start font-semibold"
                      style={{ background: '#e879f922', color: '#e879f9' }}>API_CALL</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#e879f9"
                      onNav={(id) => { setSidebar(null); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                  </div>
                )}

                </>)}

              </div>
          </>
        )}

        {/* 오른쪽 사이드바 리사이즈 핸들 — collapse 아닐 때만 */}
        {!rightCollapsed && (
          <div
            onMouseDown={(e) => { rightResizing.current = true; dragStartX.current = e.clientX; dragStartWidth.current = rightWidth; e.preventDefault() }}
            className="absolute top-0 left-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />
        )}
      </aside>

      {/* 커뮤니티 공유 모달 */}
      {showShareModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70">
          <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 w-[560px] max-h-[85vh] overflow-y-auto flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-base">커뮤니티에 공유</h2>
              <button onClick={() => setShowShareModal(false)} className="text-gray-500 hover:text-white text-lg leading-none">✕</button>
            </div>

            {/* 제목 */}
            <input
              value={shareTitle}
              onChange={(e) => setShareTitle(e.target.value)}
              placeholder="제목"
              className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
            />

            {/* 피드백 타입 */}
            <select
              value={shareFeedbackType}
              onChange={(e) => setShareFeedbackType(e.target.value)}
              className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
            >
              <option value="GENERAL">일반</option>
              <option value="ARCHITECTURE_REVIEW">아키텍처 리뷰</option>
              <option value="DEBUG">디버그</option>
            </select>

            {/* 내용 */}
            <textarea
              value={shareContent}
              onChange={(e) => setShareContent(e.target.value)}
              placeholder="내용을 입력하세요"
              rows={4}
              className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none resize-none"
            />

            {/* 레이어 숨기기 */}
            <div>
              <p className="text-xs text-gray-400 mb-2 font-medium">레이어 숨기기</p>
              <div className="flex flex-wrap gap-2">
                {availableLayers.map((layer) => (
                  <button
                    key={layer}
                    onClick={() => setShareHiddenLayers((prev) => {
                      const next = new Set(prev)
                      next.has(layer) ? next.delete(layer) : next.add(layer)
                      return next
                    })}
                    className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                      shareHiddenLayers.has(layer)
                        ? 'bg-red-900/40 border-red-700 text-red-400'
                        : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                    }`}
                  >
                    {shareHiddenLayers.has(layer) ? '🚫 ' : ''}{layer}
                  </button>
                ))}
              </div>
            </div>

            {/* 그룹 숨기기 */}
            {availableGroups.length > 0 && (
              <div>
                <p className="text-xs text-gray-400 mb-2 font-medium">그룹 숨기기</p>
                <div className="flex flex-wrap gap-2">
                  {availableGroups.map((group) => (
                    <button
                      key={group}
                      onClick={() => setShareHiddenGroups((prev) => {
                        const next = new Set(prev)
                        next.has(group) ? next.delete(group) : next.add(group)
                        return next
                      })}
                      className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                        shareHiddenGroups.has(group)
                          ? 'bg-red-900/40 border-red-700 text-red-400'
                          : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                      }`}
                    >
                      {shareHiddenGroups.has(group) ? '🚫 ' : ''}{group}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 개별 노드 숨기기 */}
            <div>
              <p className="text-xs text-gray-400 mb-2 font-medium">개별 노드 숨기기 <span className="text-gray-600">({rawNodes.length}개)</span></p>
              <div className="max-h-36 overflow-y-auto flex flex-col gap-1 bg-gray-800/40 rounded-lg p-2">
                {rawNodes.filter((n) => n.type === 'FILE' || n.type === 'FUNCTION').map((n) => (
                  <button
                    key={n.id}
                    onClick={() => setShareHiddenNodes((prev) => {
                      const next = new Set(prev)
                      next.has(n.name) ? next.delete(n.name) : next.add(n.name)
                      return next
                    })}
                    className={`text-left text-xs px-2 py-1 rounded transition-colors ${
                      shareHiddenNodes.has(n.name)
                        ? 'bg-red-900/30 text-red-400'
                        : 'text-gray-400 hover:bg-gray-700'
                    }`}
                  >
                    {shareHiddenNodes.has(n.name) ? '🚫 ' : ''}
                    <span className="text-gray-600 mr-1">{n.type === 'FILE' ? '📄' : 'ƒ'}</span>
                    {n.name}
                  </button>
                ))}
              </div>
            </div>

            {/* 숨김 요약 */}
            {(shareHiddenLayers.size > 0 || shareHiddenGroups.size > 0 || shareHiddenNodes.size > 0) && (
              <p className="text-xs text-yellow-500">
                숨김: {[
                  shareHiddenLayers.size > 0 && `레이어 ${shareHiddenLayers.size}개`,
                  shareHiddenGroups.size > 0 && `그룹 ${shareHiddenGroups.size}개`,
                  shareHiddenNodes.size > 0 && `노드 ${shareHiddenNodes.size}개`,
                ].filter(Boolean).join(', ')}
              </p>
            )}

            <div className="flex justify-end gap-2 pt-2 border-t border-gray-800">
              <button onClick={() => setShowShareModal(false)} className="text-sm text-gray-500 hover:text-white px-4 py-2">취소</button>
              <button
                onClick={handleShareSubmit}
                disabled={shareSubmitting || !shareTitle.trim() || !shareContent.trim()}
                className="text-sm bg-white text-black font-medium px-4 py-2 rounded-lg hover:bg-gray-200 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {shareSubmitting ? '등록 중...' : '커뮤니티에 등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// 왼쪽 사이드바 섹션
function LeftSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="px-3 py-3 border-b border-gray-800/60">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">{title}</p>
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

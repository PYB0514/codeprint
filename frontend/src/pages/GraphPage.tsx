// 프로젝트 코드 구조를 React Flow로 시각화하는 그래프 페이지
// ⚠️ 새 "보기"(필터·조회·전환) 기능 추가 시 GraphViewerPage.tsx도 반영 검토 — 저장/수정 액션만 여기 전용, 보기는 비로그인도 동등해야 함(2026-07-02 결정)
import { useEffect, useState, useCallback, useRef, useMemo } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
import { buildLayout, downloadWarningsMd } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset, FileSidebarData, ConnEntry, FuncCallEntry, ColumnInfo } from '../utils/graphLayout'
import { extractDomain, buildDomainColorMap, buildKnownDomains } from '../utils/graphLayout'
import { isDbEdgeType, applyEdgeVisibility, GRAPH_MIN_ZOOM, GRAPH_MAX_ZOOM, GRAPH_ARIA_LABELS, searchNodes } from '../utils/graphLayout'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'
import SketchNode from '../components/SketchNode'
import OnboardingTour, { isTourDone, GRAPH_TOUR_STEPS, GRAPH_TOUR_STORAGE_KEY } from '../components/OnboardingTour'
import AppHeader from '../components/AppHeader'
import { useCollaboration } from '../hooks/useCollaboration'
import { useSidebarResize } from '../hooks/useSidebarResize'
import CollaborationPanel from '../components/CollaborationPanel'
import CursorOverlay from '../components/CursorOverlay'
import WarningPanel from '../components/WarningPanel'
import TeamChatPanel from '../components/TeamChatPanel'
import ArchitectureIntentPanel from '../components/ArchitectureIntentPanel'
import { LayoutPresetToggle, LabelModeToggle } from '../components/GraphViewToggles'
import { GraphLegend } from '../components/GraphLegend'
import { CornerPanel } from '../components/CornerPanel'
import { FlowPlaybackPanel } from '../components/FlowPlaybackPanel'
import { type IgnoreRule, loadIgnoreRules, saveIgnoreRules } from '../utils/ignoreRules'
import { CALL_FLOW_TYPES } from '../utils/flowPlayback'
import { useFlowPlayback } from '../hooks/useFlowPlayback'
import { currentDateLocale } from '../i18n/dateLocale'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode, sketch: SketchNode }

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
  | { kind: 'func'; nodeId: string; funcName: string; funcComment: string | null; parentFileName: string; parentFileNodeId: string; callers: FuncCallChainEntry[]; callees: FuncCallChainEntry[] }
  | { kind: 'func-call'; callerName: string; callerComment: string | null; callerNodeId: string; callerFile: string; callerFileNodeId: string; calleeName: string; calleeComment: string | null; calleeNodeId: string; calleeFile: string; calleeFileNodeId: string; flowChain: FlowStep[] }
  | { kind: 'instantiation'; sourceFile: string; sourceNodeId: string; targetClass: string; targetNodeId: string; flowChain: FlowStep[] }
  | { kind: 'db-table'; tableName: string; nodeId: string; columns: ColumnInfo[]; repos: { name: string; id: string; crudTypes: string[] }[] }
  | { kind: 'db-edge'; crudType: string; repoFile: string; repoFileNodeId: string; tableName: string; tableNodeId: string; flowChain: FlowStep[] }
  | { kind: 'api-call'; frontFile: string; frontFileNodeId: string; ctrlFile: string; ctrlFileNodeId: string; flowChain: FlowStep[] }
  | { kind: 'warning'; nodeName: string; nodeWarnings: { type: string; message: string }[] }
  | { kind: 'domain-summary'; domainName: string; color: string; flows: { id: string; label: string; preview: string[] }[] }

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

  // API_CALL: 프론트 파일 → 컨트롤러 파일 → FUNCTION_CALL 체인 → DB까지
  if (edgeType === 'API_CALL') {
    const ctrlNode = rawNodes.find((n) => n.id === targetId && n.type === 'FILE')
    const ctrlFuncs = ctrlNode
      ? rawNodes.filter((n) => n.type === 'FUNCTION' && n.filePath === ctrlNode.filePath)
      : []
    const seed = new Set([sourceId, targetId])
    // 컨트롤러 함수 중 가장 긴 downstream 체인 선택
    let bestFuncId: string | null = null
    let bestDownstream: FlowStep[] = []
    for (const f of ctrlFuncs) {
      const chain = traceFuncCallDown(f.id, new Set(seed))
      if (chain.length > bestDownstream.length) {
        bestDownstream = chain
        bestFuncId = f.id
      }
    }
    const funcSteps: FlowStep[] = bestFuncId
      ? [makeStep(bestFuncId, false, false), ...bestDownstream]
      : []
    return [makeStep(sourceId, true, false), makeStep(targetId, false, false), ...funcSteps]
  }

  // IMPORT / INSTANTIATION / 기타: 동일 타입 엣지만 따라감
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


// 그래프 페이지 내부 컴포넌트 (ReactFlow 훅 사용)
function GraphPageInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { t } = useTranslation('workspace')

  // 협업 세션 상태 — URL query param에서 초기화
  const [collabSessionId, setCollabSessionId] = useState<string | null>(searchParams.get('collab'))
  const [collabInviteCode, setCollabInviteCode] = useState<string | null>(searchParams.get('code'))
  // publishCursor를 ref로 감싸서 handleCollabMouseMove의 선언 순서 문제 우회
  const publishCursorRef = useRef<(x: number, y: number) => void>(() => {})

  // 커서 throttle — 50ms마다 발행
  const cursorThrottleRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastCursorRef = useRef<{ x: number; y: number } | null>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [rawNodes, setRawNodes] = useState<RawNode[]>([])

  // 파일 경로 공통 접두사 — extractDomain에 필요
  const commonPrefix = useMemo(() => {
    const paths = rawNodes.filter(n => n.type === 'FILE').map(n => n.filePath)
    if (paths.length === 0) return ''
    return paths.reduce((a, b) => { let i = 0; while (i < a.length && a[i] === b[i]) i++; return a.slice(0, i) }, paths[0])
  }, [rawNodes])

  // 경로로 식별되는 도메인 집합 — extractDomain의 파일명 유추 화이트리스트 (레이아웃과 동일 결과 보장)
  const knownDomains = useMemo(
    () => buildKnownDomains(rawNodes.filter(n => n.type === 'FILE').map(n => n.filePath), commonPrefix),
    [rawNodes, commonPrefix]
  )

  const [hiddenNodeTypes, setHiddenNodeTypes] = useState<Set<string>>(new Set())
  const [nodeSearchQuery, setNodeSearchQuery] = useState('')
  const [counts, setCounts] = useState({ files: 0, funcs: 0, edges: 0 })
  const [loading, setLoading] = useState(true)
  const [tourRunning, setTourRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sidebar, setSidebar] = useState<SidebarContent | null>(null)
  const [rightCollapsed, setRightCollapsed] = useState(true)
  // 코너 플로팅 분석 패널 — 기본 접힘(칩만 표시, 캔버스 안 가림)
  const [archPanelOpen, setArchPanelOpen] = useState(false)
  const [analysisPanelOpen, setAnalysisPanelOpen] = useState(false)
  const [leftOpen, setLeftOpen] = useState(true)
  const { width: leftWidth, startResize: startLeftResize } = useSidebarResize(220, 160, 420, 'left')
  const { width: rightWidth, startResize: startRightResize } = useSidebarResize(320, 240, 520, 'right')
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('domain')
  const [opaqueLayerSet, setOpaqueLayerSet] = useState<Set<string>>(new Set())
  const [opaqueDomainSet, setOpaqueDomainSet] = useState<Set<string>>(new Set())
  const [showEdges, setShowEdges] = useState(false)
  const [showCallEdges, setShowCallEdges] = useState(false)
  const [showInstEdges, setShowInstEdges] = useState(false)
  const [showBrokenEdges, setShowBrokenEdges] = useState(true)
  const [showDbEdges, setShowDbEdges] = useState(false)
  const [showApiCallEdges, setShowApiCallEdges] = useState(true)
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [showTeamChat, setShowTeamChat] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [exportingContextMd, setExportingContextMd] = useState(false)
  const [showVersions, setShowVersions] = useState(false)
  const [versions, setVersions] = useState<{ graphId: string; createdAt: string; branch: string; pinnedSlot: number | null }[]>([])
  const [showRetentionInfo, setShowRetentionInfo] = useState(false)
  const [pinMenuGraphId, setPinMenuGraphId] = useState<string | null>(null)
  const [loadingVersions, setLoadingVersions] = useState(false)

  // 스케치 모드 (슈퍼 바이브 코딩 P0) — 기존 구조 위에 설계용 노드를 자유 배치. 현재 localStorage 임시 저장
  const [sketchMode, setSketchMode] = useState(false)
  const [sketchNodes, setSketchNodes] = useState<{ id: string; label: string; x: number; y: number }[]>([])
  // 로드 직후 save 이펙트가 빈 배열로 덮어쓰는 경쟁 방지 — 로드 후 첫 save는 건너뜀
  const sketchSaveReady = useRef(false)
  const [outdated, setOutdated] = useState<{ branch: string; lastAnalyzedAt: string } | null>(null)
  const [freshnessError, setFreshnessError] = useState<'rate_limit' | 'github_error' | null>(null)
  const [reanalyzing, setReanalyzing] = useState(false)
  // 분석 완료 직후 ?fresh=1로 진입했을 때 표시할 결과 카드 — 구조 카운트 + 경고 가치
  const [resultCard, setResultCard] = useState<{
    files: number; funcs: number; db: number; api: number
    high: number; medium: number; low: number; warnTotal: number
    lowCoverage: boolean
  } | null>(null)
  // 대형 레포 절단 안내 — 전체 대상 파일 수 > 분석된 파일 수일 때 배너 표시
  const [truncation, setTruncation] = useState<{ analyzed: number; total: number } | null>(null)
  // 내 레포 분석 vs 외부 공개 레포 분석 판정 (1차: owner 문자열 비교, org 레포는 예외적으로 "외부"로 뜰 수 있음)
  const [ownRepo, setOwnRepo] = useState<boolean | null>(null)
  const [showDomainBoxes, setShowDomainBoxes] = useState(true)
  // 탭 분리: null = 전체 보기, 문자열 = 해당 도메인/레이어만 표시
  const [activeDomainTab, setActiveDomainTab] = useState<string | null>(null)
  // 배경이미지 — localStorage에 on/off 상태 저장
  const [bgEnabled, setBgEnabled] = useState(() => localStorage.getItem('graphBgEnabled') !== 'false')
  const [bgUrl, setBgUrl] = useState<string | null>(null)
  const [showShareModal, setShowShareModal] = useState(false)
  const [shareTitle, setShareTitle] = useState('')
  const [shareContent, setShareContent] = useState('')
  const [shareFeedbackType, setShareFeedbackType] = useState('GENERAL')
  const [sharePresetSlot, setSharePresetSlot] = useState(1)
  const [shareVisibility, setShareVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PUBLIC')
  const [shareSubmitting, setShareSubmitting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const { getNodes, fitView, fitBounds, screenToFlowPosition } = useReactFlow()

  // 검색 결과 노드로 fitView 이동
  const handleSearchNodeClick = useCallback((nodeId: string) => {
    const flowNode = getNodes().find(n => n.id === nodeId)
    if (flowNode) fitView({ nodes: [flowNode], duration: 400, padding: 0.5 })
    setNodeSearchQuery('')
  }, [getNodes, fitView])

  // 마우스 이동 핸들러 (50ms throttle) — 협업 세션 활성 시 커서 발행
  const handleCollabMouseMove = useCallback((e: React.MouseEvent) => {
    lastCursorRef.current = { x: e.clientX, y: e.clientY }
    if (cursorThrottleRef.current) return
    cursorThrottleRef.current = setTimeout(() => {
      cursorThrottleRef.current = null
      if (lastCursorRef.current) {
        const pos = screenToFlowPosition({ x: lastCursorRef.current.x, y: lastCursorRef.current.y })
        publishCursorRef.current(pos.x, pos.y)
      }
    }, 50)
  }, [screenToFlowPosition])

  // 흐름 재생 — 종료 시 표시 토글 기준 기본 엣지 스타일 복원
  const restorePlaybackEdgeStyles = useCallback((eds: Edge[]) => applyEdgeVisibility(eds.map((e) => {
    const d = e.data as { type?: string; broken?: boolean } | undefined
    const isCall = d?.type === 'FUNCTION_CALL'
    const isInst = d?.type === 'INSTANTIATION'
    const isApiCall = d?.type === 'API_CALL'
    const isDb = isDbEdgeType(d?.type)
    const broken = d?.broken
    return { ...e, animated: false, style: { strokeWidth: (isCall || isInst) ? 1.2 : broken ? 2 : 1.5, stroke: broken ? '#ef4444' : isCall ? '#f59e0b' : isInst ? '#a855f7' : isApiCall ? '#e879f9' : isDb ? (DB_CRUD_COLOR[d?.type ?? ''] ?? '#22d3ee') : '#4b5563' } }
  }), showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges), [showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges])

  // 흐름 재생 시작 시 도메인 요약 사이드바 닫기 — 재생 컨트롤이 가려지지 않도록
  const onFlowPlaybackStart = useCallback(() => setSidebar(prev => prev?.kind === 'domain-summary' ? null : prev), [])

  const {
    callTree, playbackItems, playbackCursor, playbackPlaying, activePath, pendingBranchNodeId, playbackRootNodeId,
    setPlaybackCursor, setPlaybackPlaying, setPendingBranchNodeId,
    startPlayback, resetPlayback: resetFlowPlayback, selectBranchImmediate, confirmBranch,
  } = useFlowPlayback({
    rawNodes, rawEdges: rawEdgesCache, setNodes, setEdges, getNodes, fitView,
    restoreEdgeStyles: restorePlaybackEdgeStyles, onStart: onFlowPlaybackStart,
  })

  // 노드 코멘트 상태
  const [nodeComments, setNodeComments] = useState<{ id: string; userId: string; content: string; createdAt: number }[]>([])
  const [commentInput, setCommentInput] = useState('')
  const [commentNodeId, setCommentNodeId] = useState<string | null>(null)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const [annotationNodeId, setAnnotationNodeId] = useState<string | null>(null)
  const [annotationLabel, setAnnotationLabel] = useState('')
  const [annotationNote, setAnnotationNote] = useState('')
  const [annotationSaving, setAnnotationSaving] = useState(false)

  // 노드 커스텀 이름/메모 저장
  const handleSaveAnnotation = async () => {
    if (!graphId || !annotationNodeId) return
    setAnnotationSaving(true)
    try {
      await axios.put(`/api/graphs/${graphId}/nodes/${annotationNodeId}/annotation`, {
        userLabel: annotationLabel.trim() || null,
        userNote: annotationNote.trim() || null,
      })
      setRawNodes(prev => prev.map(n => n.id === annotationNodeId
        ? { ...n, userLabel: annotationLabel.trim() || undefined, userNote: annotationNote.trim() || undefined }
        : n
      ))
    } finally {
      setAnnotationSaving(false)
    }
  }

  // 노드 색상 커스터마이징 — nodeId → bgColor (즉시 반영용)
  const [nodeColorMap, setNodeColorMap] = useState<Record<string, string>>({})
  // 현재 선택된 노드 ID (색상 팔레트 표시 대상)
  const [clickedNodeId, setClickedNodeId] = useState<string | null>(null)
  const { state: collabState, publishCursor, publishSelection } = useCollaboration(collabSessionId, currentUserId)

  // 런타임 경고 상태
  const [warnings, setWarnings] = useState<{ type: string; severity?: 'HIGH' | 'MEDIUM' | 'LOW'; nodeIds: string[]; edgeIds?: string[]; message: string; fingerprint?: string }[]>([])
  // 숨긴 경고 목록 — 서버에서 영속 로드, 복원(unsuppress)용
  const [suppressedWarnings, setSuppressedWarnings] = useState<{ type: string; severity?: 'HIGH' | 'MEDIUM' | 'LOW'; nodeIds: string[]; edgeIds?: string[]; message: string; fingerprint?: string }[]>([])
  // publishCursorRef를 항상 최신 publishCursor로 유지
  publishCursorRef.current = publishCursor

  // 경고 숨기기 — 서버에 suppress 저장 후 패널에서 제거, 세션 복원 목록으로 이동
  const handleSuppressWarning = useCallback(async (w: { type: string; fingerprint?: string }) => {
    if (!projectId || !w.fingerprint) return
    try {
      await axios.post(`/api/projects/${projectId}/warnings/suppress`, { fingerprint: w.fingerprint, type: w.type })
      setWarnings(prev => prev.filter(x => x.fingerprint !== w.fingerprint))
      setSuppressedWarnings(prev => [...prev, w as typeof prev[number]])
    } catch {
      alert('경고 숨기기에 실패했습니다.')
    }
  }, [projectId])

  // 숨긴 경고 복원 — 서버 suppress 해제 후 경고 목록으로 복귀
  const handleRestoreWarning = useCallback(async (w: { fingerprint?: string }) => {
    if (!projectId || !w.fingerprint) return
    try {
      await axios.delete(`/api/projects/${projectId}/warnings/suppress/${w.fingerprint}`)
      setSuppressedWarnings(prev => prev.filter(x => x.fingerprint !== w.fingerprint))
      setWarnings(prev => [...prev, w as typeof prev[number]])
    } catch {
      alert('경고 복원에 실패했습니다.')
    }
  }, [projectId])

  // 내가 오탐 신고한 fingerprint 집합 — 신고 버튼 상태 표시용
  const [reportedFingerprints, setReportedFingerprints] = useState<Set<string>>(new Set())
  useEffect(() => {
    if (!projectId || !currentUserId) return
    axios.get(`/api/projects/${projectId}/warnings/report-fp/mine`)
      .then(res => setReportedFingerprints(new Set(res.data)))
      .catch(() => {})
  }, [projectId, currentUserId])

  // 오탐 신고 — 서버에 저장(멱등) 후 버튼 상태를 "신고됨"으로 갱신. graphId/message/file/line/col은 재현 페이로드용(GitHub 공개 레포면 코드 스니펫도 서버에서 최선노력 확보)
  const handleReportFp = useCallback(async (w: { type: string; fingerprint?: string; message?: string; file?: string; line?: number; col?: number; endCol?: number }) => {
    if (!projectId || !w.fingerprint) return
    try {
      await axios.post(`/api/projects/${projectId}/warnings/report-fp`, {
        fingerprint: w.fingerprint, type: w.type, graphId, message: w.message, file: w.file, line: w.line, col: w.col, endCol: w.endCol
      })
      setReportedFingerprints(prev => new Set(prev).add(w.fingerprint!))
    } catch {
      alert('오탐 신고에 실패했습니다.')
    }
  }, [projectId, graphId])

  // 패턴 예외(IGNORE) 규칙 — architecture-intent에 저장, 그룹 단위로 경고 억제
  const [ignoreRules, setIgnoreRules] = useState<IgnoreRule[]>([])
  // fetchGraph는 아래에서 정의되므로 ref로 안정 참조 (예외 규칙 변경 후 경고 재조회용)
  const fetchGraphRef = useRef<(() => Promise<void>) | null>(null)
  // 프로젝트의 예외 규칙 로드
  useEffect(() => {
    if (projectId) loadIgnoreRules(projectId).then(setIgnoreRules).catch(() => {})
  }, [projectId])

  // nodeId → 파일 경로 (글로브 추론·미리보기용) — 함수/파일 노드는 자신, 그 외는 빈 문자열
  const fileOfNodeId = useCallback((nodeId: string) => {
    const n = rawNodes.find(x => x.id === nodeId)
    return n?.filePath ?? ''
  }, [rawNodes])

  // 예외 규칙 추가 — 저장 후 경고 재조회로 억제 반영
  const handleAddIgnore = useCallback(async (rule: IgnoreRule) => {
    if (!projectId) return
    const next = [...ignoreRules, rule]
    setIgnoreRules(next)
    try {
      await saveIgnoreRules(projectId, next)
      await fetchGraphRef.current?.()
    } catch {
      alert('예외 규칙 저장에 실패했습니다.')
    }
  }, [projectId, ignoreRules])

  // 예외 규칙 제거 — 저장 후 경고 재조회로 복원 반영
  const handleRemoveIgnore = useCallback(async (index: number) => {
    if (!projectId) return
    const next = ignoreRules.filter((_, i) => i !== index)
    setIgnoreRules(next)
    try {
      await saveIgnoreRules(projectId, next)
      await fetchGraphRef.current?.()
    } catch {
      alert('예외 규칙 삭제에 실패했습니다.')
    }
  }, [projectId, ignoreRules])

  // 상단 툴바 팝업 — 'preset' | 'export' | null (한 번에 하나만 열림)
  const [openToolbarMenu, setOpenToolbarMenu] = useState<'preset' | 'export' | null>(null)
  // 뷰 프리셋 상태
  const [presets, setPresets] = useState<{ slot: number; name: string; config: Record<string, unknown>; isDefault: boolean }[]>([])
  const [showSavePresetModal, setShowSavePresetModal] = useState(false)
  const [presetSaving, setPresetSaving] = useState(false)
  const [pendingSaveSlot, setPendingSaveSlot] = useState<number | null>(null)
  const [presetSaveName, setPresetSaveName] = useState('')

  // 클릭된 노드의 직접 연결 엣지(1단계)를 토글 상태와 무관하게 강조 표시
  useEffect(() => {
    if (!clickedNodeId) return
    const connectedIds = new Set(
      rawEdgesCache
        .filter(e => e.source === clickedNodeId || e.target === clickedNodeId)
        .map(e => e.id)
    )
    setEdges(eds => eds.map(e => {
      if (!connectedIds.has(e.id)) return e
      return { ...e, hidden: false, style: { ...((e.style as object) ?? {}), opacity: 1 } }
    }))
  }, [clickedNodeId, rawEdgesCache, setEdges])

  // 레이어 섹션 박스 opaque 토글 — 섹션은 원래 크기 유지 + opaqueColor로 덮고, 내부 노드 전부 hidden
  const toggleLayerOpaque = useCallback((layer: string) => {
    setOpaqueLayerSet((prev) => {
      const next = new Set(prev)
      if (next.has(layer)) next.delete(layer)
      else next.add(layer)
      const isOpaque = next.has(layer)
      const sectionId = `layer-section-${layer}`
      setNodes((nds) => {
        const groupIds = new Set(nds.filter((n) => n.parentId === sectionId).map((n) => n.id))
        const fileIds = new Set(nds.filter((n) => n.parentId && groupIds.has(n.parentId)).map((n) => n.id))
        const funcIds = new Set(nds.filter((n) => n.parentId && fileIds.has(n.parentId)).map((n) => n.id))
        return nds.map((n) => {
          if (n.id === sectionId) {
            return { ...n, data: { ...n.data, opaque: isOpaque } }
          }
          if (groupIds.has(n.id) || fileIds.has(n.id) || funcIds.has(n.id)) {
            return { ...n, hidden: isOpaque }
          }
          return n
        })
      })
      return next
    })
  }, [setNodes])

  // 도메인 섹션 opaque 토글 — 섹션 덮기 + 내부 파일/함수 노드 hidden (domain 모드 전용)
  const toggleDomainOpaque = useCallback((domain: string) => {
    setOpaqueDomainSet((prev) => {
      const next = new Set(prev)
      if (next.has(domain)) next.delete(domain)
      else next.add(domain)
      const isOpaque = next.has(domain)
      setNodes((nds) => nds.map((n) => {
        if (n.id === `domain-section-${domain}`) return { ...n, data: { ...n.data, opaque: isOpaque } }
        if ((n.data.domain as string) === domain) return { ...n, hidden: isOpaque }
        return n
      }))
      return next
    })
  }, [setNodes])

  // 도메인 범례 클릭 — 해당 도메인의 흐름 재생 진입점 목록을 사이드바에 표시
  const openDomainFlows = useCallback((domain: string, color: string) => {
    const domainNodes = rawNodes.filter(n => extractDomain(n.filePath, commonPrefix, knownDomains) === domain)
    const domainNodeIds = new Set(domainNodes.map(n => n.id))
    const entryPoints = [
      ...domainNodes.filter(n => n.type === 'API_ENDPOINT'),
      ...domainNodes.filter(n => n.type === 'FUNCTION')
        .filter(n => !rawEdgesCache.some(e => e.type === 'FUNCTION_CALL' && e.target === n.id && !domainNodeIds.has(e.source))),
    ].slice(0, 15)
    const flows = entryPoints.map(ep => {
      const previewIds: string[] = [ep.id]
      let cur = ep.id
      for (let i = 0; i < 3; i++) {
        const next = rawEdgesCache.find(e => CALL_FLOW_TYPES.includes(e.type ?? '') && e.source === cur)
        if (!next) break
        previewIds.push(next.target)
        cur = next.target
      }
      const preview = previewIds.map(id => {
        const n = rawNodes.find(n => n.id === id)
        return n?.comment || n?.name || id
      })
      return { id: ep.id, label: ep.comment || ep.name, preview }
    })
    setSidebar({ kind: 'domain-summary', domainName: domain, color, flows })
    setRightCollapsed(false)
  }, [rawNodes, rawEdgesCache, commonPrefix, knownDomains])

  // 파일 연결 보기 — 사이드바 오픈 콜백
  const openFileSidebar = useCallback((data: FileSidebarData) => {
    setSidebar({ kind: 'file', data })
    setAnnotationNodeId(data.nodeId)
    const rawFile = rawNodes.find(n => n.id === data.nodeId)
    setAnnotationLabel(rawFile?.userLabel ?? '')
    setAnnotationNote(rawFile?.userNote ?? '')
    setRightCollapsed(false)
  }, [rawNodes])
  // openFileSidebar는 rawNodes에 의존해 매 fetchGraph 호출 후 재생성됨 → ref로 안정화
  const openFileSidebarRef = useRef(openFileSidebar)
  useEffect(() => { openFileSidebarRef.current = openFileSidebar }, [openFileSidebar])

  // GraphPage 언마운트 시 body 배경이미지 정리
  useEffect(() => () => { document.body.classList.remove('has-bg') }, [])

  // bgEnabled 또는 bgUrl 변화 시 has-bg 클래스 동기화
  useEffect(() => {
    if (bgEnabled && bgUrl) {
      document.body.classList.add('has-bg')
    } else {
      document.body.classList.remove('has-bg')
    }
  }, [bgEnabled, bgUrl])

  // 배경이미지 표시/숨김 토글
  const toggleBg = useCallback(() => {
    setBgEnabled(v => {
      const next = !v
      localStorage.setItem('graphBgEnabled', String(next))
      return next
    })
  }, [])

  // 흐름 재생 초기화 — 공유 훅 리셋 + GraphPage 전용 클릭 노드 해제
  const resetPlayback = useCallback(() => {
    resetFlowPlayback()
    setClickedNodeId(null)
  }, [resetFlowPlayback, setClickedNodeId])

  // 서버에서 그래프 데이터를 불러와 React Flow 레이아웃으로 변환
  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`)
      type WarningItem = { type: string; severity?: 'HIGH' | 'MEDIUM' | 'LOW'; nodeIds: string[]; edgeIds?: string[]; message: string; fingerprint?: string }
      const { graphId: gid, nodes: rn, edges: re, warnings: w, suppressedWarnings: sw, analyzedFileCount, totalFileCount, ownRepo: or } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[]; warnings?: WarningItem[]; suppressedWarnings?: WarningItem[]; analyzedFileCount?: number; totalFileCount?: number; ownRepo?: boolean }
      setGraphId(gid)
      setOwnRepo(or ?? null)
      // 500개 초과 절단 시에만 안내 배너 (기존 그래프는 카운트 없음)
      setTruncation(
        totalFileCount != null && analyzedFileCount != null && totalFileCount > analyzedFileCount
          ? { analyzed: analyzedFileCount, total: totalFileCount }
          : null
      )
      const warningList = w ?? []
      setWarnings(warningList)
      setSuppressedWarnings(sw ?? [])
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset, openFileSidebarRef.current)
      setRawNodes(rn)
      setRawEdgesCache(re)
      // bgColor 초기화 — 서버에서 받은 색상을 즉시 반영 맵에 저장
      const bgMap: Record<string, string> = {}
      rn.forEach(n => { if (n.bgColor) bgMap[n.id] = n.bgColor })
      setNodeColorMap(bgMap)
      const warnIds = new Set(warningList.flatMap(x => x.nodeIds))
      const warnEdgeIds = new Set(warningList.flatMap(x => x.edgeIds ?? []))
      const styledNodes = layoutNodes.map(n => {
        const bg = bgMap[n.id]
        let style = (n.style as object) ?? {}
        if (bg) style = { ...style, background: bg }
        if (warnIds.has(n.id)) style = { ...style, outline: '2px solid #eab308', outlineOffset: '2px' }
        return { ...n, style }
      })
      const baseEdges = layoutEdges.filter((e, i, arr) => arr.findIndex(x => x.id === e.id) === i)
      const styledEdges = baseEdges.map(e =>
        warnEdgeIds.has(e.id) ? { ...e, style: { ...((e.style as object) ?? {}), stroke: '#eab308', strokeWidth: 2 }, animated: true } : e
      )
      setNodes(styledNodes.filter((n, i, arr) => arr.findIndex(x => x.id === n.id) === i))
      setEdges(applyEdgeVisibility(styledEdges, false, false, false, true, false, true))
      const fileCount = rn.filter((n) => n.type === 'FILE').length
      const funcCount = rn.filter((n) => n.type === 'FUNCTION').length
      const dbCount = rn.filter((n) => n.type === 'DB_TABLE').length
      const apiCount = rn.filter((n) => n.type === 'API_ENDPOINT').length
      setCounts({ files: fileCount, funcs: funcCount, edges: re.length })
      // 분석 완료 직후 진입 시 결과 카드 표시 — 구조 카운트 + 경고 가치
      if (searchParams.get('fresh') === '1' && fileCount > 0) {
        const high = warningList.filter((w) => w.severity === 'HIGH').length
        const medium = warningList.filter((w) => w.severity === 'MEDIUM').length
        const low = warningList.filter((w) => w.severity === 'LOW').length
        setResultCard({
          files: fileCount, funcs: funcCount, db: dbCount, api: apiCount,
          high, medium, low, warnTotal: warningList.length,
          lowCoverage: fileCount < 5,
        })
      }
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)
      if (!isTourDone(GRAPH_TOUR_STORAGE_KEY)) setTimeout(() => setTourRunning(true), 800)
    } catch {
      setError(t('communityPostGraph.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [projectId, setNodes, setEdges, applyEdgeVisibility, fitView, t])
  // 예외 규칙 변경 후 경고를 재조회하기 위해 최신 fetchGraph를 ref에 보관
  fetchGraphRef.current = fetchGraph

  useEffect(() => {
    axios.get<{ id: string; graphBgUrl?: string | null }>('/api/auth/me')
      .then((res) => {
        setCurrentUserId(res.data.id)
        setBgUrl(res.data.graphBgUrl ?? null)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    fetchGraph().then(() => {
      axios.get(`/api/projects/${projectId}/freshness`)
        .then((res) => {
          if (res.data.isOutdated) {
            setOutdated({ branch: res.data.branch, lastAnalyzedAt: res.data.lastAnalyzedAt })
          } else if (res.data.reason === 'rate_limit' || res.data.reason === 'github_error') {
            setFreshnessError(res.data.reason)
          }
        })
        .catch(() => {})
    })
  }, [fetchGraph, projectId])

  // 배너에서 바로 재분석 시작 — 완료 후 그래프 새로고침
  const handleReanalyzeNow = async () => {
    if (!outdated || reanalyzing) return
    setReanalyzing(true)
    try {
      const res = await axios.post<{ analysisId: string }>(
        '/api/analyses',
        { projectId, branch: outdated.branch }
      )
      const analysisId = res.data.analysisId
      const poll = setInterval(async () => {
        try {
          const s = await axios.get<{ status: string }>(`/api/analyses/${analysisId}`)
          if (s.data.status === 'COMPLETED' || s.data.status === 'FAILED') {
            clearInterval(poll)
            setReanalyzing(false)
            setOutdated(null)
            if (s.data.status === 'COMPLETED') fetchGraph()
          }
        } catch { clearInterval(poll); setReanalyzing(false) }
      }, 2000)
    } catch {
      setReanalyzing(false)
    }
  }

  // 커뮤니티에 그래프 첨부 게시글 제출
  const handleShareSubmit = async () => {
    if (!shareTitle.trim() || !shareContent.trim() || !projectId) return
    setShareSubmitting(true)
    try {
      await axios.post(
        '/api/community/posts',
        {
          title: shareTitle,
          content: shareContent,
          feedbackType: shareFeedbackType,
          graphSnapshots: [{ projectId, presetSlot: sharePresetSlot }],
          visibility: shareVisibility,
        }
      )
      setShowShareModal(false)
      setShareTitle('')
      setShareContent('')
      setSharePresetSlot(1)
      setShareVisibility('PUBLIC')
      alert('커뮤니티에 게시글이 등록되었습니다.')
    } catch {
      alert('게시 실패. 다시 시도해주세요.')
    } finally {
      setShareSubmitting(false)
    }
  }

  // 현재 뷰 상태를 프리셋 config로 직렬화
  const buildCurrentConfig = useCallback(() => ({
    layoutPreset,
    labelMode,
    edges: {
      import: showEdges,
      call: showCallEdges,
      inst: showInstEdges,
      broken: showBrokenEdges,
      db: showDbEdges,
      api: showApiCallEdges,
    },
    opaqueLayerSet: Array.from(opaqueLayerSet),
  }), [layoutPreset, labelMode, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges, opaqueLayerSet])

  // 프리셋 config를 현재 뷰 상태에 적용
  const applyPresetConfig = useCallback((config: Record<string, unknown>) => {
    const lp = (config.layoutPreset as LayoutPreset) ?? 'domain'
    const lm = (config.labelMode as LabelMode) ?? 'name'
    const edgeConfig = (config.edges as Record<string, boolean>) ?? {}
    const se = edgeConfig.import ?? false
    const sc = edgeConfig.call ?? false
    const si = edgeConfig.inst ?? false
    const sb = edgeConfig.broken ?? true
    const sdb = edgeConfig.db ?? false
    const sapi = edgeConfig.api ?? true
    const newOpaqueSet = new Set((config.opaqueLayerSet as string[]) ?? [])

    setLayoutPreset(lp)
    setLabelMode(lm)
    setShowEdges(se)
    setShowCallEdges(sc)
    setShowInstEdges(si)
    setShowBrokenEdges(sb)
    setShowDbEdges(sdb)
    setShowApiCallEdges(sapi)
    setOpaqueLayerSet(newOpaqueSet)

    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, lm, lp, openFileSidebar)
      setNodes(() => {
        if (lp === 'domain') {
          return layoutNodes
        }
        // layer 모드: opaque 섹션의 자손 노드(group→file→function 3단계) hidden 처리
        const opaqueSectionIds = new Set(
          layoutNodes
            .filter((n) => n.id.startsWith('layer-section-') && newOpaqueSet.has(n.id.replace('layer-section-', '')))
            .map((n) => n.id)
        )
        const groupIds = new Set(layoutNodes.filter((n) => n.parentId && opaqueSectionIds.has(n.parentId!)).map((n) => n.id))
        const fileIds  = new Set(layoutNodes.filter((n) => n.parentId && groupIds.has(n.parentId!)).map((n) => n.id))
        const hiddenIds = new Set([...groupIds, ...fileIds,
          ...layoutNodes.filter((n) => n.parentId && fileIds.has(n.parentId!)).map((n) => n.id)])
        return layoutNodes.map((n) => {
          if (n.id.startsWith('layer-section-')) {
            const isOpaque = newOpaqueSet.has(n.id.replace('layer-section-', ''))
            return { ...n, hidden: false, data: { ...n.data, opaque: isOpaque } }
          }
          if (hiddenIds.has(n.id)) return { ...n, hidden: true }
          return n
        })
      })
      setEdges(applyEdgeVisibility(layoutEdges, se, sc, si, sb, sdb, sapi))
    }
  }, [rawNodes, rawEdgesCache, setNodes, setEdges, openFileSidebar, applyEdgeVisibility])

  // 그래프의 프리셋 목록 로드
  const loadPresets = useCallback(async (gid: string) => {
    try {
      const res = await axios.get(`/api/graphs/${gid}/presets`)
      setPresets(res.data)
    } catch {
      // 프리셋 로드 실패 무시
    }
  }, [])

  // graphId 변경 시 프리셋 로드
  useEffect(() => {
    if (graphId) loadPresets(graphId)
  }, [graphId, loadPresets])

  // 슬롯에 현재 뷰 저장
  const handleSavePreset = useCallback(async () => {
    if (!graphId || pendingSaveSlot === null || !presetSaveName.trim()) return
    setPresetSaving(true)
    try {
      const config = buildCurrentConfig()
      await axios.put(
        `/api/graphs/${graphId}/presets/${pendingSaveSlot}`,
        { name: presetSaveName.trim(), config }
      )
      await loadPresets(graphId)
      setShowSavePresetModal(false)
      setPendingSaveSlot(null)
      setPresetSaveName('')
    } catch {
      alert('저장 실패. 다시 시도해주세요.')
    } finally {
      setPresetSaving(false)
    }
  }, [graphId, pendingSaveSlot, presetSaveName, buildCurrentConfig, loadPresets])

  // 버전 목록을 서버에서 불러오는 함수
  const handleLoadVersions = useCallback(async () => {
    if (showVersions) { setShowVersions(false); return }
    setLoadingVersions(true)
    setShowVersions(true)
    try {
      const res = await axios.get(`/api/projects/${projectId}/graphs`)
      setVersions(res.data)
    } finally {
      setLoadingVersions(false)
    }
  }, [projectId, showVersions])

  // 특정 버전의 그래프를 로드
  const handleLoadVersion = useCallback(async (targetGraphId: string) => {
    setLoading(true)
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph?graphId=${targetGraphId}`)
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
      setError(t('graphPage.versionLoadFailed'))
    } finally {
      setLoading(false)
    }
  }, [projectId, labelMode, layoutPreset, openFileSidebar, setNodes, setEdges, applyEdgeVisibility, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges, t])

  // 현재 보는 버전 라벨 표시용 — 버전 목록을 조용히 로드 (graphId 변경 시 갱신)
  useEffect(() => {
    if (!projectId || !graphId) return
    axios.get(`/api/projects/${projectId}/graphs`)
      .then(res => setVersions(res.data))
      .catch(() => {})
  }, [projectId, graphId])

  // 버전 목록을 다시 불러옴 (고정/해제 후 갱신용)
  const refreshVersions = useCallback(async () => {
    if (!projectId) return
    try {
      const res = await axios.get(`/api/projects/${projectId}/graphs`)
      setVersions(res.data)
    } catch { /* 무시 */ }
  }, [projectId])

  // 버전을 고정 슬롯(1~5)에 고정 — 사용 중 슬롯이면 덮어쓰기
  const handlePin = useCallback(async (gId: string, slot: number) => {
    try {
      await axios.put(`/api/projects/${projectId}/graphs/${gId}/pin`, { slot })
      setPinMenuGraphId(null)
      await refreshVersions()
    } catch {
      setError(t('graphPage.pinFailed'))
    }
  }, [projectId, refreshVersions, t])

  // 버전 고정 해제
  const handleUnpin = useCallback(async (gId: string) => {
    try {
      await axios.delete(`/api/projects/${projectId}/graphs/${gId}/pin`)
      await refreshVersions()
    } catch {
      setError(t('graphPage.unpinFailed'))
    }
  }, [projectId, refreshVersions, t])

  // 스케치 노드를 프로젝트별 localStorage에서 로드 — 프로젝트 전환 시 save 준비 플래그 초기화
  useEffect(() => {
    if (!projectId) return
    sketchSaveReady.current = false
    try {
      const raw = localStorage.getItem(`sketch:${projectId}`)
      setSketchNodes(raw ? JSON.parse(raw) : [])
    } catch { setSketchNodes([]) }
  }, [projectId])

  // 스케치 노드 변경 시 localStorage에 저장 — 로드 직후 첫 실행은 건너뛰어 빈 배열 덮어쓰기 방지
  useEffect(() => {
    if (!projectId) return
    if (!sketchSaveReady.current) { sketchSaveReady.current = true; return }
    try { localStorage.setItem(`sketch:${projectId}`, JSON.stringify(sketchNodes)) } catch { /* 무시 */ }
  }, [projectId, sketchNodes])

  // 설계 노드 추가 — 화면에 겹치지 않도록 약간씩 오프셋
  const addSketchNode = useCallback(() => {
    setSketchNodes(prev => {
      const n = prev.length
      // 같은 밀리초에 연속 추가해도 충돌하지 않도록 랜덤 접미사 부여
      const id = `sketch-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      return [...prev, { id, label: '새 설계 노드', x: 120 + (n % 5) * 60, y: 120 + (n % 5) * 60 }]
    })
  }, [])

  // 설계 노드 삭제
  const deleteSketchNode = useCallback((id: string) => {
    setSketchNodes(prev => prev.filter(s => s.id !== id))
  }, [])

  // 설계 노드 라벨 변경
  const relabelSketchNode = useCallback((id: string, label: string) => {
    setSketchNodes(prev => prev.map(s => s.id === id ? { ...s, label } : s))
  }, [])

  // 노드 변경 핸들러 — 스케치 노드 위치 변경은 sketchNodes에 반영(나머지는 기존 처리). sketch id는 useNodesState가 무시
  const handleNodesChange = useCallback((changes: Parameters<typeof onNodesChange>[0]) => {
    const sketchIds = new Set(sketchNodes.map(s => s.id))
    const moves = changes.filter((c): c is Extract<typeof c, { type: 'position' }> =>
      c.type === 'position' && 'position' in c && !!c.position && sketchIds.has(c.id))
    if (moves.length) {
      setSketchNodes(prev => prev.map(s => {
        const m = moves.find(c => c.id === s.id)
        return m && m.position ? { ...s, x: m.position.x, y: m.position.y } : s
      }))
    }
    onNodesChange(changes)
  }, [sketchNodes, onNodesChange])

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

  // 특정 노드 타입의 표시/숨김 토글 — rawNodes에서 ID로 타입 역조회
  const toggleNodeType = useCallback((nodeType: string) => {
    setHiddenNodeTypes(prev => {
      const next = new Set(prev)
      if (next.has(nodeType)) next.delete(nodeType)
      else next.add(nodeType)
      const typeIds = new Set(rawNodes.filter(n => n.type === nodeType).map(n => n.id))
      setNodes(nds => nds.map(n => typeIds.has(n.id) ? { ...n, hidden: next.has(nodeType) } : n))
      return next
    })
  }, [rawNodes, setNodes])

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


  // 계층형 ↔ 도메인 뷰 전환
  const toggleLayoutPreset = useCallback(() => {
    const next: LayoutPreset = layoutPreset === 'layer' ? 'domain' : 'layer'
    setLayoutPreset(next)
    setOpaqueLayerSet(new Set())
    setOpaqueDomainSet(new Set())
    if (rawNodes.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodes, rawEdgesCache, labelMode, next, openFileSidebar)
      setNodes(ln)
      setEdges(applyEdgeVisibility(le, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges))
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }, [layoutPreset, rawNodes, rawEdgesCache, labelMode, setNodes, setEdges, fitView, openFileSidebar, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges, applyEdgeVisibility])

  // 키보드 단축키 — / 검색 포커스, Esc 사이드바 닫기, f fitView, l 라벨 전환
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      if (e.key === '/') {
        e.preventDefault()
        searchInputRef.current?.focus()
        searchInputRef.current?.select()
      } else if (e.key === 'Escape') {
        setSidebar(null)
      } else if (e.key === 'f' || e.key === 'F') {
        fitView({ padding: 0.1, duration: 300 })
      } else if (e.key === 'l' || e.key === 'L') {
        toggleLabelMode()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [fitView, toggleLabelMode])

  // 검색어 하이라이트 — 150ms 디바운스로 키 입력마다 전체 노드 순회 방지
  useEffect(() => {
    const timer = setTimeout(() => {
      const q = nodeSearchQuery.trim().toLowerCase()
      if (!q) {
        setNodes(nds => nds.map(n => ({ ...n, style: { ...(n.style as object ?? {}), opacity: undefined } })))
        return
      }
      const matchIds = new Set(
        rawNodes
          .filter(n => n.name.toLowerCase().includes(q) || (n.comment ?? '').toLowerCase().includes(q))
          .map(n => n.id)
      )
      setNodes(nds => nds.map(n => ({
        ...n,
        style: { ...(n.style as object ?? {}), opacity: matchIds.has(n.id) ? 1 : 0.1 },
      })))
    }, 150)
    return () => clearTimeout(timer)
  }, [nodeSearchQuery, rawNodes, setNodes])

  // 전체 그래프를 원본 크기 PNG로 다운로드
  // "AI 컨텍스트 (.md)" 다운로드 — 생성은 백엔드(RepoMapService)가 담당, 프론트는 결과를 받아 파일로 저장만 한다
  const handleDownloadContextMd = useCallback(async (level: 'full' | 'summary' = 'full') => {
    if (!projectId) return
    setExportingContextMd(true)
    try {
      const params = new URLSearchParams({ level })
      if (graphId) params.set('graphId', graphId)
      const url = `/api/projects/${projectId}/graph/context-md?${params.toString()}`
      const res = await axios.get<{ content: string }>(url)
      const md = res.data.content
      const rootName = md.match(/^# (.+?) — /)?.[1] ?? 'codeprint'
      const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
      const dlUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = dlUrl
      a.download = `${rootName}-structure${level === 'summary' ? '-summary' : ''}.md`
      a.click()
      URL.revokeObjectURL(dlUrl)
    } finally {
      setExportingContextMd(false)
    }
  }, [projectId, graphId])

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
      { x: node.position.x, y: node.position.y }
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
    resetPlayback()
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
  }, [rawNodes, rawEdgesCache, resetPlayback])

  // 함수/API_ENDPOINT 노드 선택 — 사이드바 갱신 + 흐름 재생 시작. 사이드바 링크 클릭(onNav)에서도 재사용
  const openFuncNode = useCallback((nodeId: string) => {
    const rawFunc = rawNodes.find((n) => n.id === nodeId && (n.type === 'FUNCTION' || n.type === 'API_ENDPOINT'))
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
      .map((e) => rawNodes.find((n) => n.id === e.source && (n.type === 'FUNCTION' || n.type === 'API_ENDPOINT')))
      .filter((n): n is RawNode => !!n && n.filePath !== rawFunc.filePath)
      .map(toEntry)

    const callees: FuncCallChainEntry[] = rawEdgesCache
      .filter((e) => e.type === 'FUNCTION_CALL' && e.source === rawFunc.id)
      .map((e) => rawNodes.find((n) => n.id === e.target && (n.type === 'FUNCTION' || n.type === 'API_ENDPOINT')))
      .filter((n): n is RawNode => !!n && n.filePath !== rawFunc.filePath)
      .map(toEntry)

    setSidebar({
      kind: 'func',
      nodeId: rawFunc.id,
      funcName: rawFunc.name,
      funcComment: rawFunc.comment ?? null,
      parentFileName: parentFile?.name ?? rawFunc.filePath,
      parentFileNodeId: parentFile?.id ?? '',
      callers,
      callees,
    })
    setAnnotationNodeId(rawFunc.id)
    setAnnotationLabel(rawFunc.userLabel ?? '')
    setAnnotationNote(rawFunc.userNote ?? '')
    startPlayback(rawFunc.id)
    setCommentNodeId(rawFunc.id)
    setCommentInput('')
    if (graphId) {
      axios.get(`/api/graphs/${graphId}/nodes/${rawFunc.id}/comments`)
        .then((res) => setNodeComments(res.data))
        .catch(() => setNodeComments([]))
    }
    setRightCollapsed(false)
  }, [rawNodes, rawEdgesCache, graphId, startPlayback])

  // ?nodeId= 딥링크 — 그래프 로드 후 해당 노드 자동 선택 + fitView
  const deepLinkApplied = useRef(false)
  useEffect(() => {
    const targetNodeId = searchParams.get('nodeId')
    if (!targetNodeId || deepLinkApplied.current || rawNodes.length === 0) return
    deepLinkApplied.current = true
    setTimeout(() => {
      openFuncNode(targetNodeId)
      setTimeout(() => fitView({ nodes: [{ id: targetNodeId }], duration: 600, padding: 0.4 }), 50)
    }, 400)
  }, [rawNodes, searchParams, openFuncNode, fitView])

  // 노드 클릭 이벤트 처리
  const handleNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    publishSelection(node.id)
    if (node.type === 'fileNode' || node.type === 'groupNode') {
      resetPlayback()
      return
    }
    if (node.type !== 'sectionNode') setClickedNodeId(node.id)

    // 도메인 뷰에서 섹션 클릭 — 해당 도메인의 주요 흐름 목록 표시
    if (node.type === 'sectionNode' && layoutPreset === 'domain') {
      const domainName = (node.data?.layer as string ?? '').toLowerCase()
      const palette = domainColorMap.get(domainName) ?? { color: '#6b7280' }
      const domainNodes = rawNodes.filter(n => extractDomain(n.filePath, commonPrefix, knownDomains) === domainName)
      const domainNodeIds = new Set(domainNodes.map(n => n.id))
      const entryPoints2 = [
        ...domainNodes.filter(n => n.type === 'API_ENDPOINT'),
        ...domainNodes.filter(n => n.type === 'FUNCTION')
          .filter(n => !rawEdgesCache.some(e => e.type === 'FUNCTION_CALL' && e.target === n.id && !domainNodeIds.has(e.source))),
      ].slice(0, 15)
      const flows2 = entryPoints2.map(ep => {
        const previewIds: string[] = [ep.id]
        let c = ep.id
        for (let i = 0; i < 3; i++) {
          const nx = rawEdgesCache.find(e => CALL_FLOW_TYPES.includes(e.type ?? '') && e.source === c)
          if (!nx) break
          previewIds.push(nx.target)
          c = nx.target
        }
        const preview = previewIds.map(id => { const n = rawNodes.find(n => n.id === id); return n?.comment || n?.name || id })
        return { id: ep.id, label: ep.comment || ep.name, preview }
      })
      setSidebar({ kind: 'domain-summary', domainName, color: palette.color, flows: flows2 })
      setRightCollapsed(false)
      resetPlayback()
      return
    }

    if (node.type === 'sectionNode') {
      resetPlayback()
      return
    }

    // 경고가 있는 노드 클릭 — 우측 사이드바에 경고 상세 표시 (다른 사이드바보다 우선)
    const nodeWarnings = warnings.filter(w => w.nodeIds.includes(node.id))
    if (nodeWarnings.length > 0) {
      const rawNode = rawNodes.find(n => n.id === node.id)
      setSidebar({ kind: 'warning', nodeName: rawNode?.name ?? node.id, nodeWarnings })
      setRightCollapsed(false)
    }

    // DB_TABLE 노드 클릭 — 칼럼 목록 + 연결된 Repository 표시 + 흐름 재생
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
      startPlayback(node.id)
      setRightCollapsed(false)
      return
    }

    openFuncNode(node.id)
  }, [rawNodes, rawEdgesCache, commonPrefix, knownDomains, layoutPreset, openFuncNode, startPlayback, resetPlayback])

  // 노드 배경색 변경 — PUT API 호출 후 즉시 UI 반영
  const handleNodeColorChange = useCallback(async (nodeId: string, bgColor: string) => {
    if (!graphId) return
    try {
      await axios.put(`/api/graphs/${graphId}/nodes/${nodeId}/style`, { bgColor })
      setNodeColorMap(prev => ({ ...prev, [nodeId]: bgColor }))
      setNodes(nds => nds.map(n => {
        if (n.id !== nodeId) return n
        const existingStyle = (n.style as object) ?? {}
        const newStyle = bgColor ? { ...existingStyle, background: bgColor } : Object.fromEntries(Object.entries(existingStyle).filter(([k]) => k !== 'background'))
        return { ...n, style: newStyle }
      }))
    } catch { /* 실패 시 무시 */ }
  }, [graphId, setNodes])

  // ReactFlow에 전달할 노드 — showDomainBoxes 꺼지면 sectionNode 제외 (매 렌더마다 재생성 방지)
  // 탭 필터링: activeDomainTab이 설정되면 해당 도메인/레이어 노드만 표시 (성능 핵심 최적화)
  const tabFilteredNodeIds = useMemo(() => {
    if (!activeDomainTab) return null
    const isLayerMode = layoutPreset === 'layer'
    const visibleRaw = rawNodes.filter(n => {
      if (isLayerMode) {
        // 계층형: filePath에서 레이어 폴더명 매칭
        const rel = n.filePath.startsWith(commonPrefix) ? n.filePath.slice(commonPrefix.length) : n.filePath
        const parts = rel.replace(/\\/g, '/').split('/').filter(Boolean)
        return parts.some(p => p.toLowerCase() === activeDomainTab)
      } else {
        // 도메인형: extractDomain 기준
        return extractDomain(n.filePath, commonPrefix, knownDomains) === activeDomainTab
      }
    })
    return new Set(visibleRaw.map(n => n.id))
  }, [activeDomainTab, rawNodes, commonPrefix, knownDomains, layoutPreset])

  // DB 테이블 노드 ID 집합 — 탭(단일 도메인) 활성 시 DB를 도메인 옆으로 재배치하는 데 사용
  const dbTableIdSet = useMemo(() => new Set(rawNodes.filter(n => n.type === 'DB_TABLE').map(n => n.id)), [rawNodes])

  const displayNodes = useMemo(() => {
    let result = showDomainBoxes ? nodes : nodes.filter(n => n.type !== 'sectionNode')
    if (tabFilteredNodeIds) {
      result = result.filter(n => {
        if (tabFilteredNodeIds.has(n.id)) return true
        // sectionNode: 활성 도메인의 박스만 남김 (data.layer가 도메인 이름)
        if (n.type === 'sectionNode') {
          const layer = (n.data?.layer as string | undefined)?.toLowerCase() ?? ''
          return layer === activeDomainTab
        }
        // groupNode: 활성 도메인 sectionNode 자식만 남김 (parentId가 tabFilteredNodeIds에 포함된 sectionNode)
        if (n.type === 'groupNode') {
          return !!n.parentId && nodes.some(
            p => p.id === n.parentId && p.type === 'sectionNode' &&
                 ((p.data?.layer as string | undefined)?.toLowerCase() ?? '') === activeDomainTab
          )
        }
        return false
      })
    }
    // 탭(단일 도메인) 활성 시 — 관련 DB 테이블을 도메인 섹션 바로 아래에 붙여 배치
    // (전역 DB 열 위치에서 도메인 옆으로. 우측 흐름 사이드바에 가리지 않도록 아래쪽·도메인 폭 안에서 줄바꿈)
    if (activeDomainTab && layoutPreset === 'domain') {
      const section = nodes.find(n => n.id === `domain-section-${activeDomainTab}`)
      if (section) {
        const sx = section.position.x
        const sy = section.position.y
        const sw = (section.style?.width as number) ?? (section.width as number) ?? 0
        const sh = (section.style?.height as number) ?? (section.height as number) ?? 0
        const GAP = 16
        let cx = sx, cy = sy + sh + 48, rowH = 0
        result = result.map(n => {
          if (!dbTableIdSet.has(n.id)) return n
          const w = (n.width as number) ?? (n.style?.width as number) ?? 160
          const h = (n.height as number) ?? (n.style?.height as number) ?? 48
          if (cx > sx && cx + w > sx + Math.max(sw, w)) { cx = sx; cy += rowH + GAP; rowH = 0 }
          const pos = { x: cx, y: cy }
          cx += w + GAP
          rowH = Math.max(rowH, h)
          return { ...n, position: pos }
        })
      }
    }
    // 스케치 노드를 기존 레이아웃 위에 덧붙임 (buildLayout을 거치지 않는 별도 레이어 — 기존 노드 합성에 영향 없음)
    if (sketchNodes.length > 0) {
      const sketchRF = sketchNodes.map(s => ({
        id: s.id,
        type: 'sketch',
        position: { x: s.x, y: s.y },
        data: { label: s.label, onDelete: deleteSketchNode, onRelabel: relabelSketchNode },
        draggable: true,
        zIndex: 1000,
      })) as unknown as typeof result
      return [...result, ...sketchRF]
    }
    return result
  }, [showDomainBoxes, nodes, tabFilteredNodeIds, activeDomainTab, layoutPreset, dbTableIdSet, sketchNodes, deleteSketchNode, relabelSketchNode])

  // 탭 필터링된 엣지 — 양쪽 노드가 모두 표시될 때만 보여줌
  // 현재 사이드바에서 열린 노드 ID (엣지 온디맨드 표시용)
  const focusedNodeId = useMemo<string | null>(() => {
    if (!sidebar) return null
    if (sidebar.kind === 'func' || sidebar.kind === 'db-table') return sidebar.nodeId
    if (sidebar.kind === 'func-call') return sidebar.callerNodeId
    if (sidebar.kind === 'file') return (sidebar.data as { nodeId?: string }).nodeId ?? null
    return null
  }, [sidebar])

  const displayEdges = useMemo(() => {
    let baseEdges = tabFilteredNodeIds
      ? edges.filter(e => tabFilteredNodeIds.has(e.source) && tabFilteredNodeIds.has(e.target))
      : []
    // 도메인 활성 시 — 그 도메인의 DB 연결선을 토글과 무관하게 표시 (DB가 도메인에 붙어있음을 시각화)
    if (activeDomainTab) {
      baseEdges = baseEdges.map(e => isDbEdgeType((e.data as { type?: string } | undefined)?.type) ? { ...e, hidden: false } : e)
    }
    // 노드가 선택됐으면 그 노드에 연결된 엣지 추가 표시 (탭 내부 + 전체 탭 모두)
    if (focusedNodeId) {
      const connectedEdges = edges.filter(e => e.source === focusedNodeId || e.target === focusedNodeId)
      const combined = [...baseEdges]
      connectedEdges.forEach(e => { if (!combined.find(b => b.id === e.id)) combined.push(e) })
      return combined
    }
    return baseEdges
  }, [edges, tabFilteredNodeIds, focusedNodeId, activeDomainTab])

  // 현재 레이아웃 기준으로 사용 가능한 탭 목록
  const availableTabs = useMemo(() => {
    if (layoutPreset === 'layer') {
      const layers = ['pages', 'components', 'hooks', 'interfaces', 'application', 'domain', 'infrastructure']
      const used = new Set(rawNodes.flatMap(n => {
        const rel = n.filePath.startsWith(commonPrefix) ? n.filePath.slice(commonPrefix.length) : n.filePath
        return rel.replace(/\\/g, '/').split('/').filter(Boolean).map(p => p.toLowerCase())
      }))
      return layers.filter(l => used.has(l))
    } else {
      // 도메인별 노드 수 계산 — 최소 3개 이상인 도메인만 탭으로 노출 (파일명 기반 파편화 방지)
      const domainCounts = new Map<string, number>()
      rawNodes.forEach(n => {
        const d = extractDomain(n.filePath, commonPrefix, knownDomains)
        domainCounts.set(d, (domainCounts.get(d) ?? 0) + 1)
      })
      const MIN_NODES = 3
      return [...domainCounts.entries()]
        .filter(([, count]) => count >= MIN_NODES)
        .map(([d]) => d)
        .sort((a, b) => {
          if (a === 'common') return 1
          if (b === 'common') return -1
          return a.localeCompare(b)
        })
    }
  }, [rawNodes, commonPrefix, knownDomains, layoutPreset])

  // 도메인 색상 맵 — 발견된 도메인 이름으로 동적 생성
  const domainColorMap = useMemo(() => {
    const domains = rawNodes.map(n => extractDomain(n.filePath, commonPrefix, knownDomains))
    return buildDomainColorMap(domains)
  }, [rawNodes, commonPrefix, knownDomains])

  // 레이어 섹션 키+라벨+색상 목록 — 고정 DDD 8종 목록 대신 실제 렌더된 섹션에서 동적으로 파생(비DDD 프로젝트도 커버)
  const layerSections = useMemo(() =>
    nodes.filter(n => n.id.startsWith('layer-section-')).map(n => ({
      key: n.id.replace('layer-section-', ''),
      label: String(n.data?.label ?? ''),
      color: String(n.data?.color ?? '#6b7280'),
    })),
    [nodes]
  )

  // 도메인 활성화 — 좌측 도메인 클릭 시 [그래프 필터 + 우측 흐름 패널 + 확대]를 한 번에 수행 (상단 탭 기능 통합)
  const activateDomain = useCallback((key: string) => {
    setActiveDomainTab(key)
    if (layoutPreset === 'domain') {
      const color = domainColorMap.get(key)?.color ?? '#6b7280'
      openDomainFlows(key, color)
    }
  }, [layoutPreset, domainColorMap, openDomainFlows])

  // 전체 보기로 리셋 — 필터 해제 + 도메인 요약 사이드바 닫기 + 전체 맞춤
  const resetDomainTab = useCallback(() => {
    setActiveDomainTab(null)
    setSidebar(prev => prev?.kind === 'domain-summary' ? null : prev)
    setTimeout(() => fitView({ duration: 400, padding: 0.1 }), 60)
  }, [fitView])

  // 도메인 활성 시 해당 도메인 섹션(+아래 붙은 DB)이 화면을 채우도록 확대.
  // 섹션 좌표·크기는 필터와 무관하게 항상 고정이라, 명시적 사각형(fitBounds)으로 맞추면
  // 대량 노드 교체 직후 React Flow 스토어가 정착되기 전에도 정확히 맞춰진다 (측정 race 회피)
  useEffect(() => {
    if (!activeDomainTab) return
    const t = setTimeout(() => {
      const sectionId = (layoutPreset === 'domain' ? 'domain-section-' : 'layer-section-') + activeDomainTab
      const section = getNodes().find(n => n.id === sectionId)
      if (!section) { fitView({ duration: 450, padding: 0.12 }); return }
      const sx = section.position.x
      const sy = section.position.y
      const sw = (section.style?.width as number) ?? (section.width as number) ?? 600
      const sh = (section.style?.height as number) ?? (section.height as number) ?? 400
      // 도메인 아래 붙은 DB 영역 높이 추정 (displayNodes 배치 규칙과 동일: 섹션 폭 안에서 줄바꿈)
      let dbCount = 0
      if (layoutPreset === 'domain' && tabFilteredNodeIds) dbTableIdSet.forEach(id => { if (tabFilteredNodeIds.has(id)) dbCount++ })
      const perRow = Math.max(1, Math.floor(sw / (160 + 16)))
      const rows = Math.ceil(dbCount / perRow)
      const dbAreaH = dbCount > 0 ? 48 + rows * (48 + 16) : 0
      fitBounds({ x: sx, y: sy, width: sw, height: sh + dbAreaH }, { duration: 0, padding: 0.12 })
    }, 80)
    return () => clearTimeout(t)
  }, [activeDomainTab, layoutPreset, fitView, fitBounds, getNodes, tabFilteredNodeIds, dbTableIdSet])

  // MiniMap 노드 색상 함수 — 인라인 선언 시 매 렌더 MiniMap 전체 재렌더 유발
  const minimapNodeColor = useCallback((n: Node) => {
    const bg = n.style?.background as string
    return bg ?? '#374151'
  }, [])

  // 엣지 토글 섹션 — drag 중 nodes 변경과 무관하게 memoize하여 불필요한 reconcile 방지
  const edgeToggleSectionJsx = useMemo(() => (
    <LeftSection title={t('communityPostGraph.edgeTypes.heading')} id="tour-edges">
      <div className="grid grid-cols-2 gap-x-1 gap-y-0.5">
      {[
        { key: 'import',  icon: <span className="block w-4 h-0.5" style={{ background: showEdges ? '#4b5563' : '#374151' }} />,                                                                                              label: t('communityPostGraph.edgeTypes.import'),    textCls: showEdges ? 'text-gray-300' : 'text-gray-600',        active: showEdges,        onToggle: toggleEdges },
        { key: 'call',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showCallEdges ? '#f59e0b' : '#78350f'} strokeWidth="1.5" strokeDasharray="5 4" /></svg>,                                label: t('communityPostGraph.edgeTypes.call'),   textCls: showCallEdges ? 'text-amber-400' : 'text-gray-600',    active: showCallEdges,    onToggle: toggleCallEdges },
        { key: 'inst',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showInstEdges ? '#a855f7' : '#4c1d95'} strokeWidth="1.5" strokeDasharray="3 4" /></svg>,                                label: t('communityPostGraph.edgeTypes.inst'),      textCls: showInstEdges ? 'text-purple-400' : 'text-gray-600',   active: showInstEdges,    onToggle: toggleInstEdges },
        { key: 'broken',  icon: <span className="block w-4 h-0.5" style={{ background: showBrokenEdges ? '#ef4444' : '#450a0a' }} />,                                                                                        label: t('communityPostGraph.edgeTypes.broken'), textCls: showBrokenEdges ? 'text-red-400' : 'text-gray-600',   active: showBrokenEdges,  onToggle: toggleBrokenEdges },
        { key: 'db',      icon: <svg width="16" height="4"><line x1="0" y1="2" x2="3.5" y2="2" stroke={showDbEdges ? '#22d3ee' : '#374151'} strokeWidth="1.5"/><line x1="4.5" y1="2" x2="8" y2="2" stroke={showDbEdges ? '#4ade80' : '#374151'} strokeWidth="1.5"/><line x1="9" y1="2" x2="12.5" y2="2" stroke={showDbEdges ? '#facc15' : '#374151'} strokeWidth="1.5"/><line x1="13.5" y1="2" x2="16" y2="2" stroke={showDbEdges ? '#f87171' : '#374151'} strokeWidth="1.5"/></svg>, label: t('communityPostGraph.edgeTypes.db'),   textCls: showDbEdges ? 'text-cyan-400' : 'text-gray-600',       active: showDbEdges,      onToggle: toggleDbEdges },
        { key: 'api',     icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showApiCallEdges ? '#e879f9' : '#701a75'} strokeWidth="1.5" strokeDasharray="6 3" /></svg>,                              label: t('communityPostGraph.edgeTypes.api'),  textCls: showApiCallEdges ? 'text-fuchsia-400' : 'text-gray-600', active: showApiCallEdges, onToggle: toggleApiCallEdges },
      ].map(({ key, icon, label, textCls, active, onToggle }) => (
        <div key={key} onClick={onToggle} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onToggle()}
          className={`flex items-center gap-1.5 px-1.5 py-1 rounded cursor-pointer hover:bg-gray-800/60 ${active ? '' : 'opacity-40'}`}>
          <span className="w-4 flex-shrink-0">{icon}</span>
          <span className={`text-xs truncate ${textCls}`}>{label}</span>
        </div>
      ))}
      </div>
    </LeftSection>
  ), [showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges, toggleEdges, toggleCallEdges, toggleInstEdges, toggleBrokenEdges, toggleDbEdges, toggleApiCallEdges, t])

  // 노드 타입 필터 섹션 — drag 중 변경 없음
  const nodeFilterSectionJsx = useMemo(() => (
    <LeftSection title={t('graphPage.nodeFilterTitle')}>
      <div className="grid grid-cols-2 gap-x-1 gap-y-0.5">
        {([
          { type: 'FILE',         label: 'FILE',     color: '#6b7280' },
          { type: 'FUNCTION',     label: 'FUNCTION', color: '#10b981' },
          { type: 'DB_TABLE',     label: 'DB',       color: '#ef4444' },
          { type: 'API_ENDPOINT', label: 'API',      color: '#3b82f6' },
        ] as { type: string; label: string; color: string }[]).map(({ type, label, color }) => {
          const hidden = hiddenNodeTypes.has(type)
          return (
            <button
              key={type}
              onClick={() => toggleNodeType(type)}
              className="flex items-center gap-1.5 px-1.5 py-1 rounded hover:bg-gray-800 transition-colors"
            >
              <span className="w-2 h-2 rounded-sm flex-shrink-0" style={{ background: hidden ? '#374151' : color }} />
              <span className={`text-xs ${hidden ? 'text-gray-600 line-through' : 'text-gray-300'}`}>{label}</span>
            </button>
          )
        })}
      </div>
    </LeftSection>
  ), [hiddenNodeTypes, toggleNodeType, t])

  if (loading) {
    return (
      <div className="app-page min-h-screen bg-gray-950 flex flex-col items-center justify-center gap-3 text-gray-400">
        <svg className="animate-spin w-8 h-8 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
        </svg>
        <p className="text-sm">{t('graphPage.loadingCode')}</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="app-page min-h-screen bg-gray-950 flex flex-col items-center justify-center gap-4">
        <div className="text-4xl">⚠️</div>
        <p className="text-gray-300 font-medium">{t('graphPage.errorHeading')}</p>
        <p className="text-gray-500 text-sm">{error}</p>
        <div className="flex gap-3 mt-2">
          <button onClick={() => window.location.reload()} className="text-sm bg-indigo-700 hover:bg-indigo-600 text-white px-4 py-1.5 rounded-lg">{t('graphPage.retryButton')}</button>
          <button onClick={() => navigate('/mypage')} className="text-sm text-gray-400 hover:text-gray-200 underline">{t('graphPage.toMyPageButton')}</button>
        </div>
      </div>
    )
  }

  const currentVersion = versions.find((v) => v.graphId === graphId)

  return (
    <div className="app-page" style={{ width: '100vw', height: '100vh', background: '#030712', display: 'flex', flexDirection: 'column' }}>
      <div style={{ position: 'relative' }}>
        <AppHeader />
      </div>
    <div ref={flowRef} style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>

      <OnboardingTour run={tourRunning} onFinish={() => setTourRunning(false)} steps={GRAPH_TOUR_STEPS} storageKey={GRAPH_TOUR_STORAGE_KEY} />
      <CursorOverlay cursors={collabState.cursors} />


      {/* GitHub API 한도 초과 또는 오류 시 안내 */}
      {!outdated && freshnessError && (
        <div className="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-4 py-1.5 bg-gray-800/80 border-b border-gray-700 text-gray-400 text-xs backdrop-blur-sm">
          <span>
            {freshnessError === 'rate_limit'
              ? t('graphPage.rateLimitBanner')
              : t('graphPage.githubErrorBanner')}
          </span>
          <button onClick={() => setFreshnessError(null)} className="text-gray-600 hover:text-gray-300 ml-4">✕</button>
        </div>
      )}

      {/* 최신 커밋 감지 배너 */}
      {outdated && (
        <div className="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-4 py-2 bg-yellow-900/80 border-b border-yellow-700 text-yellow-300 text-xs backdrop-blur-sm">
          <span>
            {t('graphPage.outdatedBanner', {
              branch: outdated.branch,
              date: new Date(outdated.lastAnalyzedAt).toLocaleString(currentDateLocale(), { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }),
            })}
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={handleReanalyzeNow}
              disabled={reanalyzing}
              className="px-2 py-0.5 rounded bg-yellow-700/60 hover:bg-yellow-600/80 disabled:opacity-50 text-yellow-100 font-medium"
            >
              {reanalyzing ? t('graphPage.reanalyzing') : t('graphPage.reanalyzeNowButton')}
            </button>
            <button onClick={() => setOutdated(null)} className="text-yellow-500 hover:text-yellow-200">✕</button>
          </div>
        </div>
      )}

      {/* 대형 레포 절단 안내 — 전체 파일 일부만 분석된 그래프 */}
      {!outdated && !freshnessError && truncation && (
        <div className="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-4 py-1.5 bg-orange-900/80 border-b border-orange-700 text-orange-300 text-xs backdrop-blur-sm">
          <span>
            {t('graphViewer.truncationBanner', { total: truncation.total.toLocaleString(), analyzed: truncation.analyzed.toLocaleString() })}
          </span>
          <button onClick={() => setTruncation(null)} className="text-orange-500 hover:text-orange-200 ml-4">✕</button>
        </div>
      )}

      {/* 상단 바 — 내비 + 통계만 */}
      <div className="absolute z-10 flex items-center gap-3" style={{ top: (outdated || freshnessError || truncation) ? '44px' : '16px', left: leftOpen ? `${leftWidth + 8}px` : '20px' }}>
        <button
          onClick={() => navigate('/mypage')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          {t('graphPage.backToMyPageButton')}
        </button>
        <span className="text-gray-500 text-sm">
          {t('graphPage.statsLabel', { files: counts.files, funcs: counts.funcs, edges: counts.edges })}
        </span>
        {ownRepo !== null && (
          <span
            title={ownRepo ? t('graphPage.ownRepoTooltip') : t('graphPage.externalRepoTooltip')}
            className={`text-[11px] px-2 py-0.5 rounded-full border ${
              ownRepo ? 'bg-blue-900/40 border-blue-700/50 text-blue-300' : 'bg-gray-800 border-gray-700 text-gray-400'
            }`}
          >
            {ownRepo ? t('graphViewer.ownRepoBadge') : t('graphViewer.externalRepoBadge')}
          </span>
        )}

        {/* 전역 뷰 컨트롤 — 레이아웃·라벨 토글 + 프리셋·내보내기 팝업 */}
        <span className="w-px h-5 bg-gray-700" />
        <LayoutPresetToggle layoutPreset={layoutPreset} onToggle={toggleLayoutPreset} id="tour-layout" />
        <LabelModeToggle labelMode={labelMode} onToggle={toggleLabelMode} />
        <div className="relative">
          <button
            onClick={() => setOpenToolbarMenu(m => m === 'preset' ? null : 'preset')}
            className={`flex items-center gap-1 text-xs px-2.5 py-1.5 rounded-lg border transition-colors ${openToolbarMenu === 'preset' ? 'bg-gray-700 text-white border-gray-500' : 'bg-gray-800 hover:bg-gray-700 text-gray-300 border-gray-700'}`}
          >
            {t('graphPage.presetButton')} <span className="text-gray-500">▾</span>
          </button>
          {openToolbarMenu === 'preset' && (
            <div className="absolute left-0 top-9 z-50 w-60 bg-gray-900 border border-gray-700 rounded-lg p-2 shadow-xl flex flex-col gap-1">
              <p className="text-[10px] text-gray-500 px-1 pb-0.5">{t('graphPage.presetHint')}</p>
              {presets.map((p) => (
                <div key={p.slot} className="flex items-center gap-1">
                  <button
                    onClick={() => { applyPresetConfig(p.config); setOpenToolbarMenu(null) }}
                    title={t('graphPage.presetLoadTitle', { slot: p.slot })}
                    className="flex-1 text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 truncate"
                  >
                    <span className="text-gray-500 mr-1">{p.slot}.</span>
                    {p.name}
                    {p.isDefault && <span className="ml-1 text-gray-600 text-[10px]">{t('graphPage.presetDefaultBadge')}</span>}
                  </button>
                  <button
                    onClick={() => { setPendingSaveSlot(p.slot); setPresetSaveName(p.name); setShowSavePresetModal(true); setOpenToolbarMenu(null) }}
                    title={t('graphPage.presetSaveTitle', { slot: p.slot })}
                    className="text-gray-600 hover:text-gray-300 text-xs px-1.5 py-1 rounded hover:bg-gray-800 flex-shrink-0"
                  >
                    💾
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
        <div className="relative">
          <button
            id="tour-export"
            onClick={() => setOpenToolbarMenu(m => m === 'export' ? null : 'export')}
            className={`flex items-center gap-1 text-xs px-2.5 py-1.5 rounded-lg border transition-colors ${openToolbarMenu === 'export' ? 'bg-gray-700 text-white border-gray-500' : 'bg-gray-800 hover:bg-gray-700 text-gray-300 border-gray-700'}`}
          >
            {t('graphPage.exportButton')} <span className="text-gray-500">▾</span>
          </button>
          {openToolbarMenu === 'export' && (
            <div className="absolute left-0 top-9 z-50 w-48 bg-gray-900 border border-gray-700 rounded-lg p-2 shadow-xl flex flex-col gap-1">
              <button
                onClick={() => { handleDownloadContextMd('full'); setOpenToolbarMenu(null) }}
                disabled={exportingContextMd || rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {exportingContextMd ? t('graphPage.generating') : t('graphPage.exportContextFull')}
              </button>
              <button
                onClick={() => { handleDownloadContextMd('summary'); setOpenToolbarMenu(null) }}
                disabled={exportingContextMd || rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {exportingContextMd ? t('graphPage.generating') : t('graphPage.exportContextSummary')}
              </button>
              <button
                onClick={() => { handleExportImage(); setOpenToolbarMenu(null) }}
                disabled={exporting || rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {exporting ? t('graphPage.savingImage') : t('graphPage.exportImage')}
              </button>
            </div>
          )}
        </div>
        {openToolbarMenu && (
          <div className="fixed inset-0 z-40" onClick={() => setOpenToolbarMenu(null)} />
        )}
        <span className="w-px h-5 bg-gray-700" />

        {graphId && (
          <button
            onClick={() => setShowShareModal(true)}
            className="bg-blue-600 hover:bg-blue-500 text-white text-sm px-3 py-1.5 rounded-lg"
          >
            {t('graphPage.shareToCommunityButton')}
          </button>
        )}
        {/* 팀채팅 패널 토글 */}
        <button
          onClick={() => setShowTeamChat(v => !v)}
          className={`text-xs px-3 py-1.5 rounded-lg border transition-colors ${
            showTeamChat
              ? 'bg-blue-600 text-white border-blue-600'
              : 'text-gray-400 border-gray-700 hover:border-gray-500 hover:text-white bg-gray-800'
          }`}
        >
          💬 {t('graphPage.teamChatButton')}
        </button>
        {/* 협업 세션 — 팀채팅 옆 */}
        {projectId && currentUserId && (
          <CollaborationPanel
            graphId={projectId}
            myUserId={currentUserId}
            participants={collabState.participants}
            connected={collabState.connected}
            sessionId={collabSessionId}
            inviteCode={collabInviteCode}
            onSessionReady={(sid, code) => { setCollabSessionId(sid); setCollabInviteCode(code) }}
          />
        )}
        {/* 키보드 단축키 도움말 */}
        <div className="relative group">
          <button className="text-gray-500 hover:text-gray-300 text-xs w-6 h-6 rounded-full border border-gray-700 hover:border-gray-500 flex items-center justify-center transition-colors">
            ?
          </button>
          <div className="absolute left-0 top-8 hidden group-hover:block bg-gray-900 border border-gray-700 rounded-lg p-3 text-xs text-gray-300 whitespace-nowrap z-50 shadow-xl">
            <div className="font-semibold text-gray-200 mb-2">{t('graphPage.shortcutsHeading')}</div>
            {([['/', t('graphPage.shortcuts.searchFocus')], ['Esc', t('graphPage.shortcuts.closeSidebar')], ['F', t('graphPage.shortcuts.fitView')], ['L', t('graphPage.shortcuts.toggleLabel')]] as [string, string][]).map(([key, desc]) => (
              <div key={key} className="flex items-center gap-3 py-0.5">
                <kbd className="px-1.5 py-0.5 bg-gray-800 border border-gray-600 rounded text-[10px] font-mono min-w-[24px] text-center">{key}</kbd>
                <span className="text-gray-400">{desc}</span>
              </div>
            ))}
          </div>
        </div>
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
              onClick={() => navigate('/mypage')}
              className="text-xs font-bold text-gray-300 hover:text-white tracking-widest uppercase transition-colors"
            >
              Codeprint
            </button>
            <button onClick={() => setLeftOpen(false)} className="w-7 h-7 flex items-center justify-center rounded-md bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white text-lg leading-none transition-colors" title={t('graphViewer.collapseSidebar')}>‹</button>
          </div>

          <div className="flex flex-col gap-0 flex-1">

            {/* 노드 검색 */}
            <LeftSection title={t('graphViewer.nodeSearchHeading')}>
              <input
                ref={searchInputRef}
                type="text"
                value={nodeSearchQuery}
                onChange={e => setNodeSearchQuery(e.target.value)}
                placeholder={t('graphPage.searchPlaceholder')}
                className="w-full text-xs bg-gray-800 border border-gray-700 rounded px-2 py-1 text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500"
              />
              {nodeSearchQuery.trim() && (() => {
                const results = searchNodes(rawNodes, nodeSearchQuery)
                return results.length === 0 ? (
                  <p className="text-[10px] text-gray-600 mt-1 px-1">{t('graphViewer.noResults')}</p>
                ) : (
                  <div className="mt-1 flex flex-col gap-0.5 max-h-48 overflow-y-auto">
                    {results.map(n => (
                      <button
                        key={n.id}
                        onClick={() => handleSearchNodeClick(n.id)}
                        className="w-full text-left text-[11px] px-2 py-1 rounded hover:bg-gray-700 text-gray-300 truncate"
                        title={n.comment ? `${n.name} — ${n.comment}` : n.name}
                      >
                        <span className="text-gray-600 mr-1">
                          {n.type === 'FILE' ? '📄' : n.type === 'FUNCTION' ? 'ƒ' : n.type === 'DB_TABLE' ? '🗄' : n.type === 'API_ENDPOINT' ? '🔌' : '◎'}
                        </span>
                        {n.comment || n.name}
                      </button>
                    ))}
                  </div>
                )
              })()}
            </LeftSection>


            {/* 보기 옵션 */}
            {(layoutPreset === 'domain' || bgUrl) && (
              <LeftSection title={t('graphViewer.viewSectionHeading')}>
                {layoutPreset === 'domain' && (
                  <button
                    onClick={() => setShowDomainBoxes(v => !v)}
                    className={`w-full text-left text-xs px-2 py-1.5 rounded transition-colors ${
                      showDomainBoxes
                        ? 'bg-purple-900/40 text-purple-300 hover:bg-purple-900/60'
                        : 'bg-gray-800/60 text-gray-500 hover:bg-gray-800'
                    }`}
                  >
                    {showDomainBoxes ? t('graphPage.domainBoxOn') : t('graphPage.domainBoxOff')}
                  </button>
                )}
                {/* 배경이미지 토글 — 배경사진 설정한 경우만 표시 */}
                {bgUrl && (
                  <button
                    onClick={toggleBg}
                    className={`w-full text-left text-xs px-2 py-1.5 rounded transition-colors ${
                      bgEnabled
                        ? 'bg-blue-900/40 text-blue-300 hover:bg-blue-900/60'
                        : 'bg-gray-800/60 text-gray-500 hover:bg-gray-800'
                    }`}
                  >
                    {bgEnabled ? t('graphViewer.bgOn') : t('graphViewer.bgOff')}
                  </button>
                )}
              </LeftSection>
            )}

            {/* 스케치 (슈퍼 바이브 코딩 P0, 베타) */}
            <LeftSection title={t('graphPage.sketchSectionTitle')}>
              <button
                onClick={() => setSketchMode(v => !v)}
                className={`w-full text-left text-xs px-2 py-1.5 rounded transition-colors ${
                  sketchMode ? 'bg-purple-900/50 text-purple-200 hover:bg-purple-900/70' : 'bg-gray-800/60 text-gray-300 hover:bg-gray-800'
                }`}
              >
                {sketchMode ? t('graphPage.sketchModeOn') : t('graphPage.sketchModeOff')}
              </button>
              {sketchMode && (
                <div className="mt-1 flex flex-col gap-1">
                  <button
                    onClick={addSketchNode}
                    className="w-full text-left text-xs px-2 py-1.5 rounded bg-purple-700 hover:bg-purple-600 text-white"
                  >
                    {t('graphPage.addSketchNodeButton')}
                  </button>
                  <p className="text-[11px] text-gray-500 leading-relaxed px-1">
                    {t('graphPage.sketchDesc')}
                    {sketchNodes.length > 0 && <span className="text-purple-400">{t('graphPage.sketchCount', { count: sketchNodes.length })}</span>}
                  </p>
                </div>
              )}
            </LeftSection>

            {/* 버전 기록 */}
            <LeftSection title={t('graphPage.versionHistoryTitle')} headerRight={
              <button
                onClick={() => setShowRetentionInfo(v => !v)}
                title={t('graphPage.retentionPolicyTitle')}
                className={`w-4 h-4 rounded-full border text-[10px] leading-none flex items-center justify-center ${showRetentionInfo ? 'border-blue-500 text-blue-400' : 'border-gray-600 text-gray-500 hover:text-gray-300'}`}
              >
                ?
              </button>
            }>
              {showRetentionInfo && (
                <div className="text-[11px] text-gray-400 bg-gray-800/60 rounded p-2 mb-1.5 leading-relaxed">
                  {t('graphPage.retentionInfoText', { count: 10, maxPin: 5 })}
                </div>
              )}
              {currentVersion && (
                <div className="text-[11px] px-1 pb-1 leading-tight">
                  <span className="text-gray-500">{t('graphPage.currentVersionLabel')}</span><br />
                  <span className="text-blue-400">{currentVersion.branch}</span>{' '}
                  <span className="text-gray-500">
                    {new Date(currentVersion.createdAt).toLocaleString(currentDateLocale(), { year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                  </span>
                  {currentVersion.pinnedSlot && <span className="text-amber-400">{t('graphPage.pinnedBadge', { slot: currentVersion.pinnedSlot })}</span>}
                </div>
              )}
              {/* 고정 슬롯 표시 — 채워진 슬롯 클릭 시 해당 버전 로드 */}
              <div className="px-1 pb-1.5">
                <span className="text-[10px] text-gray-500">{t('graphPage.pinnedSlotsLabel')}</span>
                <div className="flex gap-1 mt-0.5">
                  {[1, 2, 3, 4, 5].map((slot) => {
                    const g = versions.find((x) => x.pinnedSlot === slot)
                    return (
                      <button
                        key={slot}
                        disabled={!g}
                        onClick={() => g && handleLoadVersion(g.graphId)}
                        title={g ? t('graphPage.slotFilledTitle', { slot, branch: g.branch, date: new Date(g.createdAt).toLocaleDateString(currentDateLocale()) }) : t('graphPage.slotEmptyTitle', { slot })}
                        className={`w-6 h-6 text-[10px] rounded border flex items-center justify-center transition-colors ${
                          g ? 'bg-amber-900/40 border-amber-700 text-amber-300 hover:bg-amber-900/70' : 'border-gray-700 text-gray-600'
                        }`}
                      >
                        {slot}
                      </button>
                    )
                  })}
                </div>
              </div>
              <button
                onClick={handleLoadVersions}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300"
              >
                {showVersions ? t('graphPage.toggleVersionsClose') : t('graphPage.toggleVersionsOpen')}
              </button>
              {showVersions && (
                <div className="mt-1 flex flex-col gap-1 max-h-60 overflow-y-auto">
                  {loadingVersions ? (
                    <p className="text-xs text-gray-500 px-1">{t('graphPage.loadingVersions')}</p>
                  ) : versions.length === 0 ? (
                    <p className="text-xs text-gray-500 px-1">{t('graphPage.noVersions')}</p>
                  ) : (
                    versions.map((v, i) => (
                      <div key={v.graphId} className="flex flex-col">
                        <div className="flex items-stretch gap-1">
                          <button
                            onClick={() => handleLoadVersion(v.graphId)}
                            className={`flex-1 text-left text-xs px-2 py-1.5 rounded hover:bg-gray-700 transition-colors ${
                              v.graphId === graphId ? 'bg-gray-700 text-white' : 'bg-gray-800/40 text-gray-400'
                            }`}
                          >
                            <span className="text-gray-300">{i === 0 ? t('graphPage.latestPrefix') : ''}</span>
                            <span className="text-blue-400">{v.branch}</span>
                            {v.pinnedSlot && <span className="text-amber-400"> 📌{v.pinnedSlot}</span>}
                            <br />
                            <span className="text-gray-500">
                              {new Date(v.createdAt).toLocaleString(currentDateLocale(), { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                            </span>
                          </button>
                          <button
                            onClick={() => v.pinnedSlot ? handleUnpin(v.graphId) : setPinMenuGraphId(pinMenuGraphId === v.graphId ? null : v.graphId)}
                            title={v.pinnedSlot ? t('graphPage.unpinTitle', { slot: v.pinnedSlot }) : t('graphPage.pinTitle')}
                            className={`px-2 rounded text-sm flex-shrink-0 transition-colors ${
                              v.pinnedSlot ? 'bg-amber-900/50 text-amber-300 hover:bg-amber-900/80' : 'bg-gray-800/60 text-gray-500 hover:text-amber-300'
                            }`}
                          >
                            📌
                          </button>
                        </div>
                        {pinMenuGraphId === v.graphId && !v.pinnedSlot && (
                          <div className="mt-1 ml-1 p-2 bg-gray-800 rounded flex flex-col gap-1">
                            <span className="text-[10px] text-gray-500">{t('graphPage.pinMenuHint')}</span>
                            {[1, 2, 3, 4, 5].map((slot) => {
                              const occupant = versions.find((x) => x.pinnedSlot === slot)
                              return (
                                <button
                                  key={slot}
                                  onClick={() => handlePin(v.graphId, slot)}
                                  className="text-left text-[11px] px-2 py-1 rounded bg-gray-900/60 hover:bg-gray-700 text-gray-300"
                                >
                                  <span className="text-amber-400 mr-1">{t('graphPage.pinMenuSlotLabel', { slot })}</span>
                                  {occupant
                                    ? <span className="text-gray-500">{t('graphPage.pinMenuOccupant', { branch: occupant.branch, date: new Date(occupant.createdAt).toLocaleDateString(currentDateLocale()) })}</span>
                                    : <span className="text-gray-600">{t('graphPage.pinMenuEmpty')}</span>}
                                </button>
                              )
                            })}
                          </div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              )}
            </LeftSection>

            {/* 버전 비교 */}
            <LeftSection title={t('graphPage.versionDiffTitle')}>
              <button
                onClick={() => navigate(`/projects/${projectId}/diff`)}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300"
              >
                {t('graphPage.versionDiffButton')}
              </button>
            </LeftSection>

            {/* 노드 타입 가시성 필터 — memoized */}
            {nodeFilterSectionJsx}

            {/* 엣지 — 색인 + 토글 통합 — memoized */}
            {edgeToggleSectionJsx}

            {/* 범례 — 계층형/도메인 레이어 + 노드 */}
            <LeftSection title={t('graphPage.legendTitle')}>
              {layoutPreset === 'domain' && (
                <>
                  <GraphLegend
                    headerText={t('graphPage.legendDomainHeading')}
                    entries={availableTabs.map((key) => ({ key, label: key.charAt(0).toUpperCase() + key.slice(1), color: domainColorMap.get(key)?.color ?? '#6b7280' }))}
                    opaqueSet={opaqueDomainSet}
                    onToggleOpaque={toggleDomainOpaque}
                    isActive={(entry) => activeDomainTab === entry.key}
                    onLabelClick={(entry) => activateDomain(entry.key)}
                    labelTitle={t('graphPage.domainOnlyFlowsTitle')}
                    onReset={resetDomainTab}
                    resetActive={activeDomainTab === null}
                  />
                  {/* DB 테이블 범례 */}
                  <div className="flex items-center gap-1.5 py-0.5 mb-2">
                    <span style={{ width: 16, height: 16, borderRadius: 3, border: '1px solid #22d3ee88', background: '#22d3ee22', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 9, color: '#22d3ee' }}>◇</span>
                    <span
                      className="text-gray-400 text-xs truncate cursor-pointer hover:text-white transition-colors flex-1 min-w-0"
                      onClick={() => {
                        const dbFlows = rawNodes.filter(n => n.type === 'DB_TABLE').map(n => ({ id: n.id, label: n.comment || n.name, preview: [n.comment || n.name] }))
                        setSidebar({ kind: 'domain-summary', domainName: t('graphPage.dbTableDomainName'), color: '#22d3ee', flows: dbFlows })
                        setRightCollapsed(false)
                      }}
                      title={t('graphPage.dbTableListTitle')}
                    >DB</span>
                  </div>
                  <div className="border-t border-gray-800 my-2" />
                </>
              )}
              {layoutPreset === 'layer' && (
                <>
                  <GraphLegend
                    headerText={t('graphPage.legendLayerHeading')}
                    entries={layerSections}
                    opaqueSet={opaqueLayerSet}
                    onToggleOpaque={toggleLayerOpaque}
                    isActive={(entry) => activeDomainTab === entry.key}
                    onLabelClick={(entry) => activateDomain(entry.key)}
                    labelTitle={t('graphViewer.showOnlyLayer')}
                    onReset={resetDomainTab}
                    resetActive={activeDomainTab === null}
                  />
                  <div className="border-t border-gray-800 my-2" />
                </>
              )}
            </LeftSection>

          </div>

          {/* 왼쪽 사이드바 리사이즈 핸들 */}
          <div
            onMouseDown={startLeftResize}
            className="absolute top-0 right-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />
        </aside>
      )}

      <ReactFlow
        nodes={displayNodes}
        edges={displayEdges}
        nodeTypes={nodeTypes}
        onNodesChange={handleNodesChange}
        onEdgesChange={onEdgesChange}
        onEdgeMouseEnter={handleEdgeMouseEnter}
        onEdgeMouseLeave={handleEdgeMouseLeave}
        onEdgeClick={handleEdgeClick}
        onNodeClick={handleNodeClick}
        onNodeDragStop={handleNodeDragStop}
        onMouseMove={collabSessionId ? handleCollabMouseMove : undefined}
        fitView
        fitViewOptions={{ padding: 0.1 }}
        minZoom={GRAPH_MIN_ZOOM}
        maxZoom={GRAPH_MAX_ZOOM}
        ariaLabelConfig={GRAPH_ARIA_LABELS}
        onlyRenderVisibleElements
      >
        <Background color="#1f2937" gap={20} />
        <Controls />
        <MiniMap
          nodeColor={minimapNodeColor}
          style={{ background: '#111827' }}
          position="bottom-center"
        />
      </ReactFlow>

      {/* 빈 그래프 안내 overlay — 노드가 0개일 때 */}
      {counts.files === 0 && !loading && (
        <div className="absolute inset-0 flex flex-col items-center justify-center z-20 pointer-events-none">
          <div className="bg-gray-900 border border-gray-700 rounded-xl px-8 py-6 flex flex-col items-center gap-3 max-w-sm text-center pointer-events-auto">
            <div className="text-3xl">🔍</div>
            <p className="text-gray-200 font-medium">{t('graphPage.emptyGraphTitle')}</p>
            <p className="text-gray-500 text-sm">{t('graphPage.emptyGraphDesc')}</p>
            <ul className="text-left text-xs text-gray-500 space-y-1 mt-1">
              <li>• {t('graphPage.emptyGraphTip1')}</li>
              <li>• {t('graphPage.emptyGraphTip2')}</li>
              <li>• {t('graphPage.emptyGraphTip3')}</li>
            </ul>
            <div className="flex gap-2 mt-2">
              <button
                onClick={() => navigate('/mypage')}
                className="text-sm bg-indigo-700 hover:bg-indigo-600 text-white px-4 py-1.5 rounded-lg"
              >{t('graphPage.reanalyzeFromMyPageButton')}</button>
            </div>
          </div>
        </div>
      )}

      {/* 코너 플로팅 — 아키텍처 의도 (좌측 하단, 기본 접힘) */}
      {projectId && (
        <CornerPanel
          open={archPanelOpen}
          onOpen={() => setArchPanelOpen(true)}
          onClose={() => setArchPanelOpen(false)}
          icon="🏛"
          title={t('graphPage.archIntentPanelTitle')}
          panelClassName="w-72 max-h-[55vh]"
          style={{ left: leftOpen ? `${leftWidth + 16}px` : '20px' }}
        >
          <ArchitectureIntentPanel
            projectId={projectId}
            filePaths={rawNodes.filter(n => n.type === 'FILE').map(n => n.filePath)}
            onSaved={() => fetchGraph()}
          />
        </CornerPanel>
      )}

      {/* 코너 플로팅 — 분석·경고 (우측 하단, 기본 접힘) */}
      <CornerPanel
        open={analysisPanelOpen}
        onOpen={() => setAnalysisPanelOpen(true)}
        onClose={() => setAnalysisPanelOpen(false)}
        icon="🔎"
        title={t('graphPage.analysisPanelTitle')}
        count={warnings.length}
        panelClassName="w-80 max-h-[60vh]"
        style={{ right: rightCollapsed ? '16px' : `${rightWidth + 16}px` }}
        headerExtra={warnings.length > 0 ? (
          <button onClick={() => downloadWarningsMd(warnings)} title={t('graphViewer.exportWarningsTooltip')} className="text-gray-500 hover:text-gray-300 text-[10px] px-1.5 py-0.5 rounded hover:bg-gray-800">↓ MD</button>
        ) : undefined}
      >
        {(warnings.length > 0 || suppressedWarnings.length > 0) ? (
          <WarningPanel
            warnings={warnings}
            onNodeNavigate={handleSearchNodeClick}
            onSuppress={handleSuppressWarning}
            suppressed={suppressedWarnings}
            onRestore={handleRestoreWarning}
            onReportFp={handleReportFp}
            reportedFingerprints={reportedFingerprints}
            ignoreOps={{
              projectId: projectId ?? '',
              fileOf: fileOfNodeId,
              rules: ignoreRules,
              allWarnings: warnings,
              onAdd: handleAddIgnore,
              onRemove: handleRemoveIgnore,
            }}
          />
        ) : (
          <p className="text-[11px] text-gray-500 px-1 pt-1">{t('graphViewer.noWarnings')}</p>
        )}
      </CornerPanel>

      {/* 분석 완료 결과 카드 — 구조 카운트 + 경고 가치 + 경고 보기 CTA */}
      {resultCard && (
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-50 w-[340px] bg-gray-900 border border-gray-700 rounded-xl shadow-2xl overflow-hidden pointer-events-auto">
          <div className="flex items-center justify-between px-4 pt-3 pb-2 border-b border-gray-800">
            <span className="text-sm font-semibold text-gray-100">{t('graphPage.resultCardTitle')}</span>
            <button onClick={() => setResultCard(null)} className="text-gray-600 hover:text-gray-300 text-sm" title={t('graphPage.closeTooltip')}>✕</button>
          </div>
          <div className="px-4 py-3 flex flex-col gap-3">
            {/* 구조 카운트 */}
            <div className="text-xs text-gray-400">
              {t('graphPage.filesLabel')} <strong className="text-gray-200">{resultCard.files}</strong> · {t('graphPage.funcsLabel')} <strong className="text-gray-200">{resultCard.funcs}</strong>
              {resultCard.db > 0 && <> · DB <strong className="text-gray-200">{resultCard.db}</strong></>}
              {resultCard.api > 0 && <> · API <strong className="text-gray-200">{resultCard.api}</strong></>}
            </div>

            {/* 경고 가치 — 핵심 */}
            {resultCard.warnTotal === 0 ? (
              <div className="text-sm text-green-300">{t('graphPage.noIssuesFound')}</div>
            ) : (
              <div className="flex flex-col gap-2">
                <div className="text-sm text-yellow-300">{t('graphPage.issuesFoundBanner', { count: resultCard.warnTotal })}</div>
                <div className="flex items-center gap-1.5 flex-wrap">
                  {resultCard.high > 0 && (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-red-900/60 text-red-300 border border-red-700/50">HIGH {resultCard.high}</span>
                  )}
                  {resultCard.medium > 0 && (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-900/60 text-amber-300 border border-amber-700/50">MEDIUM {resultCard.medium}</span>
                  )}
                  {resultCard.low > 0 && (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-800 text-gray-400 border border-gray-700">LOW {resultCard.low}</span>
                  )}
                </div>
                <button
                  onClick={() => {
                    // 경고는 우측 하단 코너 플로팅 패널에 있음 — 펼치고 토스트 닫기
                    setAnalysisPanelOpen(true)
                    setResultCard(null)
                  }}
                  className="text-sm bg-indigo-700 hover:bg-indigo-600 text-white px-3 py-1.5 rounded-lg self-start"
                >{t('graphPage.viewWarningsButton')}</button>
              </div>
            )}

            {/* 저커버리지 안내 */}
            {resultCard.lowCoverage && (
              <div className="text-[11px] text-gray-500 border-t border-gray-800 pt-2">
                {t('graphPage.lowCoverageNotice')}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 팀채팅 패널 — showTeamChat 시 우측 사이드바 왼쪽에 표시 */}
      {showTeamChat && (
        <div
          className="absolute top-0 h-full z-30"
          style={{ right: rightCollapsed ? '0px' : `${rightWidth}px`, width: '280px' }}
        >
          <TeamChatPanel
            roomId={graphId}
            onClose={() => setShowTeamChat(false)}
          />
        </div>
      )}

      {/* 우측 사이드바 — 기본 숨김, 노드/도메인/파일 클릭 시 등장 (접힘 시 영역 없이 화살표 버튼만) */}
      <aside
        className={`absolute right-0 top-0 h-full z-40 flex flex-col transition-all duration-200 ${rightCollapsed ? '' : 'bg-gray-950 border-l border-gray-800 shadow-2xl'}`}
        style={{ width: rightCollapsed ? '0px' : `${rightWidth}px` }}
      >
        {/* collapse 핸들 — 접힘 시 화면에 이것만 보임 */}
        <button
          onClick={() => setRightCollapsed((v) => !v)}
          className="absolute -left-3 top-1/2 -translate-y-1/2 bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white rounded-full w-7 h-7 flex items-center justify-center text-sm z-10 shadow-lg"
          title={rightCollapsed ? t('graphViewer.expandSidebar') : t('graphViewer.collapseSidebar')}
        >
          {rightCollapsed ? '‹' : '›'}
        </button>

        {!rightCollapsed && (
          <>
            {/* 사이드바 헤더 */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 flex-shrink-0">
              <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
                {!sidebar ? t('graphPage.sidebar.defaultTitle')
                  : sidebar.kind === 'edge' ? t('graphPage.sidebar.edgeTitle')
                  : sidebar.kind === 'file' ? t('graphPage.sidebar.fileTitle')
                  : sidebar.kind === 'func' ? t('graphPage.sidebar.funcTitle')
                  : sidebar.kind === 'func-call' ? t('graphPage.sidebar.funcCallTitle')
                  : sidebar.kind === 'db-table' ? t('graphPage.dbTableDomainName')
                  : sidebar.kind === 'db-edge' ? t('communityPostGraph.edgeTypes.db')
                  : sidebar.kind === 'api-call' ? t('communityPostGraph.edgeTypes.api')
                  : sidebar.kind === 'warning' ? t('graphPage.sidebar.warningTitle')
                  : sidebar.kind === 'domain-summary' ? t('graphPage.sidebar.domainSummaryTitle')
                  : t('graphPage.sidebar.instantiationTitle')}
              </span>
              {sidebar && <button onClick={() => setSidebar(null)} className="text-gray-600 hover:text-white text-sm">✕</button>}
            </div>

              <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">

                <FlowPlaybackPanel
                  callTree={callTree}
                  playbackItems={playbackItems}
                  playbackCursor={playbackCursor}
                  playbackPlaying={playbackPlaying}
                  activePath={activePath}
                  pendingBranchNodeId={pendingBranchNodeId}
                  playbackRootNodeId={playbackRootNodeId}
                  rawNodes={rawNodes}
                  setPlaybackCursor={setPlaybackCursor}
                  setPlaybackPlaying={setPlaybackPlaying}
                  setPendingBranchNodeId={setPendingBranchNodeId}
                  resetPlayback={resetPlayback}
                  selectBranchImmediate={selectBranchImmediate}
                  confirmBranch={confirmBranch}
                  startPlayback={startPlayback}
                />

                {/* ── 기본 상태 — 아무것도 선택되지 않은 경우 ── */}
                {!sidebar && (
                  <div className="flex flex-col items-center justify-center h-full gap-3 text-center pb-10">
                    <span className="text-3xl opacity-20">↗</span>
                    <p className="text-gray-600 text-xs leading-relaxed">
                      {t('graphPage.sidebar.emptyStateLine1')}<br />{t('graphPage.sidebar.emptyStateLine2')}
                    </p>
                  </div>
                )}

                {sidebar && (<>

                {/* ── FUNCTION_CALL 엣지 클릭 ── */}
                {sidebar.kind === 'func-call' && (
                  <div className="flex flex-col gap-3">
                    <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.callerFuncLabel')}</p>
                        <p className="text-emerald-400 font-mono text-sm font-semibold cursor-pointer hover:text-emerald-200"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.callerNodeId }], duration: 500, padding: 0.4 }), 50) }}
                        >{sidebar.callerComment ?? sidebar.callerName}</p>
                        <p className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-300 mt-0.5"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.callerFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.callerFile}</p>
                      </div>
                      <div className="text-amber-500 text-sm text-center">↓</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.calleeFuncLabel')}</p>
                        <p className="text-emerald-400 font-mono text-sm font-semibold cursor-pointer hover:text-emerald-200"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.calleeNodeId }], duration: 500, padding: 0.4 }), 50) }}
                        >{sidebar.calleeComment ?? sidebar.calleeName}</p>
                        <p className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-300 mt-0.5"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.calleeFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.calleeFile}</p>
                      </div>
                    </div>
                    <span className="text-xs bg-amber-900/40 text-amber-400 px-2 py-0.5 rounded self-start">FUNCTION_CALL</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#f59e0b"
                      onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                    />
                  </div>
                )}

                {/* ── INSTANTIATION 엣지 클릭 ── */}
                {sidebar.kind === 'instantiation' && (
                  <div className="flex flex-col gap-3">
                    <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.creatingFileLabel')}</p>
                        <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.sourceNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.sourceFile}</p>
                      </div>
                      <div className="text-purple-400 text-sm text-center">↓ new</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.instantiationTargetLabel')}</p>
                        <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.targetNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.targetClass}</p>
                      </div>
                    </div>
                    <span className="text-xs bg-purple-900/40 text-purple-400 px-2 py-0.5 rounded self-start">INSTANTIATION</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#a855f7"
                      onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                  </div>
                )}

                {/* ── 엣지 클릭: 파일→파일 + 콜 체인 ── */}
                {sidebar.kind === 'edge' && (
                  <>
                    <div className="bg-gray-800/60 rounded-lg p-3 flex items-center gap-2">
                      <span
                        className="text-blue-300 font-mono text-xs cursor-pointer hover:text-white truncate flex-1"
                        onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.sourceNodeId }], duration: 500, padding: 0.3 }), 50) }}
                      >{sidebar.sourceId}</span>
                      <span className="text-gray-600 text-xs flex-shrink-0">→</span>
                      <span
                        className="text-blue-300 font-mono text-xs cursor-pointer hover:text-white truncate flex-1 text-right"
                        onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.targetNodeId }], duration: 500, padding: 0.3 }), 50) }}
                      >{sidebar.targetId}</span>
                    </div>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#4b5563"
                      onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                    <SidebarSection title={`${t('graphPage.sidebar.callChainTitle')}${sidebar.callChain.length > 0 ? ` (${sidebar.callChain.length})` : ''}`}>
                      {sidebar.callChain.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noCallsFound')}</p>
                        : sidebar.callChain.map((e, i) => (
                          <CallChainRow key={i}
                            leftLabel={e.callerLabel} leftNodeId={e.callerNodeId}
                            rightLabel={e.calleeLabel} rightNodeId={e.calleeNodeId}
                            onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
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
                    <SidebarSection title={t('graphPage.sidebar.customNameMemoTitle')}>
                      <input
                        value={annotationLabel}
                        onChange={e => setAnnotationLabel(e.target.value)}
                        placeholder={t('graphPage.sidebar.displayNamePlaceholder')}
                        className="w-full bg-gray-800 border border-gray-700 rounded px-2 py-1 text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500"
                      />
                      <textarea
                        value={annotationNote}
                        onChange={e => setAnnotationNote(e.target.value)}
                        placeholder={t('graphPage.sidebar.memoPlaceholder')}
                        rows={3}
                        className="w-full bg-gray-800 border border-gray-700 rounded px-2 py-1 text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500 mt-1 resize-none"
                      />
                      <button
                        onClick={handleSaveAnnotation}
                        disabled={annotationSaving}
                        className="mt-1 w-full text-xs bg-blue-600 hover:bg-blue-500 text-white rounded px-2 py-1 disabled:opacity-40"
                      >
                        {annotationSaving ? t('graphPage.sidebar.saving') : t('graphPage.sidebar.saveButton')}
                      </button>
                    </SidebarSection>
                    <SidebarSection title={`${t('graphPage.sidebar.dependsOnTitle')}${sidebar.data.outgoing.length > 0 ? ` (${sidebar.data.outgoing.length})` : ''}`}>
                      {sidebar.data.outgoing.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noneFound')}</p>
                        : sidebar.data.outgoing.map((c, i) => (
                          <FileConnGroup key={i} entry={c} direction="out"
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>
                    <SidebarSection title={`${t('graphPage.sidebar.importedByTitle')}${sidebar.data.incoming.length > 0 ? ` (${sidebar.data.incoming.length})` : ''}`}>
                      {sidebar.data.incoming.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noneFound')}</p>
                        : sidebar.data.incoming.map((c, i) => (
                          <FileConnGroup key={i} entry={c} direction="in"
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
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
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-white font-mono font-semibold text-sm">
                            {labelMode === 'comment' && sidebar.funcComment ? sidebar.funcComment : sidebar.funcName}
                          </p>
                          <p className="text-gray-500 font-mono text-xs mt-0.5">
                            {labelMode === 'comment' && sidebar.funcComment ? sidebar.funcName : sidebar.funcComment}
                          </p>
                          <p
                            className="text-blue-400 font-mono text-xs mt-1 cursor-pointer hover:text-blue-300 underline decoration-gray-700"
                            onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.parentFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                          >{sidebar.parentFileName}</p>
                        </div>
                        <button
                          onClick={() => {
                            const url = new URL(window.location.href)
                            url.searchParams.set('nodeId', sidebar.nodeId)
                            url.searchParams.delete('fresh')
                            navigator.clipboard.writeText(url.toString())
                          }}
                          className="shrink-0 text-xs text-gray-500 hover:text-gray-300 border border-gray-700 hover:border-gray-500 rounded px-2 py-0.5 transition-colors"
                          title={t('graphPage.sidebar.copyNodeLinkTitle')}
                        >
                          🔗
                        </button>
                      </div>
                    </div>
                    <SidebarSection title={t('graphPage.sidebar.customNameMemoTitle')}>
                      <input
                        value={annotationLabel}
                        onChange={e => setAnnotationLabel(e.target.value)}
                        placeholder={t('graphPage.sidebar.displayNamePlaceholder')}
                        className="w-full bg-gray-800 border border-gray-700 rounded px-2 py-1 text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500"
                      />
                      <textarea
                        value={annotationNote}
                        onChange={e => setAnnotationNote(e.target.value)}
                        placeholder={t('graphPage.sidebar.memoPlaceholder')}
                        rows={3}
                        className="w-full bg-gray-800 border border-gray-700 rounded px-2 py-1 text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500 mt-1 resize-none"
                      />
                      <button
                        onClick={handleSaveAnnotation}
                        disabled={annotationSaving}
                        className="mt-1 w-full text-xs bg-blue-600 hover:bg-blue-500 text-white rounded px-2 py-1 disabled:opacity-40"
                      >
                        {annotationSaving ? t('graphPage.sidebar.saving') : t('graphPage.sidebar.saveButton')}
                      </button>
                    </SidebarSection>
                    <SidebarSection title={`${t('graphPage.sidebar.callerFuncLabel')}${sidebar.callers.length > 0 ? ` (${sidebar.callers.length})` : ''}`}>
                      {sidebar.callers.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noneFound')}</p>
                        : sidebar.callers.map((c, i) => (
                          <FuncChainRow key={i} entry={c} direction="caller" labelMode={labelMode}
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                            onNodeClick={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>
                    <SidebarSection title={`${t('graphPage.sidebar.calleeFuncLabel')}${sidebar.callees.length > 0 ? ` (${sidebar.callees.length})` : ''}`}>
                      {sidebar.callees.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noneFound')}</p>
                        : sidebar.callees.map((c, i) => (
                          <FuncChainRow key={i} entry={c} direction="callee" labelMode={labelMode}
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                            onNodeClick={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>

                    {/* 노드 코멘트 */}
                    <SidebarSection title={`${t('graphPage.sidebar.commentsTitle')}${nodeComments.length > 0 ? ` (${nodeComments.length})` : ''}`}>
                      <div className="flex flex-col gap-2">
                        {nodeComments.map((c) => (
                          <div key={c.id} className="bg-gray-800 rounded-lg px-3 py-2 flex flex-col gap-1">
                            <p className="text-gray-200 text-xs leading-relaxed">{c.content}</p>
                            <div className="flex items-center justify-between">
                              <span className="text-gray-600 text-[10px]">{new Date(c.createdAt).toLocaleDateString(currentDateLocale())}</span>
                              {currentUserId === c.userId && (
                                <button
                                  onClick={() => {
                                    if (!graphId || !commentNodeId) return
                                    axios.delete(`/api/graphs/${graphId}/nodes/${commentNodeId}/comments/${c.id}`)
                                      .then(() => setNodeComments((prev) => prev.filter((x) => x.id !== c.id)))
                                      .catch(() => {})
                                  }}
                                  className="text-gray-600 hover:text-red-400 text-[10px]"
                                >{t('graphPage.sidebar.deleteButton')}</button>
                              )}
                            </div>
                          </div>
                        ))}
                        {nodeComments.length === 0 && <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noComments')}</p>}
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
                                  { content: commentInput.trim() }
                                ).then((res) => {
                                  setNodeComments((prev) => [...prev, res.data])
                                  setCommentInput('')
                                }).catch(() => {})
                              }
                            }}
                            placeholder={t('graphPage.sidebar.commentPlaceholder')}
                            className="flex-1 bg-gray-800 text-white text-xs px-2 py-1.5 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500"
                          />
                        </div>
                      </div>
                    </SidebarSection>

                    {/* 노드 색상 */}
                    {clickedNodeId && (
                      <SidebarSection title={t('graphPage.sidebar.nodeColorTitle')}>
                        <div className="flex flex-wrap gap-1.5">
                          {[
                            { color: '', label: t('graphPage.sidebar.colorNone') },
                            { color: '#ef4444', label: t('graphPage.sidebar.colorRed') },
                            { color: '#f97316', label: t('graphPage.sidebar.colorOrange') },
                            { color: '#eab308', label: t('graphPage.sidebar.colorYellow') },
                            { color: '#22c55e', label: t('graphPage.sidebar.colorGreen') },
                            { color: '#3b82f6', label: t('graphPage.sidebar.colorBlue') },
                            { color: '#a855f7', label: t('graphPage.sidebar.colorPurple') },
                            { color: '#ec4899', label: t('graphPage.sidebar.colorPink') },
                            { color: '#6b7280', label: t('graphPage.sidebar.colorGray') },
                          ].map(({ color, label }) => (
                            <button
                              key={label}
                              title={label}
                              onClick={() => handleNodeColorChange(clickedNodeId, color)}
                              style={{
                                width: 22, height: 22, borderRadius: 4,
                                background: color || '#1f2937',
                                border: nodeColorMap[clickedNodeId] === color
                                  ? '2px solid #fff'
                                  : '2px solid #374151',
                                cursor: 'pointer',
                              }}
                            />
                          ))}
                        </div>
                      </SidebarSection>
                    )}
                  </>
                )}

                {/* ── DB_TABLE 노드 클릭 ── */}
                {sidebar.kind === 'db-table' && (
                  <div className="flex flex-col gap-3">
                    <div>
                      <p className="text-white font-mono font-semibold text-sm">{sidebar.tableName}</p>
                      <p className="text-gray-500 text-xs mt-0.5">{t('graphPage.dbTableDomainName')}</p>
                    </div>

                    {/* 연결된 Repository */}
                    <SidebarSection title={t('graphPage.sidebar.repositoryTitle', { count: sidebar.repos.length })}>
                      {sidebar.repos.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noRepoConnection')}</p>
                        : sidebar.repos.map((r, i) => (
                          <div key={i} className="flex items-center gap-2">
                            <span
                              className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-200 flex-1 truncate"
                              onClick={() => { setTimeout(() => fitView({ nodes: [{ id: r.id }], duration: 500, padding: 0.3 }), 50) }}
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
                    <SidebarSection title={`${t('graphPage.sidebar.columnsTitle')}${sidebar.columns.length > 0 ? ` (${sidebar.columns.length})` : ''}`}>
                      {sidebar.columns.length === 0
                        ? <p className="text-gray-700 text-xs">{t('graphPage.sidebar.noColumnInfo')}</p>
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
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.repoFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.repoFile}</p>
                      </div>
                      <div className="text-center text-sm" style={{ color: DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee' }}>
                        ↓ {sidebar.crudType.replace('DB_', '')}
                      </div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.dbTableDomainName')}</p>
                        <p className="text-cyan-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.tableNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.tableName}</p>
                      </div>
                    </div>
                    <span
                      className="text-xs px-2 py-0.5 rounded self-start font-semibold"
                      style={{ background: (DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee') + '22', color: DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee' }}
                    >{sidebar.crudType}</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor={DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee'}
                      onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                  </div>
                )}

                {/* ── API 호출 엣지 상세 ── */}
                {sidebar?.kind === 'api-call' && (
                  <div className="flex flex-col gap-3">
                    <div className="flex gap-4 items-start">
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.frontFileLabel')}</p>
                        <p className="text-fuchsia-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.frontFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.frontFile}</p>
                      </div>
                      <div className="text-gray-500 mt-5">→</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">{t('graphPage.sidebar.controllerLabel')}</p>
                        <p className="text-fuchsia-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.ctrlFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.ctrlFile}</p>
                      </div>
                    </div>
                    <span className="text-xs px-2 py-0.5 rounded self-start font-semibold"
                      style={{ background: '#e879f922', color: '#e879f9' }}>API_CALL</span>
                    <FlowChainSection steps={sidebar.flowChain} edgeColor="#e879f9"
                      onNav={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                    />
                  </div>
                )}

                {/* ── 경고 노드 클릭 — 해당 노드의 경고 상세 표시 ── */}
                {sidebar?.kind === 'warning' && (
                  <div className="flex flex-col gap-3">
                    <div className="flex items-center gap-2">
                      <span className="text-yellow-400 font-semibold text-sm">{sidebar.nodeName}</span>
                      <span className="text-xs text-gray-500">{t('graphPage.sidebar.warningCountLabel', { count: sidebar.nodeWarnings.length })}</span>
                    </div>
                    {sidebar.nodeWarnings.map((w, i) => {
                      const WARNING_COLORS: Record<string, string> = {
                        CYCLIC_IMPORT: '#f97316', BROKEN_INTERFACE_CHAIN: '#ef4444',
                        ASYNC_SELF_CALL: '#eab308', DB_LAYER_BYPASS: '#8b5cf6',
                        CROSS_CONTEXT_IMPORT: '#06b6d4', CROSS_FEATURE_IMPORT: '#0ea5e9', FEATURE_LAYER_VIOLATION: '#6366f1', MISSING_CONVERTER_MIGRATION: '#ec4899',
                      }
                      const color = WARNING_COLORS[w.type] ?? '#eab308'
                      return (
                        <div key={i} className="rounded-lg p-3 flex flex-col gap-1.5" style={{ background: color + '12', border: `1px solid ${color}40` }}>
                          <span className="text-xs font-semibold" style={{ color }}>{w.type.replace(/_/g, ' ')}</span>
                          <span className="text-xs text-gray-300 leading-snug">{w.message}</span>
                        </div>
                      )
                    })}
                  </div>
                )}

                {/* ── 도메인 섹션 클릭 — 흐름 재생 목록 ── */}
                {sidebar?.kind === 'domain-summary' && (
                  <div className="flex flex-col gap-3">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold uppercase tracking-widest" style={{ color: sidebar.color }}>
                        {sidebar.domainName}
                      </span>
                      <span className="text-xs text-gray-500">{sidebar.domainName === t('graphPage.dbTableDomainName') ? t('graphPage.sidebar.domainSummaryTypeTable') : t('graphPage.sidebar.domainSummaryTypeDomain')}</span>
                    </div>
                    <p className="text-[10px] text-gray-500 uppercase tracking-wider">{t('graphPage.sidebar.flowPlaybackListTitle')}</p>
                    {sidebar.flows.length > 0 ? sidebar.flows.map(flow => (
                      <button key={flow.id}
                        className="flex flex-col gap-1 rounded-lg px-2.5 py-2.5 text-left transition-opacity hover:opacity-80"
                        style={{ background: sidebar.color + '15', border: `1px solid ${sidebar.color}40` }}
                        onClick={() => {
                          startPlayback(flow.id)
                          setTimeout(() => fitView({ nodes: [{ id: flow.id }], duration: 500, padding: 0.3 }), 50)
                        }}
                      >
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px]" style={{ color: sidebar.color }}>▶</span>
                          <span className="text-xs font-semibold truncate" style={{ color: sidebar.color }}>{flow.label}</span>
                        </div>
                        {flow.preview.length > 1 && (
                          <span className="text-[10px] text-gray-500 leading-snug">
                            {flow.preview.join(' → ')}
                            {flow.preview.length >= 4 ? ' ...' : ''}
                          </span>
                        )}
                      </button>
                    )) : (
                      <p className="text-xs text-gray-500">{t('graphPage.sidebar.noFlowsFound')}</p>
                    )}
                  </div>
                )}

                </>)}

              </div>
          </>
        )}

        {/* 오른쪽 사이드바 리사이즈 핸들 — collapse 아닐 때만 */}
        {!rightCollapsed && (
          <div
            onMouseDown={startRightResize}
            className="absolute top-0 left-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />
        )}
      </aside>

      {/* 뷰 프리셋 저장 모달 */}
      {showSavePresetModal && pendingSaveSlot !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70">
          <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 w-80 flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-sm">슬롯 {pendingSaveSlot}에 저장</h2>
              <button
                onClick={() => { setShowSavePresetModal(false); setPendingSaveSlot(null); setPresetSaveName('') }}
                className="text-gray-500 hover:text-white text-lg leading-none"
              >✕</button>
            </div>
            <p className="text-xs text-gray-400">현재 레이아웃, 라벨, 엣지 설정을 이 슬롯에 저장합니다.</p>
            <input
              value={presetSaveName}
              onChange={(e) => setPresetSaveName(e.target.value)}
              placeholder="프리셋 이름 (최대 30자)"
              maxLength={30}
              autoFocus
              className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
              onKeyDown={(e) => { if (e.key === 'Enter' && !presetSaving && presetSaveName.trim()) handleSavePreset() }}
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowSavePresetModal(false); setPendingSaveSlot(null); setPresetSaveName('') }}
                className="text-sm text-gray-500 hover:text-white px-3 py-1.5"
              >취소</button>
              <button
                onClick={handleSavePreset}
                disabled={presetSaving || !presetSaveName.trim()}
                className="text-sm bg-white text-black font-medium px-4 py-1.5 rounded-lg hover:bg-gray-200 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {presetSaving ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}

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

            {/* 공유할 뷰(프리셋) 선택 — 등록 시점의 설정을 스냅샷으로 고정, 이후 프리셋을 바꿔도 게시글엔 영향 없음 */}
            <div>
              <p className="text-xs text-gray-400 mb-2 font-medium">공유할 뷰</p>
              <select
                value={sharePresetSlot}
                onChange={(e) => setSharePresetSlot(Number(e.target.value))}
                className="w-full bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
              >
                {presets.map((p) => (
                  <option key={p.slot} value={p.slot}>{p.name}</option>
                ))}
              </select>
              <p className="text-[11px] text-gray-500 mt-1.5">
                선택한 프리셋이 이 시점의 그래프 전체를 담은 스냅샷으로 첨부됩니다. 보는 사람은 확대·검색·흐름 재생까지 자유롭고, 수정만 할 수 없습니다.
              </p>
            </div>

            {/* 공개범위 */}
            <div>
              <p className="text-xs text-gray-400 mb-2 font-medium">공개범위</p>
              <div className="flex gap-2">
                <button
                  onClick={() => setShareVisibility('PUBLIC')}
                  className={`flex-1 text-xs px-3 py-2 rounded-lg border transition-colors ${
                    shareVisibility === 'PUBLIC'
                      ? 'bg-blue-900/40 border-blue-600 text-blue-300'
                      : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                  }`}
                >
                  공개 — 커뮤니티 피드에 표시
                </button>
                <button
                  onClick={() => setShareVisibility('PRIVATE')}
                  className={`flex-1 text-xs px-3 py-2 rounded-lg border transition-colors ${
                    shareVisibility === 'PRIVATE'
                      ? 'bg-blue-900/40 border-blue-600 text-blue-300'
                      : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                  }`}
                >
                  링크 공유
                </button>
              </div>
              {shareVisibility === 'PRIVATE' && (
                <p className="text-[11px] text-gray-500 mt-1.5">
                  피드에 표시되지 않으며, 링크가 있는 사람은 누구나 볼 수 있습니다.
                </p>
              )}
            </div>

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
    </div>
  )
}

// 왼쪽 사이드바 섹션
function LeftSection({ title, children, id, headerRight }: { title: string; children: React.ReactNode; id?: string; headerRight?: React.ReactNode }) {
  return (
    <div id={id} className="px-3 py-3 border-b border-gray-800/60">
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{title}</p>
        {headerRight}
      </div>
      {children}
    </div>
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
  const { t } = useTranslation('workspace')
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
        <p className="text-gray-700 text-[11px] pl-3">{t('graphPage.sidebar.noCallChainFound')}</p>
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
function FuncChainRow({ entry, direction, labelMode, onNav, onNodeClick }: {
  entry: FuncCallChainEntry
  direction: 'caller' | 'callee'
  labelMode?: 'name' | 'comment'
  onNav: (id: string) => void
  onNodeClick?: (nodeId: string) => void
}) {
  const funcLabel = labelMode === 'comment' && entry.funcComment ? entry.funcComment : entry.funcName
  return (
    <div className="flex items-center gap-2 bg-gray-900 rounded-lg px-2.5 py-2 text-xs">
      <span className="text-amber-500 flex-shrink-0">{direction === 'caller' ? '←' : '→'}</span>
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-emerald-400 font-mono cursor-pointer hover:text-emerald-200 truncate"
          onClick={() => { onNodeClick ? onNodeClick(entry.funcNodeId) : onNav(entry.funcNodeId) }}
        >{funcLabel}</span>
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
  const { t } = useTranslation('workspace')
  if (steps.length <= 2) return null  // source·target만 있으면 표시 의미 없음
  return (
    <div>
      <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-2">{t('graphPage.sidebar.fullFlowTitle')}</p>
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
                  <span className="text-[9px] text-gray-600 mt-0.5">{t('graphPage.sidebar.altRoutesCount', { count: step.altCount })}</span>
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

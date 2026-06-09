// 프로젝트 코드 구조를 React Flow로 시각화하는 그래프 페이지
import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
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
import OnboardingTour, { isTourDone } from '../components/OnboardingTour'
import AppHeader from '../components/AppHeader'
import { useCollaboration } from '../hooks/useCollaboration'
import CollaborationPanel from '../components/CollaborationPanel'
import CursorOverlay from '../components/CursorOverlay'
import WarningPanel from '../components/WarningPanel'

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
  | { kind: 'warning'; nodeName: string; nodeWarnings: { type: string; message: string }[] }

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

// ── 호출 트리 (흐름 재생용) ─────────────────────────────────────────
interface CallTreeNode {
  nodeId: string
  edgeId?: string
  edgeType?: string
  label: string
  subLabel?: string   // 함수 노드일 때 소속 파일명
  nodeType?: string
  children: CallTreeNode[]
}

// 재생 스텝 = 노드 단위 (엣지 스텝 없음). incomingEdgeType은 전환 레이블용.
interface PlaybackItem {
  id: string
  incomingEdgeId?: string
  incomingEdgeType?: string
}

const CALL_FLOW_TYPES = ['FUNCTION_CALL', 'DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE', 'API_CALL']

// 엣지 타입 → 전환 레이블
const EDGE_TYPE_LABEL: Record<string, string> = {
  FUNCTION_CALL: '호출',
  API_CALL: 'HTTP 요청',
  DB_READ: 'DB 조회',
  DB_WRITE: 'DB 저장',
  DB_CREATE: 'DB 생성',
  DB_UPDATE: 'DB 수정',
  DB_DELETE: 'DB 삭제',
}

// 트리에서 nodeId 검색
function findTreeNode(root: CallTreeNode, nodeId: string): CallTreeNode | null {
  if (root.nodeId === nodeId) return root
  for (const c of root.children) { const f = findTreeNode(c, nodeId); if (f) return f }
  return null
}

// 루트에서 targetId까지 경로 반환
function findPathInTree(root: CallTreeNode, targetId: string): { nodeIds: string[]; edgeIds: string[]; edgeTypes: string[] } | null {
  if (root.nodeId === targetId) return { nodeIds: [root.nodeId], edgeIds: [], edgeTypes: [] }
  for (const child of root.children) {
    const r = findPathInTree(child, targetId)
    if (r) return {
      nodeIds: [root.nodeId, ...r.nodeIds],
      edgeIds: [child.edgeId ?? '', ...r.edgeIds],
      edgeTypes: [child.edgeType ?? '', ...r.edgeTypes],
    }
  }
  return null
}

// startId 노드부터 첫 번째 자식을 따라 리프까지 경로 확장
function extendToDefaultLeaf(root: CallTreeNode, startId: string, nodeIds: string[], edgeIds: string[], edgeTypes: string[]) {
  let cur = findTreeNode(root, startId)
  while (cur && cur.children.length > 0) {
    const child = cur.children[0]
    nodeIds.push(child.nodeId)
    edgeIds.push(child.edgeId ?? '')
    edgeTypes.push(child.edgeType ?? '')
    cur = child
  }
}

// nodeIds/edgeIds → PlaybackItem[] 변환 — 노드만, 엣지 타입은 전환 메타로
function pathToPlaybackItems(nodeIds: string[], edgeIds: string[], edgeTypes: string[]): PlaybackItem[] {
  return nodeIds.map((id, i) => ({
    id,
    incomingEdgeId: i > 0 ? edgeIds[i - 1] : undefined,
    incomingEdgeType: i > 0 ? edgeTypes[i - 1] : undefined,
  }))
}

// CallTreeNode 생성 헬퍼
function makeCallTreeNode(nodeId: string, rawNodes: RawNode[], edgeId?: string, edgeType?: string): CallTreeNode {
  const raw = rawNodes.find((n) => n.id === nodeId)
  const label = raw?.comment ?? raw?.name ?? nodeId
  let subLabel: string | undefined
  if (raw?.type === 'FUNCTION') {
    const file = rawNodes.find((f) => f.type === 'FILE' && f.filePath === raw.filePath)
    subLabel = file?.name
  }
  return { nodeId, edgeId, edgeType, label, subLabel, nodeType: raw?.type, children: [] }
}

// 자손 트리 재귀 빌드 — visited를 공유해 중복 노드 방지
function buildDownstreamTree(nodeId: string, rawEdges: RawEdge[], rawNodes: RawNode[], visited: Set<string>, depth: number): CallTreeNode[] {
  if (depth >= 12) return []
  const result: CallTreeNode[] = []
  for (const e of rawEdges) {
    if (!CALL_FLOW_TYPES.includes(e.type) || e.source !== nodeId || visited.has(e.target)) continue
    visited.add(e.target)
    const child = makeCallTreeNode(e.target, rawNodes, e.id, e.type)
    child.children = buildDownstreamTree(e.target, rawEdges, rawNodes, visited, depth + 1)
    result.push(child)
  }
  return result
}

// 호출 트리 전체 빌드 — upstream 추적 후 전체 트리 생성 + 기본 경로 반환
function buildCallTree(
  nodeId: string, rawEdges: RawEdge[], rawNodes: RawNode[],
): { tree: CallTreeNode; defaultNodeIds: string[]; defaultEdgeIds: string[]; defaultEdgeTypes: string[] } {
  // 1. FUNCTION_CALL upstream 추적 → 최상위 호출자 탐색
  const upChain: { nodeId: string; edgeId: string; edgeType: string }[] = []
  const visitedUp = new Set([nodeId])
  let cur = nodeId
  for (let i = 0; i < 12; i++) {
    const e = rawEdges.find((e) => e.type === 'FUNCTION_CALL' && e.target === cur && !visitedUp.has(e.source))
    if (!e) break
    visitedUp.add(e.source)
    upChain.unshift({ nodeId: e.source, edgeId: e.id, edgeType: e.type })
    cur = e.source
  }
  const rootFuncId = upChain.length > 0 ? upChain[0].nodeId : nodeId

  // 2. 프론트엔드 진입점 탐색 (루트 함수 소속 파일에 API_CALL이 오면 프론트가 entry)
  const rootFuncRaw = rawNodes.find((n) => n.id === rootFuncId && n.type === 'FUNCTION')
  let frontendNodeId: string | undefined
  let frontendEdgeId: string | undefined
  if (rootFuncRaw) {
    const parentFile = rawNodes.find((f) => f.type === 'FILE' && f.filePath === rootFuncRaw.filePath)
    if (parentFile) {
      const apiEdge = rawEdges.find((e) => e.type === 'API_CALL' && e.target === parentFile.id)
      if (apiEdge) { frontendNodeId = apiEdge.source; frontendEdgeId = apiEdge.id }
    }
  }

  // 3. 루트 함수부터 전체 트리 빌드 (shared visited로 중복 방지)
  const treeVisited = new Set([rootFuncId])
  const rootNode = makeCallTreeNode(rootFuncId, rawNodes)
  rootNode.children = buildDownstreamTree(rootFuncId, rawEdges, rawNodes, treeVisited, 0)

  // 4. 기본 경로: upstream chain → nodeId → 첫 번째 자식 따라 리프까지
  const defaultNodeIds: string[] = upChain.map((u) => u.nodeId).concat([nodeId])
  const defaultEdgeIds: string[] = upChain.map((u) => u.edgeId)
  const defaultEdgeTypes: string[] = upChain.map((u) => u.edgeType)
  extendToDefaultLeaf(rootNode, nodeId, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes)

  // 5. 프론트엔드 진입점 prepend (컨트롤러 FILE 중간 노드 생략, 직접 연결)
  if (frontendNodeId) {
    const frontendNode = makeCallTreeNode(frontendNodeId, rawNodes)
    rootNode.edgeId = frontendEdgeId; rootNode.edgeType = 'API_CALL'
    frontendNode.children = [rootNode]
    return {
      tree: frontendNode,
      defaultNodeIds: [frontendNodeId, ...defaultNodeIds],
      defaultEdgeIds: [frontendEdgeId ?? '', ...defaultEdgeIds],
      defaultEdgeTypes: ['API_CALL', ...defaultEdgeTypes],
    }
  }
  return { tree: rootNode, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes }
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// 호출 트리 패널 — 분기 포함 흐름 트리 표시
function CallTreePanel({ root, activeNodeIds, onSelectBranch }: {
  root: CallTreeNode
  activeNodeIds: Set<string>
  onSelectBranch: (nodeId: string) => void
}) {
  const EDGE_LABEL: Record<string, string> = {
    API_CALL: 'API', FUNCTION_CALL: '호출',
    DB_READ: 'READ', DB_CREATE: 'CREATE', DB_UPDATE: 'UPDATE', DB_DELETE: 'DELETE', DB_WRITE: 'WRITE',
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function renderNode(node: CallTreeNode, depth: number): any {
    const isActive = activeNodeIds.has(node.nodeId)
    const hasBranches = node.children.length > 1
    return (
      <div key={node.nodeId}>
        <button
          className={`w-full text-left flex items-center gap-1 py-0.5 rounded text-[11px] transition-colors ${
            isActive ? 'text-amber-300' : 'text-gray-500 hover:text-gray-300'
          }`}
          style={{ paddingLeft: `${depth * 10 + 4}px` }}
          onClick={() => onSelectBranch(node.nodeId)}
          title={node.subLabel}
        >
          {node.edgeType && <span className="text-[9px] text-gray-600 flex-shrink-0 mr-0.5">{EDGE_LABEL[node.edgeType] ?? '→'}</span>}
          {hasBranches && <span className="text-blue-400 flex-shrink-0 text-[10px] mr-0.5">⑂</span>}
          <span className={`truncate ${isActive ? 'font-medium' : ''}`}>{node.label}</span>
        </button>
        <div>{node.children.map((child) => renderNode(child, depth + 1))}</div>
      </div>
    )
  }
  return <div className="flex flex-col max-h-52 overflow-y-auto">{renderNode(root, 0)}</div>
}

// 그래프 페이지 내부 컴포넌트 (ReactFlow 훅 사용)
function GraphPageInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

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
  const [hiddenNodeTypes, setHiddenNodeTypes] = useState<Set<string>>(new Set())
  const [nodeSearchQuery, setNodeSearchQuery] = useState('')
  const [counts, setCounts] = useState({ files: 0, funcs: 0, edges: 0 })
  const [loading, setLoading] = useState(true)
  const [tourRunning, setTourRunning] = useState(false)
  const [aiProviders, setAiProviders] = useState<{ provider: string; registered: boolean }[]>([])
  const [selectedAiProvider, setSelectedAiProvider] = useState<string>('')
  const [aiExplaining, setAiExplaining] = useState(false)
  const [aiExplanation, setAiExplanation] = useState<string | null>(null)
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
  const [opaqueDomainSet, setOpaqueDomainSet] = useState<Set<string>>(new Set())
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
  const [reanalyzing, setReanalyzing] = useState(false)
  const [showShareModal, setShowShareModal] = useState(false)
  const [shareTitle, setShareTitle] = useState('')
  const [shareContent, setShareContent] = useState('')
  const [shareFeedbackType, setShareFeedbackType] = useState('GENERAL')
  const [shareHiddenLayers, setShareHiddenLayers] = useState<Set<string>>(new Set())
  const [shareHiddenGroups, setShareHiddenGroups] = useState<Set<string>>(new Set())
  const [shareHiddenNodes, setShareHiddenNodes] = useState<Set<string>>(new Set())
  const [shareSubmitting, setShareSubmitting] = useState(false)
  const flowRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const { getNodes, fitView, screenToFlowPosition } = useReactFlow()

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

  // 흐름 재생 상태
  const [playbackItems, setPlaybackItems] = useState<PlaybackItem[]>([])
  const [playbackCursor, setPlaybackCursor] = useState(-1)
  const [playbackPlaying, setPlaybackPlaying] = useState(false)
  const [playbackSpeed, setPlaybackSpeed] = useState(600)
  const playbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [callTree, setCallTree] = useState<CallTreeNode | null>(null)
  const [activePath, setActivePath] = useState<{ nodeIds: string[]; edgeIds: string[]; edgeTypes: string[] }>({ nodeIds: [], edgeIds: [], edgeTypes: [] })
  // 현재 경로의 엣지 ID 집합 — 재생 중 visibility 관리용
  const playbackEdgeIdsRef = useRef<Set<string>>(new Set())

  // 노드 코멘트 상태
  const [nodeComments, setNodeComments] = useState<{ id: string; userId: string; content: string; createdAt: number }[]>([])
  const [commentInput, setCommentInput] = useState('')
  const [commentNodeId, setCommentNodeId] = useState<string | null>(null)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const { state: collabState, publishCursor, publishSelection } = useCollaboration(collabSessionId, currentUserId)

  // 런타임 경고 상태
  const [warnings, setWarnings] = useState<{ type: string; nodeIds: string[]; edgeIds?: string[]; message: string }[]>([])
  // publishCursorRef를 항상 최신 publishCursor로 유지
  publishCursorRef.current = publishCursor

  // 뷰 프리셋 상태
  const [presets, setPresets] = useState<{ slot: number; name: string; config: Record<string, unknown>; isDefault: boolean }[]>([])
  const [showSavePresetModal, setShowSavePresetModal] = useState(false)
  const [presetSaving, setPresetSaving] = useState(false)
  const [pendingSaveSlot, setPendingSaveSlot] = useState<number | null>(null)
  const [presetSaveName, setPresetSaveName] = useState('')

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

  // 범례 도메인 클릭 시 해당 도메인 섹션으로 fitView 이동
  const handleFitToDomain = useCallback((domain: string) => {
    const section = getNodes().find((n) => n.id === `domain-section-${domain}`)
    if (section) fitView({ nodes: [section], duration: 400, padding: 0.15 })
  }, [getNodes, fitView])

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

  // 흐름 재생 — 커서 이동 시 노드/엣지 하이라이트 적용 (노드 중심 스텝)
  useEffect(() => {
    if (playbackItems.length === 0) return

    // 커서까지 지나온 노드만 하이라이트
    const visitedNodeIds = new Set(playbackItems.slice(0, playbackCursor + 1).map((it) => it.id))
    const activeNodeId = playbackCursor >= 0 ? playbackItems[playbackCursor].id : null
    // 커서까지의 incoming 엣지 ID (현재 커서의 incoming 엣지가 "활성" 엣지)
    const visitedEdgeIds = new Set(
      playbackItems.slice(1, playbackCursor + 1).map((it) => it.incomingEdgeId).filter(Boolean) as string[]
    )
    const activeEdgeId = playbackCursor > 0 ? playbackItems[playbackCursor].incomingEdgeId : undefined

    setNodes((nds) => nds.map((n) => {
      if (!visitedNodeIds.has(n.id)) {
        return { ...n, data: { ...n.data, playbackActive: false, playbackInPath: false } }
      }
      const isActive = n.id === activeNodeId
      const baseStyle = n.style ?? {}
      const playbackStyle = isActive
        ? { ...baseStyle, outline: '2px solid #fbbf24', outlineOffset: '2px', boxShadow: '0 0 10px #fbbf2488' }
        : { ...baseStyle, outline: '1px solid #22d3ee66', outlineOffset: '2px' }
      return { ...n, style: playbackStyle, data: { ...n.data, playbackActive: isActive, playbackInPath: !isActive } }
    }))

    // 경로 엣지 — hidden 해제 + 방문한 엣지 강조 (applyEdgeVisibility 간섭 없이 직접 제어)
    const allPathEdgeIds = playbackEdgeIdsRef.current
    setEdges((eds) => eds.map((e) => {
      if (!allPathEdgeIds.has(e.id)) return e
      const isActive = e.id === activeEdgeId
      const isVisited = visitedEdgeIds.has(e.id)
      return {
        ...e,
        hidden: false,
        animated: isActive,
        style: {
          strokeWidth: isActive ? 3.5 : isVisited ? 2 : 1,
          stroke: isActive ? '#fbbf24' : isVisited ? '#22d3ee' : '#4b5563',
        },
      }
    }))
  }, [playbackCursor, playbackItems, setNodes, setEdges])

  // 흐름 재생 — 재생 중 현재 노드를 화면 중앙으로 이동
  useEffect(() => {
    if (!playbackPlaying || playbackItems.length === 0 || playbackCursor < 0) return
    fitView({ nodes: [{ id: playbackItems[playbackCursor].id }], duration: 300, padding: 0.5, maxZoom: 1.5 })
  }, [playbackCursor, playbackItems, playbackPlaying, fitView])

  // 흐름 재생 — 자동 진행 타이머 (분기점에서 자동 일시정지)
  useEffect(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    if (!playbackPlaying || playbackCursor >= playbackItems.length - 1) {
      if (playbackCursor >= playbackItems.length - 1 && playbackPlaying) setPlaybackPlaying(false)
      return
    }
    // 현재 노드가 분기점이면 자동 일시정지
    if (callTree && playbackCursor >= 0) {
      const treeNode = findTreeNode(callTree, playbackItems[playbackCursor].id)
      if (treeNode && treeNode.children.length > 1) {
        setPlaybackPlaying(false)
        return
      }
    }
    playbackTimerRef.current = setTimeout(() => {
      setPlaybackCursor((c) => c + 1)
    }, playbackSpeed)
    return () => { if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current) }
  }, [playbackPlaying, playbackCursor, playbackItems, playbackSpeed, callTree])

  // 흐름 재생 시작 — 호출 트리 빌드 후 기본 경로로 재생 시작
  const startPlayback = useCallback((nodeId: string) => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    const { tree, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes } = buildCallTree(nodeId, rawEdgesCache, rawNodes)
    const items = pathToPlaybackItems(defaultNodeIds, defaultEdgeIds, defaultEdgeTypes)
    const edgeIds = new Set(defaultEdgeIds.filter(Boolean))
    playbackEdgeIdsRef.current = edgeIds
    setCallTree(tree)
    setActivePath({ nodeIds: defaultNodeIds, edgeIds: defaultEdgeIds, edgeTypes: defaultEdgeTypes })
    setPlaybackItems(items)
    setPlaybackCursor(0)
    setPlaybackPlaying(false)
    setEdges((eds) => eds.map((e) => edgeIds.has(e.id) ? { ...e, hidden: false } : e))
  }, [rawEdgesCache, rawNodes, setEdges])

  // 사이드바 노드 변경 시 AI 설명 초기화
  useEffect(() => { setAiExplanation(null) }, [sidebar])

  // 선택한 AI 제공자로 함수 노드 설명 요청
  const handleAiExplain = async () => {
    if (!selectedAiProvider || sidebar?.kind !== 'func') return
    setAiExplaining(true)
    setAiExplanation(null)
    try {
      const s = sidebar
      const res = await axios.post<{ explanation: string }>(
        '/api/ai/explain',
        {
          provider: selectedAiProvider,
          nodeId: s.funcName,
          nodeName: s.funcName,
          nodeType: 'FUNCTION',
          comment: s.funcComment ?? '',
          callers: s.callers.map((c: { funcName: string }) => c.funcName).join(', '),
          callees: s.callees.map((c: { funcName: string }) => c.funcName).join(', '),
        },
        { headers: authHeaders() }
      )
      setAiExplanation(res.data.explanation)
    } catch {
      setAiExplanation('AI 설명을 가져오지 못했습니다. API 키를 확인해주세요.')
    } finally {
      setAiExplaining(false)
    }
  }

  // 흐름 재생 초기화
  const resetPlayback = useCallback(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    playbackEdgeIdsRef.current = new Set()
    setPlaybackPlaying(false)
    setPlaybackCursor(-1)
    setPlaybackItems([])
    setCallTree(null)
    setActivePath({ nodeIds: [], edgeIds: [], edgeTypes: [] })
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

  // 트리에서 분기 선택 — 해당 노드까지의 경로로 재생 전환
  const selectBranch = useCallback((nodeId: string) => {
    if (!callTree) return
    const path = findPathInTree(callTree, nodeId)
    if (!path) return
    extendToDefaultLeaf(callTree, nodeId, path.nodeIds, path.edgeIds, path.edgeTypes)
    const items = pathToPlaybackItems(path.nodeIds, path.edgeIds, path.edgeTypes)
    const edgeIds = new Set(path.edgeIds.filter(Boolean))
    playbackEdgeIdsRef.current = edgeIds
    setActivePath(path)
    setPlaybackItems(items)
    setPlaybackCursor(0)
    setPlaybackPlaying(false)
    setEdges((eds) => eds.map((e) => edgeIds.has(e.id) ? { ...e, hidden: false } : e))
  }, [callTree, setEdges])

  // 서버에서 그래프 데이터를 불러와 React Flow 레이아웃으로 변환
  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { graphId: gid, nodes: rn, edges: re, warnings: w } = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[]; warnings?: { type: string; nodeIds: string[]; edgeIds?: string[]; message: string }[] }
      setGraphId(gid)
      const warningList = w ?? []
      setWarnings(warningList)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode, layoutPreset, openFileSidebar)
      setRawNodes(rn)
      setRawEdgesCache(re)
      const warnIds = new Set(warningList.flatMap(x => x.nodeIds))
      const warnEdgeIds = new Set(warningList.flatMap(x => x.edgeIds ?? []))
      const styledNodes = layoutNodes.map(n =>
        warnIds.has(n.id) ? { ...n, style: { ...((n.style as object) ?? {}), outline: '2px solid #eab308', outlineOffset: '2px' } } : n
      )
      const baseEdges = layoutEdges.filter((e, i, arr) => arr.findIndex(x => x.id === e.id) === i)
      const styledEdges = baseEdges.map(e =>
        warnEdgeIds.has(e.id) ? { ...e, style: { ...((e.style as object) ?? {}), stroke: '#eab308', strokeWidth: 2 }, animated: true } : e
      )
      setNodes(styledNodes.filter((n, i, arr) => arr.findIndex(x => x.id === n.id) === i))
      setEdges(applyEdgeVisibility(styledEdges, false, false, false, true, false, true))
      setCounts({
        files: rn.filter((n) => n.type === 'FILE').length,
        funcs: rn.filter((n) => n.type === 'FUNCTION').length,
        edges: re.length,
      })
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)
      if (!isTourDone()) setTimeout(() => setTourRunning(true), 800)
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
    axios.get<{ provider: string; registered: boolean }[]>('/api/ai/keys', { headers: authHeaders() })
      .then((res) => {
        setAiProviders(res.data)
        const first = res.data.find((p) => p.registered)
        if (first) setSelectedAiProvider(first.provider)
      })
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

  // 배너에서 바로 재분석 시작 — 완료 후 그래프 새로고침
  const handleReanalyzeNow = async () => {
    if (!outdated || reanalyzing) return
    setReanalyzing(true)
    try {
      const res = await axios.post<{ analysisId: string }>(
        '/api/analyses',
        { projectId, branch: outdated.branch },
        { headers: authHeaders() }
      )
      const analysisId = res.data.analysisId
      const poll = setInterval(async () => {
        try {
          const s = await axios.get<{ status: string }>(`/api/analyses/${analysisId}`, { headers: authHeaders() })
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
    hiddenLayers: Array.from(shareHiddenLayers),
    hiddenGroups: Array.from(shareHiddenGroups),
    hiddenNodes: Array.from(shareHiddenNodes),
  }), [layoutPreset, labelMode, showEdges, showCallEdges, showInstEdges, showBrokenEdges, showDbEdges, showApiCallEdges, opaqueLayerSet, shareHiddenLayers, shareHiddenGroups, shareHiddenNodes])

  // 프리셋 config를 현재 뷰 상태에 적용
  const applyPresetConfig = useCallback((config: Record<string, unknown>) => {
    const lp = (config.layoutPreset as LayoutPreset) ?? 'layer'
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
    setShareHiddenLayers(new Set((config.hiddenLayers as string[]) ?? []))
    setShareHiddenGroups(new Set((config.hiddenGroups as string[]) ?? []))
    setShareHiddenNodes(new Set((config.hiddenNodes as string[]) ?? []))

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
      const res = await axios.get(`/api/graphs/${gid}/presets`, { headers: authHeaders() })
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
        { name: presetSaveName.trim(), config },
        { headers: authHeaders() }
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

  // 검색어가 있으면 일치 노드 강조, 나머지 반투명 처리
  useEffect(() => {
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
  }, [nodeSearchQuery, rawNodes, setNodes])

  // 경고 목록을 타입별로 그룹핑하여 마크다운 파일로 다운로드
  const downloadWarningsMd = (warningList: { type: string; nodeIds: string[]; message: string }[]) => {
    const WARNING_LABELS: Record<string, string> = {
      CYCLIC_IMPORT: '순환 의존 (CYCLIC_IMPORT)',
      BROKEN_INTERFACE_CHAIN: '인터페이스 미구현 (BROKEN_INTERFACE_CHAIN)',
      ASYNC_SELF_CALL: '@Async 자기 호출 (ASYNC_SELF_CALL)',
      DB_LAYER_BYPASS: 'DB 레이어 우회 (DB_LAYER_BYPASS)',
      CROSS_CONTEXT_IMPORT: 'DDD 경계 위반 (CROSS_CONTEXT_IMPORT)',
      MISSING_CONVERTER_MIGRATION: '@Convert 마이그레이션 필요 (MISSING_CONVERTER_MIGRATION)',
    }
    const grouped = new Map<string, string[]>()
    for (const w of warningList) {
      if (!grouped.has(w.type)) grouped.set(w.type, [])
      grouped.get(w.type)!.push(w.message)
    }
    const lines = [`# 런타임 경고 리포트\n`, `> 총 ${warningList.length}개 경고\n`]
    for (const [type, msgs] of grouped.entries()) {
      lines.push(`\n## ${WARNING_LABELS[type] ?? type} (${msgs.length}개)\n`)
      msgs.forEach(m => lines.push(`- ${m.replace(/^[^:]+:\s*/, '')}`))
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'codeprint-warnings.md'
    a.click()
    URL.revokeObjectURL(a.href)
  }

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

  // 함수 노드 선택 — 사이드바 갱신 + 흐름 재생 시작. 사이드바 링크 클릭(onNav)에서도 재사용
  const openFuncNode = useCallback((nodeId: string) => {
    const rawFunc = rawNodes.find((n) => n.id === nodeId && n.type === 'FUNCTION')
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
  }, [rawNodes, rawEdgesCache, graphId, startPlayback])

  const handleNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    publishSelection(node.id)
    if (node.type === 'fileNode' || node.type === 'groupNode' || node.type === 'sectionNode') {
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
  }, [rawNodes, rawEdgesCache, openFuncNode, startPlayback, resetPlayback])

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
    <div style={{ width: '100vw', height: '100vh', background: '#030712', display: 'flex', flexDirection: 'column' }}>
      <div style={{ position: 'relative' }}>
        <AppHeader />
        {projectId && currentUserId && (
          <div style={{ position: 'absolute', right: '1rem', top: '50%', transform: 'translateY(-50%)', zIndex: 50 }}>
            <CollaborationPanel
              graphId={projectId}
              myUserId={currentUserId}
              participants={collabState.participants}
              connected={collabState.connected}
              sessionId={collabSessionId}
              inviteCode={collabInviteCode}
              onSessionReady={(sid, code) => { setCollabSessionId(sid); setCollabInviteCode(code) }}
            />
          </div>
        )}
      </div>
    <div ref={flowRef} style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>

      <OnboardingTour run={tourRunning} onFinish={() => setTourRunning(false)} />
      <CursorOverlay cursors={collabState.cursors} />


      {/* 최신 커밋 감지 배너 */}
      {outdated && (
        <div className="absolute top-0 left-0 right-0 z-30 flex items-center justify-between px-4 py-2 bg-yellow-900/80 border-b border-yellow-700 text-yellow-300 text-xs backdrop-blur-sm">
          <span>
            ⚠️ <strong>{outdated.branch}</strong> 브랜치에 새 커밋이 있습니다. 마지막 분석: {new Date(outdated.lastAnalyzedAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={handleReanalyzeNow}
              disabled={reanalyzing}
              className="px-2 py-0.5 rounded bg-yellow-700/60 hover:bg-yellow-600/80 disabled:opacity-50 text-yellow-100 font-medium"
            >
              {reanalyzing ? '분석 중...' : '지금 재분석'}
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
        {/* 키보드 단축키 도움말 */}
        <div className="relative group">
          <button className="text-gray-500 hover:text-gray-300 text-xs w-6 h-6 rounded-full border border-gray-700 hover:border-gray-500 flex items-center justify-center transition-colors">
            ?
          </button>
          <div className="absolute left-0 top-8 hidden group-hover:block bg-gray-900 border border-gray-700 rounded-lg p-3 text-xs text-gray-300 whitespace-nowrap z-50 shadow-xl">
            <div className="font-semibold text-gray-200 mb-2">키보드 단축키</div>
            {([['/', '검색창 포커스'], ['Esc', '사이드바 닫기'], ['F', '전체 화면 맞춤'], ['L', '라벨 이름/주석 전환']] as [string, string][]).map(([key, desc]) => (
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
              onClick={() => navigate('/dashboard')}
              className="text-xs font-bold text-gray-300 hover:text-white tracking-widest uppercase transition-colors"
            >
              Codeprint
            </button>
            <button onClick={() => setLeftOpen(false)} className="text-gray-600 hover:text-white text-sm leading-none" title="사이드바 접기">‹</button>
          </div>

          <div className="flex flex-col gap-0 flex-1">

            {/* 노드 검색 */}
            <LeftSection title="노드 검색">
              <input
                ref={searchInputRef}
                type="text"
                value={nodeSearchQuery}
                onChange={e => setNodeSearchQuery(e.target.value)}
                placeholder="파일명 / 함수명 검색... (/)"
                className="w-full text-xs bg-gray-800 border border-gray-700 rounded px-2 py-1 text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500"
              />
              {nodeSearchQuery.trim() && (() => {
                const q = nodeSearchQuery.trim().toLowerCase()
                const results = rawNodes.filter(n =>
                  n.type !== 'GROUP' &&
                  (n.name.toLowerCase().includes(q) || (n.comment ?? '').toLowerCase().includes(q))
                ).slice(0, 10)
                return results.length === 0 ? (
                  <p className="text-[10px] text-gray-600 mt-1 px-1">결과 없음</p>
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
                          {n.type === 'FILE' ? '📄' : n.type === 'FUNCTION' ? 'ƒ' : n.type === 'DB_TABLE' ? '🗄' : '◎'}
                        </span>
                        {n.comment || n.name}
                      </button>
                    ))}
                  </div>
                )
              })()}
            </LeftSection>

            {/* 내보내기 — 최상단 */}
            <LeftSection title="내보내기">
              <button onClick={() => downloadTreeText(rawNodes)} disabled={rawNodes.length === 0}
                className="w-full text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 disabled:opacity-40 disabled:cursor-not-allowed">
                ↓ AI 컨텍스트
              </button>
              <button id="tour-export" onClick={handleExportImage} disabled={exporting || rawNodes.length === 0}
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

            {/* 뷰 프리셋 */}
            <LeftSection title="뷰 프리셋">
              <div className="flex flex-col gap-1">
                {presets.map((p) => (
                  <div key={p.slot} className="flex items-center gap-1">
                    <button
                      onClick={() => applyPresetConfig(p.config)}
                      title={`슬롯 ${p.slot} 불러오기`}
                      className="flex-1 text-left text-xs px-2 py-1.5 rounded bg-gray-800/60 hover:bg-gray-800 text-gray-300 truncate"
                    >
                      <span className="text-gray-500 mr-1">{p.slot}.</span>
                      {p.name}
                      {p.isDefault && <span className="ml-1 text-gray-600 text-[10px]">기본</span>}
                    </button>
                    <button
                      onClick={() => {
                        setPendingSaveSlot(p.slot)
                        setPresetSaveName(p.name)
                        setShowSavePresetModal(true)
                      }}
                      title={`슬롯 ${p.slot}에 현재 뷰 저장`}
                      className="text-gray-600 hover:text-gray-300 text-xs px-1.5 py-1 rounded hover:bg-gray-800 flex-shrink-0"
                    >
                      💾
                    </button>
                  </div>
                ))}
              </div>
            </LeftSection>

            {/* 레이아웃 */}
            <LeftSection title="레이아웃" id="tour-layout">
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">뷰</span>
                <button
                  onClick={toggleLayoutPreset}
                  className="flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2 py-1 rounded border border-gray-700"
                >
                  <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>계층형</span>
                  <span className="text-gray-600">/</span>
                  <span className={layoutPreset === 'domain' ? 'text-white' : 'text-gray-500'}>도메인</span>
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

            {/* 노드 타입 가시성 필터 */}
            <LeftSection title="노드">
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

            {/* 엣지 — 색인 + 토글 통합 */}
            <LeftSection title="엣지" id="tour-edges">
              <div className="grid grid-cols-2 gap-x-1 gap-y-0.5">
              {[
                { key: 'import',  icon: <span className="block w-4 h-0.5" style={{ background: showEdges ? '#4b5563' : '#374151' }} />,                                                                                              label: '의존성',    textCls: showEdges ? 'text-gray-300' : 'text-gray-600',        active: showEdges,        onToggle: toggleEdges },
                { key: 'call',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showCallEdges ? '#f59e0b' : '#78350f'} strokeWidth="1.5" strokeDasharray="5 4" /></svg>,                                label: '콜 체인',   textCls: showCallEdges ? 'text-amber-400' : 'text-gray-600',    active: showCallEdges,    onToggle: toggleCallEdges },
                { key: 'inst',    icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showInstEdges ? '#a855f7' : '#4c1d95'} strokeWidth="1.5" strokeDasharray="3 4" /></svg>,                                label: '생성',      textCls: showInstEdges ? 'text-purple-400' : 'text-gray-600',   active: showInstEdges,    onToggle: toggleInstEdges },
                { key: 'broken',  icon: <span className="block w-4 h-0.5" style={{ background: showBrokenEdges ? '#ef4444' : '#450a0a' }} />,                                                                                        label: '끊긴 연결', textCls: showBrokenEdges ? 'text-red-400' : 'text-gray-600',   active: showBrokenEdges,  onToggle: toggleBrokenEdges },
                { key: 'db',      icon: <svg width="16" height="4"><line x1="0" y1="2" x2="3.5" y2="2" stroke={showDbEdges ? '#22d3ee' : '#374151'} strokeWidth="1.5"/><line x1="4.5" y1="2" x2="8" y2="2" stroke={showDbEdges ? '#4ade80' : '#374151'} strokeWidth="1.5"/><line x1="9" y1="2" x2="12.5" y2="2" stroke={showDbEdges ? '#facc15' : '#374151'} strokeWidth="1.5"/><line x1="13.5" y1="2" x2="16" y2="2" stroke={showDbEdges ? '#f87171' : '#374151'} strokeWidth="1.5"/></svg>, label: 'DB 연결',   textCls: showDbEdges ? 'text-cyan-400' : 'text-gray-600',       active: showDbEdges,      onToggle: toggleDbEdges },
                { key: 'api',     icon: <svg width="16" height="4"><line x1="0" y1="2" x2="16" y2="2" stroke={showApiCallEdges ? '#e879f9' : '#701a75'} strokeWidth="1.5" strokeDasharray="6 3" /></svg>,                              label: 'API 호출',  textCls: showApiCallEdges ? 'text-fuchsia-400' : 'text-gray-600', active: showApiCallEdges, onToggle: toggleApiCallEdges },
              ].map(({ key, icon, label, textCls, active, onToggle }) => (
                <div key={key} onClick={onToggle} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onToggle()}
                  className={`flex items-center gap-1.5 px-1.5 py-1 rounded cursor-pointer hover:bg-gray-800/60 ${active ? '' : 'opacity-40'}`}>
                  <span className="w-4 flex-shrink-0">{icon}</span>
                  <span className={`text-xs truncate ${textCls}`}>{label}</span>
                </div>
              ))}
              </div>
            </LeftSection>

            {/* 범례 — 계층형/도메인 레이어 + 노드 */}
            <LeftSection title="범례">
              {layoutPreset === 'domain' && (
                <>
                  <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">도메인</p>
                  <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 mb-2">
                  {[
                    { label: 'Project',       color: '#3b82f6', key: 'project' },
                    { label: 'User',          color: '#10b981', key: 'user' },
                    { label: 'Graph',         color: '#8b5cf6', key: 'graph' },
                    { label: 'Analysis',      color: '#f59e0b', key: 'analysis' },
                    { label: 'Community',     color: '#06b6d4', key: 'community' },
                    { label: 'AI',            color: '#e879f9', key: 'ai' },
                    { label: 'Notice',        color: '#f97316', key: 'notice' },
                    { label: 'Donation',      color: '#4ade80', key: 'donation' },
                    { label: 'Collaboration', color: '#fb7185', key: 'collaboration' },
                    { label: 'Common',        color: '#6b7280', key: 'common' },
                  ].map(({ label, color, key }) => {
                    const active = opaqueDomainSet.has(key)
                    return (
                      <div key={key} className="flex items-center gap-1.5 py-0.5">
                        <button
                          onClick={() => toggleDomainOpaque(key)}
                          title={active ? '내용 표시' : '내용 가리기'}
                          style={{
                            width: 16, height: 16, borderRadius: 3,
                            border: `1px solid ${color}88`,
                            background: active ? color : `${color}22`,
                            color: active ? '#fff' : color,
                            fontSize: 9, cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            flexShrink: 0,
                          }}
                        >
                          {active ? '◑' : '○'}
                        </button>
                        <span
                          className="text-gray-400 text-xs truncate cursor-pointer hover:text-white transition-colors"
                          onClick={() => handleFitToDomain(key)}
                          title="이 도메인으로 이동"
                        >
                          {label}
                        </span>
                      </div>
                    )
                  })}
                  </div>
                  <div className="border-t border-gray-800 my-2" />
                </>
              )}
              {layoutPreset === 'layer' && (
                <>
                  <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">계층형 레이어</p>
                  <div className="grid grid-cols-2 gap-x-2 gap-y-0.5">
                  {[
                    { label: 'Domain',        color: '#3b82f6', key: 'domain' },
                    { label: 'Application',   color: '#eab308', key: 'application' },
                    { label: 'Infra',         color: '#a855f7', key: 'infrastructure' },
                    { label: 'Interfaces',    color: '#10b981', key: 'interfaces' },
                    { label: 'Pages',         color: '#06b6d4', key: 'pages' },
                    { label: 'Components',    color: '#0ea5e9', key: 'components' },
                    { label: 'Hooks/Utils',   color: '#f97316', key: 'hooks' },
                    { label: 'Database',      color: '#ef4444', key: 'database' },
                  ].map(({ label, color, key }) => {
                    const active = opaqueLayerSet.has(key)
                    return (
                      <div key={key} className="flex items-center gap-1.5 py-0.5">
                        <button
                          onClick={() => toggleLayerOpaque(key)}
                          title={active ? '내용 표시' : '내용 가리기'}
                          style={{
                            width: 16, height: 16, borderRadius: 3,
                            border: `1px solid ${color}88`,
                            background: active ? color : `${color}22`,
                            color: active ? '#fff' : color,
                            fontSize: 9, cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            flexShrink: 0,
                          }}
                        >
                          {active ? '◑' : '○'}
                        </button>
                        <span className="text-gray-400 text-xs truncate">{label}</span>
                      </div>
                    )
                  })}
                  </div>
                  <div className="border-t border-gray-800 my-2" />
                </>
              )}
              <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">노드</p>
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#1e3a5f', border: '1.5px solid #3b82f6' }} />
                  <span className="text-gray-400 text-xs">FILE</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded flex-shrink-0" style={{ background: '#064e3b', border: '1px solid #10b981' }} />
                  <span className="text-gray-400 text-xs">FUNCTION</span>
                </div>
              </div>
            </LeftSection>

            {/* 런타임 경고 패널 */}
            {warnings.length > 0 && (
              <LeftSection title={`경고 (${warnings.length})`} headerRight={
                <button
                  onClick={() => downloadWarningsMd(warnings)}
                  title="경고 목록 마크다운으로 내보내기"
                  className="text-gray-500 hover:text-gray-300 text-[10px] px-1.5 py-0.5 rounded hover:bg-gray-800 transition-colors"
                >
                  ↓ MD
                </button>
              }>
                <WarningPanel warnings={warnings} onNodeNavigate={handleSearchNodeClick} />
              </LeftSection>
            )}
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
        onMouseMove={handleCollabMouseMove}
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
          position="bottom-center"
        />
      </ReactFlow>

      {/* 우측 사이드바 — 항상 표시 */}
      <aside
        className="absolute right-0 top-0 h-full bg-gray-950 border-l border-gray-800 z-40 flex flex-col shadow-2xl transition-all duration-200"
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
                  : sidebar.kind === 'warning' ? '⚠️ 경고 상세'
                  : '인스턴스화'}
              </span>
              {sidebar && <button onClick={() => setSidebar(null)} className="text-gray-600 hover:text-white text-sm">✕</button>}
            </div>

              <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">

                {/* ── 흐름 재생 패널 — 호출 트리 + 재생 컨트롤 ── */}
                {callTree && (
                  <div className="bg-gray-800/60 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] text-gray-400 uppercase tracking-wider">흐름 재생</span>
                      <button onClick={resetPlayback} className="text-gray-600 hover:text-gray-400 text-xs" title="초기화">✕</button>
                    </div>
                    <CallTreePanel
                      root={callTree}
                      activeNodeIds={new Set(activePath.nodeIds)}
                      onSelectBranch={selectBranch}
                    />
                    {playbackItems.length > 1 && (
                      <div className="border-t border-gray-700/50 pt-2 flex flex-col gap-2">
                        {/* 현재 스텝 전환 레이블 — 레이어 경계 표시 */}
                        {playbackCursor > 0 && playbackItems[playbackCursor].incomingEdgeType && (
                          <div className="flex items-center gap-1.5 px-1">
                            <span className="text-gray-600 text-[9px]">▸</span>
                            <span className="text-[9px] text-amber-400/80 bg-amber-900/20 px-1.5 py-0.5 rounded">
                              {EDGE_TYPE_LABEL[playbackItems[playbackCursor].incomingEdgeType!] ?? playbackItems[playbackCursor].incomingEdgeType}
                            </span>
                          </div>
                        )}
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] text-gray-600">
                            {playbackCursor < 0 ? '-' : `${playbackCursor + 1} / ${playbackItems.length} 단계`}
                          </span>
                          <div className="flex items-center gap-1">
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
                        <div className="w-full h-1 bg-gray-700 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-amber-400 rounded-full transition-all duration-300"
                            style={{ width: playbackCursor < 0 ? '0%' : `${((playbackCursor + 1) / playbackItems.length) * 100}%` }}
                          />
                        </div>
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
                        </div>
                      </div>
                    )}
                  </div>
                )}

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
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.callerNodeId }], duration: 500, padding: 0.4 }), 50) }}
                        >{sidebar.callerComment ?? sidebar.callerName}</p>
                        <p className="text-blue-400 font-mono text-xs cursor-pointer hover:text-blue-300 mt-0.5"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.callerFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.callerFile}</p>
                      </div>
                      <div className="text-amber-500 text-sm text-center">↓</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">호출받는 함수</p>
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
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">생성하는 파일</p>
                        <p className="text-blue-300 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.sourceNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.sourceFile}</p>
                      </div>
                      <div className="text-purple-400 text-sm text-center">↓ new</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">인스턴스화 대상</p>
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
                    <SidebarSection title={`함수 호출 체인${sidebar.callChain.length > 0 ? ` (${sidebar.callChain.length})` : ''}`}>
                      {sidebar.callChain.length === 0
                        ? <p className="text-gray-700 text-xs">분석된 함수 호출 없음</p>
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
                    <SidebarSection title={`의존하는 파일 — import${sidebar.data.outgoing.length > 0 ? ` (${sidebar.data.outgoing.length})` : ''}`}>
                      {sidebar.data.outgoing.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
                        : sidebar.data.outgoing.map((c, i) => (
                          <FileConnGroup key={i} entry={c} direction="out"
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.3 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>
                    <SidebarSection title={`이 파일을 import하는 곳${sidebar.data.incoming.length > 0 ? ` (${sidebar.data.incoming.length})` : ''}`}>
                      {sidebar.data.incoming.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
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
                    <SidebarSection title={`호출하는 함수${sidebar.callers.length > 0 ? ` (${sidebar.callers.length})` : ''}`}>
                      {sidebar.callers.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
                        : sidebar.callers.map((c, i) => (
                          <FuncChainRow key={i} entry={c} direction="caller" labelMode={labelMode}
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                            onNodeClick={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>
                    <SidebarSection title={`호출받는 함수${sidebar.callees.length > 0 ? ` (${sidebar.callees.length})` : ''}`}>
                      {sidebar.callees.length === 0
                        ? <p className="text-gray-700 text-xs">없음</p>
                        : sidebar.callees.map((c, i) => (
                          <FuncChainRow key={i} entry={c} direction="callee" labelMode={labelMode}
                            onNav={(id) => { setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                            onNodeClick={(id) => { openFuncNode(id); setTimeout(() => fitView({ nodes: [{ id }], duration: 500, padding: 0.4 }), 50) }}
                          />
                        ))
                      }
                    </SidebarSection>

                    {/* AI 설명 */}
                    {aiProviders.some((p) => p.registered) && (
                      <SidebarSection title="AI 설명">
                        <div className="flex items-center gap-2 mb-2">
                          <select
                            value={selectedAiProvider}
                            onChange={(e) => setSelectedAiProvider(e.target.value)}
                            className="flex-1 bg-gray-800 text-white text-xs px-2 py-1.5 rounded border border-gray-700 focus:outline-none"
                          >
                            {aiProviders.filter((p) => p.registered).map((p) => (
                              <option key={p.provider} value={p.provider}>
                                {p.provider === 'CLAUDE' ? 'Claude' : p.provider === 'OPENAI' ? 'ChatGPT' : 'Gemini'}
                              </option>
                            ))}
                          </select>
                          <button
                            onClick={handleAiExplain}
                            disabled={aiExplaining}
                            className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-2 py-1.5 rounded disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
                          >
                            {aiExplaining ? '생성 중...' : '설명'}
                          </button>
                        </div>
                        {aiExplanation && (
                          <p className="text-xs text-gray-300 leading-relaxed bg-gray-800 rounded-lg px-3 py-2">
                            {aiExplanation}
                          </p>
                        )}
                        {!aiExplanation && !aiExplaining && (
                          <p className="text-xs text-gray-600">버튼을 눌러 이 함수의 역할을 설명받으세요.</p>
                        )}
                      </SidebarSection>
                    )}

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
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.repoFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.repoFile}</p>
                      </div>
                      <div className="text-center text-sm" style={{ color: DB_CRUD_COLOR[sidebar.crudType] ?? '#22d3ee' }}>
                        ↓ {sidebar.crudType.replace('DB_', '')}
                      </div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">DB 테이블</p>
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
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">프론트 파일</p>
                        <p className="text-fuchsia-400 font-mono text-sm font-semibold cursor-pointer hover:text-white"
                          onClick={() => { setTimeout(() => fitView({ nodes: [{ id: sidebar.frontFileNodeId }], duration: 500, padding: 0.3 }), 50) }}
                        >{sidebar.frontFile}</p>
                      </div>
                      <div className="text-gray-500 mt-5">→</div>
                      <div>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">컨트롤러</p>
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
                      <span className="text-xs text-gray-500">{sidebar.nodeWarnings.length}개 경고</span>
                    </div>
                    {sidebar.nodeWarnings.map((w, i) => {
                      const WARNING_COLORS: Record<string, string> = {
                        CYCLIC_IMPORT: '#f97316', BROKEN_INTERFACE_CHAIN: '#ef4444',
                        ASYNC_SELF_CALL: '#eab308', DB_LAYER_BYPASS: '#8b5cf6',
                        CROSS_CONTEXT_IMPORT: '#06b6d4', MISSING_CONVERTER_MIGRATION: '#ec4899',
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

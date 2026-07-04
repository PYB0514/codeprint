// 커뮤니티 게시글에 첨부된 그래프 — 숨김 필터 적용 읽기 전용 뷰어
import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import axios from 'axios'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  ReactFlowProvider,
  useReactFlow,
  type NodeMouseHandler,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { buildLayout, applyEdgeVisibility, searchNodes, getGroupKey, findCommonPrefix, downloadWarningsMd, GRAPH_MIN_ZOOM, GRAPH_MAX_ZOOM } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset } from '../utils/graphLayout'
import type { Node, Edge } from '@xyflow/react'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'
import WarningPanel from '../components/WarningPanel'
import { LayoutPresetToggle, LabelModeToggle } from '../components/GraphViewToggles'
import { GraphLegend } from '../components/GraphLegend'
import { CornerPanel } from '../components/CornerPanel'
import { useSidebarResize } from '../hooks/useSidebarResize'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

const DDD_LAYERS = ['domain', 'application', 'infrastructure', 'interfaces', 'pages', 'components', 'hooks', 'utils']

// filePath에서 레이어 이름 추출
function getLayer(filePath: string): string {
  const parts = filePath.replace(/\\/g, '/').split('/')
  for (const part of parts) {
    if (DDD_LAYERS.includes(part)) return part
  }
  return 'root'
}

// 숨김 필터를 적용하여 노드를 걸러내는 함수 (레거시 단일 첨부 전용)
function applyHiddenFilter(
  nodes: RawNode[],
  hiddenLayers: string[],
  hiddenGroups: string[],
  hiddenNodeNames: string[]
): RawNode[] {
  const commonPrefix = findCommonPrefix(nodes.filter(n => n.type === 'FILE').map(n => n.filePath))
  return nodes.filter((n) => {
    if (hiddenNodeNames.includes(n.name)) return false
    if (n.filePath) {
      const layer = getLayer(n.filePath)
      if (hiddenLayers.includes(layer)) return false
      const groupKey = getGroupKey(n.filePath, commonPrefix)
      if (hiddenGroups.includes(groupKey)) return false
    }
    return true
  })
}

// layer 모드에서 opaque 섹션의 자손 노드 hidden 처리 (ShareGraphPage와 동일 로직)
function applyOpaqueLayerSet(nodes: Node[], opaqueLayerSet: Set<string>): Node[] {
  const opaqueSectionIds = new Set(
    nodes
      .filter((n) => n.id.startsWith('layer-section-') && opaqueLayerSet.has(n.id.replace('layer-section-', '')))
      .map((n) => n.id)
  )
  const groupIds = new Set(nodes.filter((n) => n.parentId && opaqueSectionIds.has(n.parentId!)).map((n) => n.id))
  const fileIds  = new Set(nodes.filter((n) => n.parentId && groupIds.has(n.parentId!)).map((n) => n.id))
  const hiddenIds = new Set([...groupIds, ...fileIds,
    ...nodes.filter((n) => n.parentId && fileIds.has(n.parentId!)).map((n) => n.id)])
  return nodes.map((n) => {
    if (n.id.startsWith('layer-section-')) {
      const isOpaque = opaqueLayerSet.has(n.id.replace('layer-section-', ''))
      return { ...n, hidden: false, data: { ...n.data, opaque: isOpaque } }
    }
    if (hiddenIds.has(n.id)) return { ...n, hidden: true }
    return n
  })
}

// NODE_TYPE_LABEL 매핑
const NODE_TYPE_LABEL: Record<string, string> = {
  fileNode: 'FILE',
  FILE: 'FILE',
  FUNCTION: 'FUNCTION',
  DB_TABLE: 'DB',
  API_ENDPOINT: 'API',
}

// 게시글 첨부 그래프 뷰어 내부 컴포넌트 (레거시 — post.graphId 단일 첨부 전용)
function CommunityPostGraphInner() {
  const { postId } = useParams<{ postId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [hiddenSummary, setHiddenSummary] = useState<string[]>([])

  useEffect(() => {
    if (!postId) return
    axios
      .get(`/api/community/posts/${postId}/graph`)
      .then((res) => {
        const { nodes: rawNodes, edges: rawEdges, hiddenLayers, hiddenGroups, hiddenNodeNames } =
          res.data as {
            graphId: string
            nodes: RawNode[]
            edges: RawEdge[]
            hiddenLayers: string[]
            hiddenGroups: string[]
            hiddenNodeNames: string[]
          }

        const filteredNodes = applyHiddenFilter(rawNodes, hiddenLayers, hiddenGroups, hiddenNodeNames)
        const filteredNodeIds = new Set(filteredNodes.map((n) => n.id))
        const filteredEdges = rawEdges.filter(
          (e) => filteredNodeIds.has(e.source) && filteredNodeIds.has(e.target)
        )

        const { nodes: builtNodes, edges: builtEdges } = buildLayout(filteredNodes, filteredEdges, 'name', 'layer')
        setNodes(builtNodes)
        setEdges(builtEdges)

        // 숨겨진 항목 요약
        const summary: string[] = []
        if (hiddenLayers.length > 0) summary.push(`레이어: ${hiddenLayers.join(', ')}`)
        if (hiddenGroups.length > 0) summary.push(`그룹: ${hiddenGroups.join(', ')}`)
        if (hiddenNodeNames.length > 0) summary.push(`노드 ${hiddenNodeNames.length}개`)
        setHiddenSummary(summary)
      })
      .catch(() => setError('그래프를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [postId])

  if (loading) {
    return (
      <div className="app-page min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">{error}</p>
        <button onClick={() => navigate('/community')} className="text-sm underline text-gray-400">
          커뮤니티로
        </button>
      </div>
    )
  }

  return (
    <div className="app-page w-screen h-screen bg-gray-950 flex flex-col">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800 shrink-0">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/community')} className="font-bold text-white text-sm hover:text-gray-300">
            Codeprint
          </button>
          <span className="text-gray-500 text-xs">커뮤니티 공유 그래프</span>
        </div>
        {hiddenSummary.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">일부 비공개:</span>
            <span className="text-xs text-yellow-500">{hiddenSummary.join(' / ')}</span>
          </div>
        )}
      </div>

      <div className="flex-1">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          fitView
          onlyRenderVisibleElements
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
        >
          <Background color="#374151" gap={20} />
          <Controls />
          <MiniMap nodeColor="#6b7280" maskColor="rgba(17,24,39,0.7)" />
        </ReactFlow>
      </div>
    </div>
  )
}

// 게시글 다중 그래프 스냅샷 응답 타입
interface PostSnapshot {
  projectId: string
  graphId: string
  nodes: RawNode[]
  edges: RawEdge[]
  config: {
    layoutPreset?: LayoutPreset
    labelMode?: LabelMode
    edges?: { import?: boolean; call?: boolean; inst?: boolean; broken?: boolean; db?: boolean; api?: boolean }
    opaqueLayerSet?: string[]
  }
  position: number
  warnings: { type: string; nodeIds: string[]; message: string }[]
}

// 노드 코멘트 응답 타입 (읽기 전용)
interface NodeCommentView {
  id: string
  content: string
  userId: string
  createdAt: number
}

// 엣지 타입 토글 항목 정의 (구조 파악 목적 — GraphPage 엣지 섹션과 동일 라벨)
const EDGE_TOGGLE_DEFS: { key: 'se' | 'sc' | 'si' | 'sb' | 'sdb' | 'sapi'; label: string }[] = [
  { key: 'se', label: '의존성' },
  { key: 'sc', label: '콜 체인' },
  { key: 'si', label: '생성' },
  { key: 'sb', label: '끊긴 연결' },
  { key: 'sdb', label: 'DB 연결' },
  { key: 'sapi', label: 'API 호출' },
]

// 게시글 다중 스냅샷 중 하나를 읽기 전용으로 렌더링하는 내부 컴포넌트
function CommunityPostSnapshotInner() {
  const { postId, position } = useParams<{ postId: string; position: string }>()
  const navigate = useNavigate()
  const { fitView } = useReactFlow()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [nodeSearch, setNodeSearch] = useState('')
  const [leftOpen, setLeftOpen] = useState(true)
  const [rightOpen, setRightOpen] = useState(true)
  const { width: leftWidth, startResize: startLeftResize } = useSidebarResize(224, 160, 420, 'left')
  const { width: rightWidth, startResize: startRightResize } = useSidebarResize(256, 240, 520, 'right')
  const [activeDomainTab, setActiveDomainTab] = useState<string>('전체')
  const [rawNodesCache, setRawNodesCache] = useState<RawNode[]>([])
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [builtEdgesCache, setBuiltEdgesCache] = useState<Edge[]>([])
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [edgeVisibility, setEdgeVisibility] = useState({ se: false, sc: false, si: false, sb: true, sdb: false, sapi: true })
  const [opaqueLayerSet, setOpaqueLayerSet] = useState<Set<string>>(new Set())
  const [opaqueDomainSet, setOpaqueDomainSet] = useState<Set<string>>(new Set())
  const [warnings, setWarnings] = useState<{ type: string; nodeIds: string[]; message: string }[]>([])
  const [warningPanelOpen, setWarningPanelOpen] = useState(false)
  const [graphId, setGraphId] = useState<string | null>(null)
  const [nodeComments, setNodeComments] = useState<NodeCommentView[]>([])

  // 노드 클릭 시 우측 사이드바 표시
  const handleNodeClick: NodeMouseHandler = (_, node) => {
    if (node.type === 'sectionNode' || node.type === 'groupNode') return
    setSelectedNode(node)
  }

  // 좌측 노드 목록 클릭 시 해당 노드로 이동
  const handleFocusNode = (nodeId: string) => {
    fitView({ nodes: [{ id: nodeId }], duration: 400, padding: 0.3 })
    const target = nodes.find(n => n.id === nodeId)
    if (target) setSelectedNode(target)
  }

  useEffect(() => {
    if (!postId || position === undefined) return
    const targetPosition = Number(position)
    axios
      .get<PostSnapshot[]>(`/api/community/posts/${postId}/snapshots`)
      .then((res) => {
        const snapshot = res.data.find((s) => s.position === targetPosition)
        if (!snapshot) {
          setError('스냅샷을 찾을 수 없습니다.')
          return
        }

        const cfg = snapshot.config ?? {}
        const lp: LayoutPreset = cfg.layoutPreset ?? 'layer'
        const lm: LabelMode = cfg.labelMode ?? 'name'
        const edgeCfg = cfg.edges ?? {}
        const se = edgeCfg.import ?? false
        const sc = edgeCfg.call ?? false
        const si = edgeCfg.inst ?? false
        const sb = edgeCfg.broken ?? true
        const sdb = edgeCfg.db ?? false
        const sapi = edgeCfg.api ?? true
        const initialOpaqueLayerSet = new Set(cfg.opaqueLayerSet ?? [])

        const { nodes: builtNodes, edges: builtEdges } = buildLayout(snapshot.nodes, snapshot.edges, lm, lp)
        const finalNodes = lp === 'domain' ? builtNodes : applyOpaqueLayerSet(builtNodes, initialOpaqueLayerSet)

        setRawNodesCache(snapshot.nodes)
        setRawEdgesCache(snapshot.edges)
        setBuiltEdgesCache(builtEdges)
        setLayoutPreset(lp)
        setLabelMode(lm)
        setEdgeVisibility({ se, sc, si, sb, sdb, sapi })
        setOpaqueLayerSet(initialOpaqueLayerSet)
        setNodes(finalNodes)
        setEdges(applyEdgeVisibility(builtEdges, se, sc, si, sb, sdb, sapi))
        setGraphId(snapshot.graphId)
        setWarnings(snapshot.warnings ?? [])
        setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)
      })
      .catch(() => setError('그래프를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [postId, position])

  // 선택된 노드가 바뀌면 코멘트 목록을 읽기 전용으로 조회
  useEffect(() => {
    if (!graphId || !selectedNode) {
      setNodeComments([])
      return
    }
    axios
      .get<NodeCommentView[]>(`/api/graphs/${graphId}/nodes/${selectedNode.id}/comments`)
      .then((res) => setNodeComments(res.data))
      .catch(() => setNodeComments([]))
  }, [graphId, selectedNode])

  // 계층형 ↔ 도메인 뷰 전환
  const toggleLayoutPreset = () => {
    const next: LayoutPreset = layoutPreset === 'layer' ? 'domain' : 'layer'
    setLayoutPreset(next)
    setOpaqueLayerSet(new Set())
    setOpaqueDomainSet(new Set())
    setActiveDomainTab('전체')
    if (rawNodesCache.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodesCache, rawEdgesCache, labelMode, next)
      setBuiltEdgesCache(le)
      setNodes(ln)
      const { se, sc, si, sb, sdb, sapi } = edgeVisibility
      setEdges(applyEdgeVisibility(le, se, sc, si, sb, sdb, sapi))
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = () => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodesCache.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodesCache, rawEdgesCache, next, layoutPreset)
      setBuiltEdgesCache(le)
      setNodes(ln)
      const { se, sc, si, sb, sdb, sapi } = edgeVisibility
      setEdges(applyEdgeVisibility(le, se, sc, si, sb, sdb, sapi))
    }
  }

  // 엣지 타입 토글 — 레이아웃 재계산 없이 캐시된 빌드 결과에 가시성만 재적용
  const toggleEdgeType = (key: keyof typeof edgeVisibility) => {
    setEdgeVisibility((prev) => {
      const next = { ...prev, [key]: !prev[key] }
      setEdges(applyEdgeVisibility(builtEdgesCache, next.se, next.sc, next.si, next.sb, next.sdb, next.sapi))
      return next
    })
  }

  // 레이어 섹션 opaque 토글 — 섹션 덮기 + 내부 파일/함수 노드 hidden (layer 모드 전용)
  const toggleLayerOpaque = (layer: string) => {
    setOpaqueLayerSet((prev) => {
      const next = new Set(prev)
      if (next.has(layer)) next.delete(layer)
      else next.add(layer)
      setNodes((nds) => applyOpaqueLayerSet(nds, next))
      return next
    })
  }

  // 도메인 섹션 opaque 토글 — 섹션 덮기 + 내부 파일/함수 노드 hidden (domain 모드 전용)
  const toggleDomainOpaque = (domain: string) => {
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
  }

  // 도메인/레이어 탭 활성화 — 상단 탭바 클릭과 동일 동작(필터 + 화면 맞춤), 범례 라벨 클릭에서도 재사용
  const activateTab = (tab: string) => {
    setActiveDomainTab(tab)
    setTimeout(() => fitView({ duration: 400, padding: 0.15 }), 50)
  }

  // 도메인/레이어 탭 목록 — sectionNode 라벨에서 추출
  const availableTabs = useMemo(() => {
    const sections = nodes.filter(n => n.type === 'sectionNode').map(n => String(n.data?.label ?? ''))
    return ['전체', ...Array.from(new Set(sections)).sort()]
  }, [nodes])

  // 도메인 섹션 키+색상 목록
  const domainSections = useMemo(() =>
    nodes.filter(n => n.id.startsWith('domain-section-')).map(n => ({
      key: n.id.replace('domain-section-', ''),
      color: String(n.data?.color ?? '#6b7280'),
    })),
    [nodes]
  )

  // 레이어 섹션 키+라벨+색상 목록
  const layerSections = useMemo(() =>
    nodes.filter(n => n.id.startsWith('layer-section-')).map(n => ({
      key: n.id.replace('layer-section-', ''),
      label: String(n.data?.label ?? ''),
      color: String(n.data?.color ?? '#6b7280'),
    })),
    [nodes]
  )

  // 탭 필터링된 노드 ID 집합
  const tabFilteredNodeIds = useMemo(() => {
    if (activeDomainTab === '전체') return null
    const sectionNode = nodes.find(n => n.type === 'sectionNode' && String(n.data?.label ?? '') === activeDomainTab)
    if (!sectionNode) return null
    const childIds = new Set<string>([sectionNode.id])
    nodes.forEach(n => { if (n.parentId === sectionNode.id) childIds.add(n.id) })
    nodes.forEach(n => { if (n.parentId && childIds.has(n.parentId)) childIds.add(n.id) })
    nodes.forEach(n => { if (n.parentId && childIds.has(n.parentId)) childIds.add(n.id) })
    return childIds
  }, [activeDomainTab, nodes])

  const displayNodes = useMemo(() =>
    tabFilteredNodeIds ? nodes.filter(n => tabFilteredNodeIds.has(n.id)) : nodes,
    [nodes, tabFilteredNodeIds]
  )
  const focusedNodeId = selectedNode?.id ?? null

  const displayEdges = useMemo(() => {
    const baseEdges = tabFilteredNodeIds
      ? edges.filter(e => tabFilteredNodeIds.has(e.source) && tabFilteredNodeIds.has(e.target))
      : edges
    if (focusedNodeId) {
      const connectedEdges = edges.filter(e => e.source === focusedNodeId || e.target === focusedNodeId)
      const combined = [...baseEdges]
      connectedEdges.forEach(e => { if (!combined.find(b => b.id === e.id)) combined.push(e) })
      return combined
    }
    return baseEdges
  }, [edges, tabFilteredNodeIds, focusedNodeId])

  // 인덱스에 표시할 노드 — 검색어 있으면 원본(RawNode) 대상, 없으면 현재 화면에 보이는 노드
  const indexItems = nodeSearch.trim()
    ? searchNodes(rawNodesCache, nodeSearch).map(n => ({
        id: n.id,
        icon: n.type === 'FILE' ? '📄' : n.type === 'FUNCTION' ? 'ƒ' : n.type === 'DB_TABLE' ? '🗄' : '◎',
        label: n.name,
      }))
    : nodes
        .filter(n => n.type !== 'sectionNode' && n.type !== 'groupNode' && !n.hidden)
        .map(n => ({
          id: n.id,
          icon: n.type === 'fileNode' ? '📄' : n.type === 'DB_TABLE' ? '🗄' : 'ƒ',
          label: String(n.data?.name ?? n.id),
        }))

  if (loading) {
    return (
      <div className="app-page min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">{error}</p>
        <button onClick={() => navigate('/community')} className="text-sm underline text-gray-400">
          커뮤니티로
        </button>
      </div>
    )
  }

  return (
    <div className="app-page w-screen h-screen bg-gray-950 flex flex-col">
      {/* 상단 배너 */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800 shrink-0">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/community')} className="font-bold text-white text-sm hover:text-gray-300">
            Codeprint
          </button>
          <span className="text-gray-500 text-xs">커뮤니티 공유 그래프</span>
        </div>
        <div className="flex items-center gap-3">
          <LayoutPresetToggle layoutPreset={layoutPreset} onToggle={toggleLayoutPreset} />
          <LabelModeToggle labelMode={labelMode} onToggle={toggleLabelMode} />
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">

        {/* 좌측 사이드바 접힘 */}
        {!leftOpen && (
          <div className="w-6 shrink-0 bg-gray-950 border-r border-gray-800 flex items-start justify-center pt-3">
            <button
              onClick={() => setLeftOpen(true)}
              title="사이드바 펼치기"
              className="w-5 h-5 flex items-center justify-center rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white text-xs leading-none"
            >
              ›
            </button>
          </div>
        )}

        {/* 좌측 사이드바 */}
        {leftOpen && (
        <aside className="relative shrink-0 bg-gray-950 border-r border-gray-800 flex flex-col overflow-y-auto" style={{ width: `${leftWidth}px` }}>
          <div className="flex items-center justify-end px-2 py-1.5 border-b border-gray-800/60 shrink-0">
            <button
              onClick={() => setLeftOpen(false)}
              title="사이드바 접기"
              className="w-6 h-6 flex items-center justify-center rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white text-sm leading-none"
            >
              ‹
            </button>
          </div>
          <div
            onMouseDown={startLeftResize}
            className="absolute top-0 right-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />

          {/* 노드 검색 */}
          <div className="px-3 py-3 border-b border-gray-800/60 flex flex-col gap-2">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">노드 검색</p>
            <input
              value={nodeSearch}
              onChange={e => setNodeSearch(e.target.value)}
              placeholder="파일명 / 함수명 검색..."
              className="w-full text-xs bg-gray-800 border border-gray-700 rounded px-2 py-1 text-gray-200 placeholder-gray-600 focus:outline-none focus:border-gray-500"
            />
            <div className="flex flex-col gap-0.5 max-h-64 overflow-y-auto">
              {indexItems.length === 0 ? (
                <p className="text-[10px] text-gray-600 px-1">결과 없음</p>
              ) : (
                indexItems.map(n => (
                  <button
                    key={n.id}
                    onClick={() => handleFocusNode(n.id)}
                    className={`w-full text-left text-[11px] px-2 py-1 rounded hover:bg-gray-700 truncate transition-colors ${selectedNode?.id === n.id ? 'bg-gray-700 text-white' : 'text-gray-300'}`}
                    title={n.label}
                  >
                    <span className="text-gray-600 mr-1">{n.icon}</span>
                    {n.label}
                  </button>
                ))
              )}
            </div>
          </div>

          {/* 범례 — 도메인/레이어 다중 표시 토글 + 라벨 클릭으로 필터 */}
          {availableTabs.length > 2 && (
            <div className="px-3 py-3 border-b border-gray-800/60 flex flex-col gap-2">
              {layoutPreset === 'domain' && (
                <GraphLegend
                  headerText="범례 (○ 클릭 = 가리기, 이름 클릭 = 해당 도메인만 보기)"
                  entries={domainSections.map(({ key, color }) => ({ key, label: key.charAt(0).toUpperCase() + key.slice(1), color }))}
                  opaqueSet={opaqueDomainSet}
                  onToggleOpaque={toggleDomainOpaque}
                  isActive={(entry) => activeDomainTab === entry.label}
                  onLabelClick={(entry) => activateTab(entry.label)}
                  labelTitle="이 도메인만 보기"
                  onReset={() => activateTab('전체')}
                  resetActive={activeDomainTab === '전체'}
                />
              )}
              {layoutPreset === 'layer' && (
                <GraphLegend
                  headerText="범례 (○ 클릭 = 가리기, 이름 클릭 = 해당 레이어만 보기)"
                  entries={layerSections}
                  opaqueSet={opaqueLayerSet}
                  onToggleOpaque={toggleLayerOpaque}
                  isActive={(entry) => activeDomainTab === entry.label}
                  onLabelClick={(entry) => activateTab(entry.label)}
                  labelTitle="이 레이어만 보기"
                  onReset={() => activateTab('전체')}
                  resetActive={activeDomainTab === '전체'}
                />
              )}
            </div>
          )}

          {/* 엣지 타입 토글 — 구조 파악 목적 */}
          <div className="px-3 py-3 border-b border-gray-800/60 flex flex-col gap-2">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">엣지</p>
            <div className="grid grid-cols-2 gap-x-1 gap-y-0.5">
              {EDGE_TOGGLE_DEFS.map(({ key, label }) => {
                const active = edgeVisibility[key]
                return (
                  <button
                    key={key}
                    onClick={() => toggleEdgeType(key)}
                    className={`text-left text-xs px-1.5 py-1 rounded hover:bg-gray-800/60 transition-colors ${active ? 'text-gray-300' : 'text-gray-600 opacity-60'}`}
                  >
                    {label}
                  </button>
                )
              })}
            </div>
          </div>
        </aside>
        )}

        {/* 그래프 캔버스 */}
        <div className="flex-1 h-full flex flex-col min-w-0">
          {/* 도메인/레이어 탭바 */}
          {availableTabs.length > 2 && (
            <div className="flex items-center gap-0.5 px-2 py-1 bg-gray-950/90 border-b border-gray-800 overflow-x-auto shrink-0">
              <button
                onClick={() => activateTab('전체')}
                className={`flex-shrink-0 text-xs px-3 py-1 rounded transition-colors ${
                  activeDomainTab === '전체'
                    ? 'bg-blue-700/60 text-blue-200 border border-blue-600/60'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                }`}
              >
                전체
              </button>
              {availableTabs.filter(t => t !== '전체').map(tab => (
                <button
                  key={tab}
                  onClick={() => activateTab(tab)}
                  className={`flex-shrink-0 text-xs px-3 py-1 rounded transition-colors ${
                    activeDomainTab === tab
                      ? 'bg-blue-600/30 text-blue-300 border border-blue-600/40'
                      : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          )}
          <div className="flex-1 relative">
            <ReactFlow
              nodes={displayNodes}
              edges={displayEdges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              nodeTypes={nodeTypes}
              fitView
              minZoom={GRAPH_MIN_ZOOM}
              maxZoom={GRAPH_MAX_ZOOM}
              onlyRenderVisibleElements
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable={false}
              onNodeClick={handleNodeClick}
              onPaneClick={() => setSelectedNode(null)}
            >
              <Background color="#374151" gap={20} />
              <Controls />
              <MiniMap nodeColor="#6b7280" maskColor="rgba(17,24,39,0.7)" />
            </ReactFlow>

            {/* 코너 플로팅 — 경고 (우측 하단, 기본 접힘, 읽기 전용) */}
            <CornerPanel
              open={warningPanelOpen}
              onOpen={() => setWarningPanelOpen(true)}
              onClose={() => setWarningPanelOpen(false)}
              icon="🔎"
              title="경고"
              count={warnings.length}
              panelClassName="w-72 max-h-[60vh]"
              style={{ right: '16px' }}
              headerExtra={warnings.length > 0 ? (
                <button onClick={() => downloadWarningsMd(warnings)} title="경고 마크다운 내보내기" className="text-gray-500 hover:text-gray-300 text-[10px] px-1.5 py-0.5 rounded hover:bg-gray-800">↓ MD</button>
              ) : undefined}
            >
              {warnings.length === 0 ? (
                <p className="text-[11px] text-gray-500 px-1 pt-1">감지된 구조 경고가 없습니다.</p>
              ) : (
                <WarningPanel warnings={warnings} onNodeNavigate={handleFocusNode} />
              )}
            </CornerPanel>
          </div>
        </div>

        {/* 우측 사이드바 접힘 */}
        {!rightOpen && (
          <div className="w-6 shrink-0 bg-gray-900 border-l border-gray-800 flex items-start justify-center pt-3">
            <button
              onClick={() => setRightOpen(true)}
              title="사이드바 펼치기"
              className="w-5 h-5 flex items-center justify-center rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white text-xs leading-none"
            >
              ‹
            </button>
          </div>
        )}

        {/* 우측 사이드바 — 노드 상세 (읽기 전용) */}
        {rightOpen && (
        <aside className="relative shrink-0 bg-gray-900 border-l border-gray-800 flex flex-col overflow-hidden" style={{ width: `${rightWidth}px` }}>
          <div
            onMouseDown={startRightResize}
            className="absolute top-0 left-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />

          <div className="flex flex-col overflow-hidden flex-1">
            <div className="px-3 py-2.5 border-b border-gray-800 shrink-0 flex items-center justify-between">
              <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">노드 정보</span>
              <div className="flex items-center gap-1.5">
                {selectedNode && (
                  <button onClick={() => setSelectedNode(null)} className="text-gray-600 hover:text-gray-300 text-xs">✕</button>
                )}
                <button
                  onClick={() => setRightOpen(false)}
                  title="사이드바 접기"
                  className="w-6 h-6 flex items-center justify-center rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-400 hover:text-white text-sm leading-none"
                >
                  ›
                </button>
              </div>
            </div>

            {selectedNode ? (
              <div className="p-3 flex flex-col gap-2.5 overflow-y-auto">
                <p className="text-sm font-medium text-white break-words leading-snug">
                  {String(selectedNode.data?.name ?? selectedNode.id)}
                </p>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] text-gray-500 w-10 shrink-0">타입</span>
                    <span className="text-xs font-mono bg-gray-800 text-blue-300 px-1.5 py-0.5 rounded">
                      {NODE_TYPE_LABEL[selectedNode.type ?? ''] ?? selectedNode.type ?? '-'}
                    </span>
                  </div>
                  {!!selectedNode.data?.domain && String(selectedNode.data.domain) !== 'common' && (
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] text-gray-500 w-10 shrink-0">도메인</span>
                      <span className="text-xs text-gray-300">{String(selectedNode.data.domain)}</span>
                    </div>
                  )}
                  {!!selectedNode.data?.filePath && (
                    <div className="flex items-start gap-2">
                      <span className="text-[10px] text-gray-500 w-10 shrink-0 mt-0.5">경로</span>
                      <span className="text-[10px] text-gray-500 break-all font-mono">{String(selectedNode.data.filePath)}</span>
                    </div>
                  )}
                  {!!selectedNode.data?.comment && (
                    <div className="flex items-start gap-2">
                      <span className="text-[10px] text-gray-500 w-10 shrink-0 mt-0.5">설명</span>
                      <span className="text-xs text-gray-300 break-words">{String(selectedNode.data.comment)}</span>
                    </div>
                  )}
                </div>

                {/* 노드 코멘트 — 읽기 전용(작성·삭제는 GraphPage 소유자 전용) */}
                <div className="border-t border-gray-800 pt-2.5 flex flex-col gap-1.5">
                  <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wider">코멘트 {nodeComments.length > 0 && `(${nodeComments.length})`}</p>
                  {nodeComments.length === 0 ? (
                    <p className="text-[11px] text-gray-600">코멘트가 없습니다.</p>
                  ) : (
                    nodeComments.map((c) => (
                      <div key={c.id} className="text-xs text-gray-300 bg-gray-800/60 rounded px-2 py-1.5 break-words">
                        {c.content}
                      </div>
                    ))
                  )}
                </div>
              </div>
            ) : (
              <div className="p-3 flex-1 flex items-center justify-center">
                <p className="text-xs text-gray-600 text-center">노드를 클릭하면<br />상세 정보가 표시됩니다.</p>
              </div>
            )}
          </div>
        </aside>
        )}
      </div>
    </div>
  )
}

// ReactFlowProvider로 감싼 커뮤니티 게시글 그래프 페이지 — position 파라미터 유무로 신규/레거시 분기
export default function CommunityPostGraphPage() {
  const { position } = useParams<{ position?: string }>()
  return (
    <ReactFlowProvider>
      {position !== undefined ? <CommunityPostSnapshotInner /> : <CommunityPostGraphInner />}
    </ReactFlowProvider>
  )
}

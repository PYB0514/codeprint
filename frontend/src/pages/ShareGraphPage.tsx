// 공개 프로젝트 읽기 전용 그래프 뷰어 (비인증 접근 허용)
// ⚠️ GraphPage.tsx에 새 "보기"(필터·조회·전환) 기능이 추가되면 여기도 반영 검토 — 저장/수정 액션(프리셋 저장·코멘트·suppress 등)만 GraphPage 전용, 보기는 동등해야 함(2026-07-02 결정, 백로그: PROGRESS.md "ShareGraphPage 뷰어 기능 확장")
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { useSidebarResize } from '../hooks/useSidebarResize'
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
import { buildLayout, applyEdgeVisibility, searchNodes, downloadWarningsMd, GRAPH_MIN_ZOOM, GRAPH_MAX_ZOOM } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset } from '../utils/graphLayout'
import type { Node, Edge } from '@xyflow/react'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'
import WarningPanel from '../components/WarningPanel'
import { LayoutPresetToggle, LabelModeToggle } from '../components/GraphViewToggles'
import { GraphLegend } from '../components/GraphLegend'
import { CornerPanel } from '../components/CornerPanel'
import { FlowPlaybackPanel } from '../components/FlowPlaybackPanel'
import { useFlowPlayback } from '../hooks/useFlowPlayback'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

// layer 모드에서 opaque 섹션의 자손 노드 hidden 처리
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

// 공개 프로젝트 그래프를 읽기 전용으로 표시하는 페이지
function ShareGraphInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { fitView } = useReactFlow()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [warnings, setWarnings] = useState<{ type: string; nodeIds: string[]; message: string }[]>([])
  // 대형 레포 절단 안내 — 전체 대상 파일 수 > 분석된 파일 수일 때 표시
  const [truncation, setTruncation] = useState<{ analyzed: number; total: number } | null>(null)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [nodeSearch, setNodeSearch] = useState('')
  const [warningPanelOpen, setWarningPanelOpen] = useState(false)
  const [leftOpen, setLeftOpen] = useState(true)
  const [rightOpen, setRightOpen] = useState(true)
  const { width: leftWidth, startResize: startLeftResize } = useSidebarResize(224, 160, 420, 'left')
  const { width: rightWidth, startResize: startRightResize } = useSidebarResize(256, 240, 520, 'right')
  const [activeDomainTab, setActiveDomainTab] = useState<string>('전체')
  const [ownerBgUrl, setOwnerBgUrl] = useState<string | null>(null)
  const [bgEnabled, setBgEnabled] = useState(false)
  const [rawNodesCache, setRawNodesCache] = useState<RawNode[]>([])
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])
  const [layoutPreset, setLayoutPreset] = useState<LayoutPreset>('layer')
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [edgeVisibility, setEdgeVisibility] = useState({ se: false, sc: false, si: false, sb: true, sdb: false, sapi: true })
  const [opaqueLayerSet, setOpaqueLayerSet] = useState<Set<string>>(new Set())
  const [opaqueDomainSet, setOpaqueDomainSet] = useState<Set<string>>(new Set())

  // 흐름 재생 — 종료 시 레이아웃 재빌드로 기본 엣지 스타일 복원 (dasharray 등 타입별 스타일 보존)
  const restorePlaybackEdgeStyles = useCallback(() => {
    const { edges: rebuilt } = buildLayout(rawNodesCache, rawEdgesCache, labelMode, layoutPreset)
    const { se, sc, si, sb, sdb, sapi } = edgeVisibility
    return applyEdgeVisibility(rebuilt, se, sc, si, sb, sdb, sapi)
  }, [rawNodesCache, rawEdgesCache, labelMode, layoutPreset, edgeVisibility])

  const {
    callTree, playbackItems, playbackCursor, playbackPlaying, activePath, pendingBranchNodeId, playbackRootNodeId,
    setPlaybackCursor, setPlaybackPlaying, setPendingBranchNodeId,
    startPlayback, resetPlayback, selectBranchImmediate, confirmBranch,
  } = useFlowPlayback({
    rawNodes: rawNodesCache, rawEdges: rawEdgesCache, setNodes, setEdges, getNodes: () => nodes, fitView,
    restoreEdgeStyles: restorePlaybackEdgeStyles,
  })

  // 노드 클릭 시 우측 사이드바 표시 + 함수/DB 노드는 흐름 재생 시작
  const handleNodeClick: NodeMouseHandler = (_, node) => {
    if (node.type === 'sectionNode') return
    if (node.type === 'groupNode') { resetPlayback(); return }
    setSelectedNode(node)
    if (node.type === 'fileNode') { resetPlayback(); return }
    const raw = rawNodesCache.find(n => n.id === node.id)
    if (raw && (raw.type === 'FUNCTION' || raw.type === 'API_ENDPOINT' || raw.type === 'DB_TABLE')) {
      startPlayback(node.id)
    } else {
      resetPlayback()
    }
  }

  // 좌측 노드 목록 클릭 시 해당 노드로 이동
  const handleFocusNode = (nodeId: string) => {
    fitView({ nodes: [{ id: nodeId }], duration: 400, padding: 0.3 })
    const target = nodes.find(n => n.id === nodeId)
    if (target) setSelectedNode(target)
  }

  useEffect(() => {
    if (!projectId) return

    const presetSlot = searchParams.get('preset')
    const userId = searchParams.get('userId')
    const hasPreset = presetSlot && userId

    const graphPromise = axios.get(`/api/share/${projectId}/graph`)
    const presetPromise = hasPreset
      ? axios.get(`/api/share/${projectId}/presets/${presetSlot}?userId=${userId}`).catch(() => null)
      : Promise.resolve(null)

    Promise.all([graphPromise, presetPromise])
      .then(([graphRes, presetRes]) => {
        const raw = graphRes.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[]; warnings?: { type: string; nodeIds: string[]; message: string }[]; ownerBgUrl?: string | null; analyzedFileCount?: number; totalFileCount?: number }
        if (raw.warnings) setWarnings(raw.warnings)
        if (raw.ownerBgUrl) setOwnerBgUrl(raw.ownerBgUrl)
        // 500개 초과 절단 시에만 안내 (기존 그래프는 카운트 없음)
        if (raw.totalFileCount != null && raw.analyzedFileCount != null && raw.totalFileCount > raw.analyzedFileCount) {
          setTruncation({ analyzed: raw.analyzedFileCount, total: raw.totalFileCount })
        }

        const cfg = (presetRes?.data?.config ?? {}) as Record<string, unknown>
        const lp = (cfg.layoutPreset as LayoutPreset) ?? 'layer'
        const lm = (cfg.labelMode as LabelMode) ?? 'name'
        const edgeCfg = (cfg.edges as Record<string, boolean>) ?? {}
        const se  = edgeCfg.import ?? false
        const sc  = edgeCfg.call   ?? false
        const si  = edgeCfg.inst   ?? false
        const sb  = edgeCfg.broken ?? true
        const sdb = edgeCfg.db     ?? false
        const sapi = edgeCfg.api   ?? true
        const initialOpaqueLayerSet = new Set((cfg.opaqueLayerSet as string[]) ?? [])

        const { nodes: builtNodes, edges: builtEdges } = buildLayout(raw.nodes, raw.edges, lm, lp)

        const finalNodes = lp === 'domain'
          ? builtNodes
          : applyOpaqueLayerSet(builtNodes, initialOpaqueLayerSet)

        setRawNodesCache(raw.nodes)
        setRawEdgesCache(raw.edges)
        setLayoutPreset(lp)
        setLabelMode(lm)
        setEdgeVisibility({ se, sc, si, sb, sdb, sapi })
        setOpaqueLayerSet(initialOpaqueLayerSet)
        setNodes(finalNodes)
        setEdges(applyEdgeVisibility(builtEdges, se, sc, si, sb, sdb, sapi))
        setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)
      })
      .catch(() => setError('프로젝트를 찾을 수 없거나 비공개 상태입니다.'))
      .finally(() => setLoading(false))
  }, [projectId, searchParams, fitView])

  // bgEnabled 또는 ownerBgUrl 변화 시 body 배경이미지 동기화
  useEffect(() => {
    if (bgEnabled && ownerBgUrl) {
      document.body.style.backgroundImage = `url(${ownerBgUrl})`
      document.body.classList.add('has-bg')
    } else {
      document.body.classList.remove('has-bg')
    }
    return () => { document.body.classList.remove('has-bg') }
  }, [bgEnabled, ownerBgUrl])

  // 계층형 ↔ 도메인 뷰 전환
  const toggleLayoutPreset = () => {
    const next: LayoutPreset = layoutPreset === 'layer' ? 'domain' : 'layer'
    setLayoutPreset(next)
    setOpaqueLayerSet(new Set())
    setOpaqueDomainSet(new Set())
    setActiveDomainTab('전체')
    if (rawNodesCache.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodesCache, rawEdgesCache, labelMode, next)
      setNodes(ln)
      const { se, sc, si, sb, sdb, sapi } = edgeVisibility
      setEdges(applyEdgeVisibility(le, se, sc, si, sb, sdb, sapi))
      setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 50)
    }
  }

  // 도메인/레이어 탭 활성화 — 상단 탭바 클릭과 동일 동작(필터 + 화면 맞춤), 범례 라벨 클릭에서도 재사용
  const activateTab = (tab: string) => {
    setActiveDomainTab(tab)
    setTimeout(() => fitView({ duration: 400, padding: 0.15 }), 50)
  }

  // 노드 라벨 표시 모드를 이름/주석 간 전환
  const toggleLabelMode = () => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodesCache.length > 0) {
      const { nodes: ln, edges: le } = buildLayout(rawNodesCache, rawEdgesCache, next, layoutPreset)
      setNodes(ln)
      const { se, sc, si, sb, sdb, sapi } = edgeVisibility
      setEdges(applyEdgeVisibility(le, se, sc, si, sb, sdb, sapi))
    }
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

  // 도메인/레이어 탭 목록 — sectionNode 라벨에서 추출
  const availableTabs = useMemo(() => {
    const sections = nodes.filter(n => n.type === 'sectionNode').map(n => String(n.data?.label ?? ''))
    return ['전체', ...Array.from(new Set(sections)).sort()]
  }, [nodes])

  // 도메인 섹션 키+색상 목록(소문자, id 기준) — 범례 opaque 토글은 라벨이 아닌 이 키로 매칭, 색상은 섹션 노드 실제 색상 재사용
  const domainSections = useMemo(() =>
    nodes.filter(n => n.id.startsWith('domain-section-')).map(n => ({
      key: n.id.replace('domain-section-', ''),
      color: String(n.data?.color ?? '#6b7280'),
    })),
    [nodes]
  )

  // 레이어 섹션 키+라벨+색상 목록 — 고정 DDD 8종 목록 대신 실제 렌더된 섹션에서 동적으로 파생(비DDD 프로젝트도 커버)
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
      : []
    if (focusedNodeId) {
      const connectedEdges = edges.filter(e => e.source === focusedNodeId || e.target === focusedNodeId)
      const combined = [...baseEdges]
      connectedEdges.forEach(e => { if (!combined.find(b => b.id === e.id)) combined.push(e) })
      return combined
    }
    return baseEdges
  }, [edges, tabFilteredNodeIds, focusedNodeId])

  // 인덱스에 표시할 노드 — 검색어 있으면 원본(RawNode) 대상으로 GraphPage와 동일하게 매치(주석 포함, 최대 10개)
  // 검색어 없으면 현재 화면에 보이는(hidden 아닌) 노드 전체를 훑어보기 목록으로 표시
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
        <button onClick={() => navigate('/')} className="text-sm underline text-gray-400">홈으로</button>
      </div>
    )
  }

  return (
    <div className="app-page w-screen h-screen bg-gray-950 flex flex-col">
      {/* 상단 배너 */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800 shrink-0">
        <div className="flex items-center gap-3">
          <span className="font-bold text-white text-sm">Codeprint</span>
          <span className="text-gray-500 text-xs">읽기 전용</span>
        </div>
        <div className="flex items-center gap-3">
          <LayoutPresetToggle layoutPreset={layoutPreset} onToggle={toggleLayoutPreset} />
          <LabelModeToggle labelMode={labelMode} onToggle={toggleLabelMode} />
          <span className="text-gray-400 text-xs">공유된 그래프</span>
          <button
            onClick={() => navigate('/')}
            className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
          >
            로그인하기
          </button>
        </div>
      </div>

      {/* 대형 레포 절단 안내 */}
      {truncation && (
        <div className="flex items-center justify-between px-4 py-1.5 bg-orange-900/80 border-b border-orange-700 text-orange-300 text-xs shrink-0">
          <span>
            📦 대형 레포 — 전체 <strong>{truncation.total.toLocaleString()}</strong>개 파일 중 <strong>{truncation.analyzed.toLocaleString()}</strong>개만 분석되었습니다. 그래프는 일부 구조만 표시합니다.
          </span>
          <button onClick={() => setTruncation(null)} className="text-orange-500 hover:text-orange-200 ml-4">✕</button>
        </div>
      )}

      {/* 본문 */}
      <div className="flex-1 flex overflow-hidden">

        {/* 좌측 사이드바 접힘 — 얇은 스트립 + 펼치기 버튼만 표시 */}
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
          {/* 좌측 사이드바 리사이즈 핸들 */}
          <div
            onMouseDown={startLeftResize}
            className="absolute top-0 right-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />

          {/* 노드 검색 섹션 */}
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

          {/* 범례 — 도메인/레이어 다중 표시 토글 + 라벨 클릭으로 필터(상단 탭바와 동일 동작) */}
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

          {/* 배경이미지 토글 — 오너 배경이 있을 때만 표시 */}
          {ownerBgUrl && (
            <div className="px-3 py-3 border-b border-gray-800/60 flex flex-col gap-2">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">보기</p>
              <button
                onClick={() => setBgEnabled(v => !v)}
                className={`w-full text-left text-xs px-2 py-1.5 rounded transition-colors ${
                  bgEnabled
                    ? 'bg-blue-900/40 text-blue-300 hover:bg-blue-900/60'
                    : 'bg-gray-800/60 text-gray-500 hover:bg-gray-800'
                }`}
              >
                {bgEnabled ? '🖼 배경이미지 켜짐' : '□ 배경이미지 꺼짐'}
              </button>
            </div>
          )}
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

          {/* 코너 플로팅 — 경고 (우측 하단, 기본 접힘, GraphPage와 동일 패턴) */}
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
              <WarningPanel warnings={warnings} />
            )}
          </CornerPanel>
          </div>
        </div>

        {/* 우측 사이드바 접힘 — 얇은 스트립 + 펼치기 버튼만 표시 */}
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

        {/* 우측 사이드바 */}
        {rightOpen && (
        <aside className="relative shrink-0 bg-gray-900 border-l border-gray-800 flex flex-col overflow-hidden" style={{ width: `${rightWidth}px` }}>
          {/* 우측 사이드바 리사이즈 핸들 */}
          <div
            onMouseDown={startRightResize}
            className="absolute top-0 left-0 h-full w-1 cursor-col-resize hover:bg-blue-500/40 active:bg-blue-500/60 transition-colors"
            style={{ userSelect: 'none' }}
          />

          {/* 노드 상세 */}
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

            <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-3">
            <FlowPlaybackPanel
              callTree={callTree}
              playbackItems={playbackItems}
              playbackCursor={playbackCursor}
              playbackPlaying={playbackPlaying}
              activePath={activePath}
              pendingBranchNodeId={pendingBranchNodeId}
              playbackRootNodeId={playbackRootNodeId}
              rawNodes={rawNodesCache}
              setPlaybackCursor={setPlaybackCursor}
              setPlaybackPlaying={setPlaybackPlaying}
              setPendingBranchNodeId={setPendingBranchNodeId}
              resetPlayback={resetPlayback}
              selectBranchImmediate={selectBranchImmediate}
              confirmBranch={confirmBranch}
              startPlayback={startPlayback}
            />
            {selectedNode ? (
              <div className="flex flex-col gap-2.5">
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
              </div>
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-xs text-gray-600 text-center">노드를 클릭하면<br />상세 정보가 표시됩니다.</p>
              </div>
            )}
            </div>
          </div>
        </aside>
        )}

      </div>
    </div>
  )
}

// ReactFlowProvider로 감싼 공유 그래프 페이지
export default function ShareGraphPage() {
  return (
    <ReactFlowProvider>
      <ShareGraphInner />
    </ReactFlowProvider>
  )
}

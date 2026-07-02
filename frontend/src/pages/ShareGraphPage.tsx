// 공개 프로젝트 읽기 전용 그래프 뷰어 (비인증 접근 허용)
// ⚠️ GraphPage.tsx에 새 "보기"(필터·조회·전환) 기능이 추가되면 여기도 반영 검토 — 저장/수정 액션(프리셋 저장·코멘트·suppress 등)만 GraphPage 전용, 보기는 동등해야 함(2026-07-02 결정, 백로그: PROGRESS.md "ShareGraphPage 뷰어 기능 확장")
import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { useGraphChat } from '../hooks/useGraphChat'
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
import { buildLayout } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode, LayoutPreset } from '../utils/graphLayout'
import type { Node, Edge } from '@xyflow/react'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'
import WarningPanel from '../components/WarningPanel'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

// DB 엣지 타입 판별
function isDbEdgeType(t: string | undefined): boolean {
  return t === 'DB_READ' || t === 'DB_WRITE' || t === 'DB_CREATE' || t === 'DB_UPDATE' || t === 'DB_DELETE'
}

// 엣지 타입별 hidden 여부 적용
function applyEdgeVisibility(
  edges: Edge[],
  se: boolean, sc: boolean, si: boolean, sb: boolean, sdb: boolean, sapi: boolean
): Edge[] {
  return edges.map((e) => {
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
  })
}

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
  const [graphId, setGraphId] = useState<string | null>(null)
  const [chatInput, setChatInput] = useState('')
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [nodeSearch, setNodeSearch] = useState('')
  const [showChat, setShowChat] = useState(false)
  const [warningPanelOpen, setWarningPanelOpen] = useState(false)
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
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { messages, connected, sendMessage } = useGraphChat(graphId, null)


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
        setGraphId(raw.graphId)
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

  // 새 채팅 메시지 수신 시 스크롤
  useEffect(() => {
    if (showChat) messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, showChat])

  // 채팅 메시지 전송
  const handleSendChat = (e: React.FormEvent) => {
    e.preventDefault()
    if (!chatInput.trim()) return
    sendMessage(chatInput)
    setChatInput('')
  }

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

  // 인덱스에 표시할 노드 (section/group 제외, 검색 필터)
  const indexNodes = nodes.filter(n =>
    n.type !== 'sectionNode' && n.type !== 'groupNode' && !n.hidden &&
    (nodeSearch === '' || String(n.data?.name ?? n.id).toLowerCase().includes(nodeSearch.toLowerCase()))
  )

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
          <button
            onClick={toggleLayoutPreset}
            title="레이아웃 전환"
            className="flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2.5 py-1.5 rounded-lg border border-gray-700"
          >
            <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>계층형</span>
            <span className="text-gray-600">/</span>
            <span className={layoutPreset === 'domain' ? 'text-white' : 'text-gray-500'}>도메인</span>
          </button>
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

        {/* 좌측 사이드바 */}
        <aside className="w-56 shrink-0 bg-gray-950 border-r border-gray-800 flex flex-col overflow-y-auto">

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
              {indexNodes.length === 0 ? (
                <p className="text-[10px] text-gray-600 px-1">결과 없음</p>
              ) : (
                indexNodes.map(n => (
                  <button
                    key={n.id}
                    onClick={() => handleFocusNode(n.id)}
                    className={`w-full text-left text-[11px] px-2 py-1 rounded hover:bg-gray-700 truncate transition-colors ${selectedNode?.id === n.id ? 'bg-gray-700 text-white' : 'text-gray-300'}`}
                    title={String(n.data?.name ?? n.id)}
                  >
                    <span className="text-gray-600 mr-1">
                      {n.type === 'fileNode' ? '📄' : n.type === 'DB_TABLE' ? '🗄' : 'ƒ'}
                    </span>
                    {String(n.data?.name ?? n.id)}
                  </button>
                ))
              )}
            </div>
          </div>

          {/* 범례 — 도메인/레이어 다중 표시 토글 */}
          {availableTabs.length > 2 && (
            <div className="px-3 py-3 border-b border-gray-800/60 flex flex-col gap-2">
              {layoutPreset === 'domain' && (
                <>
                  <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">범례 (클릭 = 가리기)</p>
                  <div className="grid grid-cols-2 gap-x-2 gap-y-0.5">
                    {domainSections.map(({ key, color }) => {
                      const opaque = opaqueDomainSet.has(key)
                      const label = key.charAt(0).toUpperCase() + key.slice(1)
                      return (
                        <div key={key} className="flex items-center gap-1.5 py-0.5 px-1 rounded">
                          <button
                            onClick={() => toggleDomainOpaque(key)}
                            title={opaque ? '내용 표시' : '내용 가리기'}
                            style={{
                              width: 16, height: 16, borderRadius: 3,
                              border: `1px solid ${color}88`,
                              background: opaque ? color : `${color}22`,
                              color: opaque ? '#fff' : color,
                              fontSize: 9, cursor: 'pointer',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              flexShrink: 0,
                            }}
                          >
                            {opaque ? '◑' : '○'}
                          </button>
                          <span className="text-xs truncate text-gray-400 flex-1 min-w-0">{label}</span>
                        </div>
                      )
                    })}
                  </div>
                </>
              )}
              {layoutPreset === 'layer' && (
                <>
                  <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">범례 (클릭 = 가리기)</p>
                  <div className="grid grid-cols-2 gap-x-2 gap-y-0.5">
                    {[
                      { label: 'Domain',         key: 'domain',         color: '#3b82f6' },
                      { label: 'Application',    key: 'application',    color: '#eab308' },
                      { label: 'Infrastructure', key: 'infrastructure', color: '#a855f7' },
                      { label: 'Interfaces',     key: 'interfaces',     color: '#10b981' },
                      { label: 'Pages',          key: 'pages',          color: '#06b6d4' },
                      { label: 'Components',     key: 'components',     color: '#0ea5e9' },
                      { label: 'Hooks / Utils',  key: 'hooks',          color: '#f97316' },
                      { label: 'Database',       key: 'database',       color: '#ef4444' },
                    ].filter(({ label }) => availableTabs.includes(label)).map(({ label, key, color }) => {
                      const opaque = opaqueLayerSet.has(key)
                      return (
                        <div key={key} className="flex items-center gap-1.5 py-0.5 px-1 rounded">
                          <button
                            onClick={() => toggleLayerOpaque(key)}
                            title={opaque ? '내용 표시' : '내용 가리기'}
                            style={{
                              width: 16, height: 16, borderRadius: 3,
                              border: `1px solid ${color}88`,
                              background: opaque ? color : `${color}22`,
                              color: opaque ? '#fff' : color,
                              fontSize: 9, cursor: 'pointer',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              flexShrink: 0,
                            }}
                          >
                            {opaque ? '◑' : '○'}
                          </button>
                          <span className="text-xs truncate text-gray-400 flex-1 min-w-0">{label}</span>
                        </div>
                      )
                    })}
                  </div>
                </>
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

        {/* 그래프 캔버스 */}
        <div className="flex-1 h-full flex flex-col min-w-0">
          {/* 도메인/레이어 탭바 */}
          {availableTabs.length > 2 && (
            <div className="flex items-center gap-0.5 px-2 py-1 bg-gray-950/90 border-b border-gray-800 overflow-x-auto shrink-0">
              <button
                onClick={() => { setActiveDomainTab('전체'); setTimeout(() => fitView({ duration: 400, padding: 0.15 }), 50) }}
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
                  onClick={() => { setActiveDomainTab(tab); setTimeout(() => fitView({ duration: 400, padding: 0.15 }), 50) }}
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
          <div className="absolute z-30 bottom-4 right-4">
            {warningPanelOpen ? (
              <div className="w-72 max-h-[60vh] flex flex-col bg-gray-950/95 border border-gray-800 rounded-xl shadow-2xl backdrop-blur-sm overflow-hidden">
                <div className="flex items-center justify-between px-3 py-2 border-b border-gray-800 flex-shrink-0">
                  <span className="text-xs font-semibold text-gray-300">🔎 경고{warnings.length > 0 && <span className="ml-1 text-yellow-400">({warnings.length})</span>}</span>
                  <button onClick={() => setWarningPanelOpen(false)} title="접기" className="text-gray-500 hover:text-gray-200 text-xs w-5 h-5 flex items-center justify-center rounded hover:bg-gray-800">▾</button>
                </div>
                <div className="p-2 overflow-y-auto">
                  {warnings.length === 0 ? (
                    <p className="text-[11px] text-gray-500 px-1 pt-1">감지된 구조 경고가 없습니다.</p>
                  ) : (
                    <WarningPanel warnings={warnings} />
                  )}
                </div>
              </div>
            ) : (
              <button onClick={() => setWarningPanelOpen(true)} className="flex items-center gap-2 bg-gray-900/90 hover:bg-gray-800 border border-gray-700 text-gray-200 text-sm font-medium px-4 py-2.5 rounded-xl shadow-lg backdrop-blur-sm">
                <span className="text-base">🔎</span> 경고
                {warnings.length > 0 && <span className="text-xs px-2 py-0.5 rounded-full bg-yellow-900/60 text-yellow-300 border border-yellow-700/50">{warnings.length}</span>}
                <span className="text-gray-500 text-xs">▴</span>
              </button>
            )}
          </div>
          </div>
        </div>

        {/* 우측 사이드바 */}
        <aside className="w-64 shrink-0 bg-gray-900 border-l border-gray-800 flex flex-col overflow-hidden">

          {/* 노드 상세 */}
          <div className="flex flex-col overflow-hidden" style={{ flex: selectedNode ? '0 0 auto' : '1' }}>
            <div className="px-3 py-2.5 border-b border-gray-800 shrink-0 flex items-center justify-between">
              <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">노드 정보</span>
              {selectedNode && (
                <button onClick={() => setSelectedNode(null)} className="text-gray-600 hover:text-gray-300 text-xs">✕</button>
              )}
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
              </div>
            ) : (
              <div className="p-3 flex-1 flex items-center justify-center">
                <p className="text-xs text-gray-600 text-center">노드를 클릭하면<br />상세 정보가 표시됩니다.</p>
              </div>
            )}
          </div>

          {/* 채팅 */}
          <div className="flex flex-col border-t border-gray-800" style={{ flex: showChat ? '1' : '0 0 auto', overflow: 'hidden' }}>
            <button
              onClick={() => setShowChat(v => !v)}
              className="flex items-center justify-between px-3 py-2.5 hover:bg-gray-800 transition-colors shrink-0"
            >
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">💬 채팅</span>
                {connected && <span className="w-1.5 h-1.5 rounded-full bg-green-400" />}
              </div>
              <span className="text-gray-600 text-xs">{showChat ? '▲' : '▼'}</span>
            </button>

            {showChat && (
              <>
                <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2 min-h-0">
                  {messages.length === 0 && (
                    <p className="text-xs text-gray-600 text-center mt-4">
                      아직 메시지가 없습니다.<br />첫 메시지를 보내보세요.
                    </p>
                  )}
                  {messages.map((msg, i) => (
                    <div key={i} className="flex flex-col gap-0.5">
                      <span className="text-[10px] text-gray-500">{msg.username}</span>
                      <span className="text-xs text-white bg-gray-800 rounded-lg px-2.5 py-1.5 break-words">{msg.message}</span>
                    </div>
                  ))}
                  <div ref={messagesEndRef} />
                </div>
                <form onSubmit={handleSendChat} className="p-2 border-t border-gray-800 flex gap-1.5 shrink-0">
                  <input
                    value={chatInput}
                    onChange={e => setChatInput(e.target.value)}
                    placeholder="메시지 입력..."
                    className="flex-1 bg-gray-800 text-white text-xs px-2.5 py-1.5 rounded border border-gray-700 focus:outline-none focus:border-gray-500 placeholder-gray-600"
                    maxLength={500}
                  />
                  <button
                    type="submit"
                    disabled={!connected || !chatInput.trim()}
                    className="text-xs bg-blue-600 text-white px-2.5 py-1.5 rounded hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    전송
                  </button>
                </form>
              </>
            )}
          </div>
        </aside>

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

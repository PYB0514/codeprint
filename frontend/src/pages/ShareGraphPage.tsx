// 공개 프로젝트 읽기 전용 그래프 뷰어 (비인증 접근 허용)
import { useEffect, useRef, useState } from 'react'
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

// layer 모드에서 opaque 섹션의 자손 노드(group→file→function 3단계) hidden 처리
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

// 공개 프로젝트 그래프를 읽기 전용으로 표시하는 페이지
function ShareGraphInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [warnings, setWarnings] = useState<{ type: string; nodeIds: string[]; message: string }[]>([])
  const [graphId, setGraphId] = useState<string | null>(null)
  const [showChat, setShowChat] = useState(false)
  const [chatInput, setChatInput] = useState('')
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { messages, connected, sendMessage } = useGraphChat(graphId, null)

  // 노드 클릭 시 상세 패널 표시
  const handleNodeClick: NodeMouseHandler = (_, node) => setSelectedNode(node)

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
        const raw = graphRes.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[]; warnings?: { type: string; nodeIds: string[]; message: string }[] }
        if (raw.warnings) setWarnings(raw.warnings)
        setGraphId(raw.graphId)

        // 프리셋 config 파싱 (없으면 기본값)
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
        const opaqueLayerSet = new Set((cfg.opaqueLayerSet as string[]) ?? [])

        const { nodes: builtNodes, edges: builtEdges } = buildLayout(raw.nodes, raw.edges, lm, lp)

        // domain 모드는 별도 처리 없음 / layer 모드: opaqueLayerSet 처리
        const finalNodes = lp === 'domain'
          ? builtNodes
          : applyOpaqueLayerSet(builtNodes, opaqueLayerSet)

        setNodes(finalNodes)
        setEdges(applyEdgeVisibility(builtEdges, se, sc, si, sb, sdb, sapi))
      })
      .catch(() => setError('프로젝트를 찾을 수 없거나 비공개 상태입니다.'))
      .finally(() => setLoading(false))
  }, [projectId, searchParams])

  // 새 메시지 수신 시 채팅 패널 하단으로 자동 스크롤
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

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">{error}</p>
        <button onClick={() => navigate('/')} className="text-sm underline text-gray-400">
          홈으로
        </button>
      </div>
    )
  }

  return (
    <div className="w-screen h-screen bg-gray-950 flex flex-col">
      {/* 상단 읽기 전용 배너 */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800 shrink-0">
        <div className="flex items-center gap-3">
          <span className="font-bold text-white text-sm">Codeprint</span>
          <span className="text-gray-500 text-xs">읽기 전용</span>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowChat(v => !v)}
            className={`text-xs px-3 py-1 rounded-lg border transition-colors ${
              showChat
                ? 'bg-blue-600 text-white border-blue-600'
                : 'text-gray-400 border-gray-700 hover:border-gray-500 hover:text-white'
            }`}
          >
            💬 채팅{connected && showChat ? ' •' : ''}
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

      {/* 본문 */}
      <div className="flex-1 flex overflow-hidden">
        {/* 경고 패널 (경고 있을 때만) */}
        {warnings.length > 0 && (
          <div className="w-64 shrink-0 bg-gray-900 border-r border-gray-800 overflow-y-auto p-3 flex flex-col gap-2">
            <div className="text-xs font-semibold text-yellow-400 flex items-center gap-1.5">
              <span>⚠</span>
              <span>런타임 경고 ({warnings.length})</span>
            </div>
            <WarningPanel warnings={warnings} />
          </div>
        )}

        {/* 채팅 패널 */}
        {showChat && (
          <div className="w-72 shrink-0 bg-gray-900 border-l border-gray-800 flex flex-col order-last">
            <div className="px-3 py-2 border-b border-gray-800 flex items-center justify-between">
              <span className="text-xs font-semibold text-white">채팅</span>
              <span className={`text-xs ${connected ? 'text-green-400' : 'text-gray-500'}`}>
                {connected ? '연결됨' : '연결 중...'}
              </span>
            </div>
            <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
              {messages.length === 0 && (
                <p className="text-xs text-gray-600 text-center mt-4">
                  아직 메시지가 없습니다.
                  <br />첫 메시지를 보내보세요.
                </p>
              )}
              {messages.map((msg, i) => (
                <div key={i} className="flex flex-col gap-0.5">
                  <span className="text-xs text-gray-500">{msg.username}</span>
                  <span className="text-sm text-white bg-gray-800 rounded-lg px-2.5 py-1.5 break-words">
                    {msg.message}
                  </span>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>
            <form onSubmit={handleSendChat} className="p-3 border-t border-gray-800 flex gap-2">
              <input
                value={chatInput}
                onChange={e => setChatInput(e.target.value)}
                placeholder="메시지 입력..."
                className="flex-1 bg-gray-800 text-white text-sm px-3 py-1.5 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500 placeholder-gray-600"
                maxLength={500}
              />
              <button
                type="submit"
                disabled={!connected || !chatInput.trim()}
                className="text-xs bg-blue-600 text-white px-3 py-1.5 rounded-lg hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                전송
              </button>
            </form>
          </div>
        )}

        {/* 그래프 */}
        <div className="flex-1 h-full relative">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            fitView
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

          {/* 노드 상세 사이드바 */}
          {selectedNode && selectedNode.type !== 'sectionNode' && selectedNode.type !== 'groupNode' && (
            <div className="absolute top-3 right-3 w-64 bg-gray-900 border border-gray-700 rounded-lg shadow-2xl z-10 overflow-hidden">
              <div className="flex items-center justify-between px-3 py-2 border-b border-gray-700">
                <span className="text-xs font-semibold text-white truncate flex-1 mr-2">
                  {String(selectedNode.data?.name ?? selectedNode.id)}
                </span>
                <button
                  onClick={() => setSelectedNode(null)}
                  className="text-gray-500 hover:text-white text-sm leading-none shrink-0"
                >
                  ✕
                </button>
              </div>
              <div className="p-3 flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs text-gray-500 w-12 shrink-0">타입</span>
                  <span className="text-xs font-mono bg-gray-800 text-blue-300 px-2 py-0.5 rounded">
                    {selectedNode.type === 'fileNode' ? 'FILE' : 'FUNCTION'}
                  </span>
                </div>
                {!!selectedNode.data?.domain && String(selectedNode.data.domain) !== 'common' && (
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-500 w-12 shrink-0">도메인</span>
                    <span className="text-xs text-gray-300">{String(selectedNode.data.domain)}</span>
                  </div>
                )}
                {!!selectedNode.data?.comment && (
                  <div className="flex items-start gap-2">
                    <span className="text-xs text-gray-500 w-12 shrink-0 mt-0.5">설명</span>
                    <span className="text-xs text-gray-300 break-words">{String(selectedNode.data.comment)}</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
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

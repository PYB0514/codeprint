// 두 그래프 버전의 변경 사항을 색상으로 시각화하는 diff 페이지
import { useEffect, useState, useCallback } from 'react'
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
import type { Node, Edge } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { buildLayout } from '../utils/graphLayout'
import type { RawNode, RawEdge } from '../utils/graphLayout'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import AppHeader from '../components/AppHeader'
import FileNode from '../components/FileNode'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

// diff 상태 → 노드 테두리/배경 색상
const STATUS_STYLE: Record<string, { border: string; bg: string }> = {
  added:     { border: '#22c55e', bg: 'rgba(34,197,94,0.15)' },
  removed:   { border: '#ef4444', bg: 'rgba(239,68,68,0.15)' },
  unchanged: { border: '#4b5563', bg: 'transparent' },
}

// diff 상태 → 엣지 색상
const EDGE_STATUS_COLOR: Record<string, string> = {
  added:     '#22c55e',
  removed:   '#ef4444',
  unchanged: '#4b5563',
}

interface GraphVersion {
  graphId: string
  createdAt: string
  branch: string
}

interface DiffSummary {
  added: number
  removed: number
  unchanged: number
}

interface RawDiffNode extends RawNode {
  status: 'added' | 'removed' | 'unchanged'
}

interface RawDiffEdge extends RawEdge {
  status: 'added' | 'removed' | 'unchanged'
  sourceName: string
  targetName: string
}

// diff 상태에 따라 노드 스타일을 오버레이
function applyDiffStyles(nodes: Node[], diffNodes: RawDiffNode[]): Node[] {
  const statusMap = new Map(diffNodes.map((n) => [n.id, n.status]))
  return nodes.map((n) => {
    const status = statusMap.get(n.id)
    if (!status || n.type === 'groupNode' || n.type === 'sectionNode') return n
    const s = STATUS_STYLE[status]
    return {
      ...n,
      style: {
        ...n.style,
        border: `2px solid ${s.border}`,
        backgroundColor: s.bg,
        borderRadius: 6,
      },
    }
  })
}

// diff 상태에 따라 엣지 색상을 오버레이
function applyDiffEdgeStyles(edges: Edge[], diffEdges: RawDiffEdge[]): Edge[] {
  const statusMap = new Map(diffEdges.map((e) => [e.edgeIdentifier, e.status]))
  return edges.map((e): Edge => {
    const identifier = (e.data as { edgeIdentifier?: string } | undefined)?.edgeIdentifier
    const status = identifier ? statusMap.get(identifier) : undefined
    if (!status) return e
    const color = EDGE_STATUS_COLOR[status]
    return {
      ...e,
      style: { ...e.style, stroke: color, strokeWidth: status === 'unchanged' ? 1 : 2 },
      markerEnd: typeof e.markerEnd === 'object' && e.markerEnd !== null
        ? { ...e.markerEnd, color }
        : e.markerEnd,
      animated: status === 'added',
    }
  })
}

// diff 그래프 내부 컴포넌트
function DiffPageInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [versions, setVersions] = useState<GraphVersion[]>([])
  const [fromId, setFromId] = useState('')
  const [toId, setToId] = useState('')
  const [summary, setSummary] = useState<DiffSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { fitView } = useReactFlow()

  // 버전 목록 로드
  useEffect(() => {
    axios.get(`/api/projects/${projectId}/graphs`)
      .then((res) => {
        const list: GraphVersion[] = res.data
        setVersions(list)
        if (list.length >= 2) {
          setToId(list[0].graphId)
          setFromId(list[1].graphId)
        } else if (list.length === 1) {
          setToId(list[0].graphId)
        }
      })
      .catch(() => setError('버전 목록을 불러오지 못했습니다.'))
  }, [projectId])

  // diff 불러오기
  const fetchDiff = useCallback(async () => {
    if (!fromId || !toId || fromId === toId) return
    setLoading(true)
    setError(null)
    try {
      const res = await axios.get(
        `/api/projects/${projectId}/diff?from=${fromId}&to=${toId}`
      )
      const diffNodes: RawDiffNode[] = res.data.nodes
      const diffEdges: RawDiffEdge[] = res.data.edges
      setSummary(res.data.summary)

      // buildLayout에 넘길 때 removed 노드도 포함 (회색 처리를 위해)
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(diffNodes, diffEdges, 'name', 'layer', () => {})
      const styled = applyDiffStyles(layoutNodes, diffNodes)
      const styledEdges = applyDiffEdgeStyles(layoutEdges, diffEdges)
      setNodes(styled)
      setEdges(styledEdges)
      setTimeout(() => fitView({ padding: 0.1 }), 100)
    } catch {
      setError('diff를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, fromId, toId, fitView, setNodes, setEdges])

  useEffect(() => { fetchDiff() }, [fromId, toId])

  // 버전 레이블 — branch + 날짜
  const versionLabel = (v: GraphVersion) =>
    `${v.branch} · ${new Date(v.createdAt).toLocaleDateString('ko-KR')}`

  return (
    <div className="app-page flex flex-col h-screen bg-gray-950 text-white">
      <AppHeader />
      {/* 서브 바 — 그래프 복귀 + 버전 선택 */}
      <header className="flex items-center gap-4 px-5 py-3 border-b border-gray-800 shrink-0">
        <button
          onClick={() => navigate(`/projects/${projectId}/graph`)}
          className="text-gray-400 hover:text-white text-sm transition-colors"
        >
          ← 그래프로
        </button>
        <span className="font-semibold">버전 비교</span>

        {/* From 선택 */}
        <div className="flex items-center gap-2 ml-auto">
          <span className="text-xs text-gray-500">기준</span>
          <select
            value={fromId}
            onChange={(e) => setFromId(e.target.value)}
            className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm focus:outline-none"
          >
            {versions.map((v) => (
              <option key={v.graphId} value={v.graphId}>{versionLabel(v)}</option>
            ))}
          </select>

          <span className="text-gray-500">→</span>

          {/* To 선택 */}
          <span className="text-xs text-gray-500">비교</span>
          <select
            value={toId}
            onChange={(e) => setToId(e.target.value)}
            className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm focus:outline-none"
          >
            {versions.map((v) => (
              <option key={v.graphId} value={v.graphId}>{versionLabel(v)}</option>
            ))}
          </select>
        </div>

        {/* 요약 배지 */}
        {summary && (
          <div className="flex items-center gap-3 ml-4 text-xs font-medium">
            <span className="text-green-400">+{summary.added} 추가</span>
            <span className="text-red-400">-{summary.removed} 삭제</span>
            <span className="text-gray-500">{summary.unchanged} 유지</span>
          </div>
        )}
      </header>

      {/* 범례 */}
      <div className="flex items-center gap-4 px-5 py-2 border-b border-gray-800 text-xs text-gray-400 shrink-0">
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded border-2 border-green-500 bg-green-500/15" />
          <span>추가됨</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded border-2 border-red-500 bg-red-500/15" />
          <span>삭제됨</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded border-2 border-gray-600" />
          <span>변경 없음</span>
        </div>
        <div className="flex items-center gap-1.5 ml-2">
          <div className="w-5 h-0.5 bg-green-500" />
          <span>추가된 연결</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-5 h-0.5 bg-red-500" />
          <span>삭제된 연결</span>
        </div>
      </div>

      {/* 그래프 영역 */}
      <div className="flex-1 relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center z-10 bg-gray-950/70">
            <span className="text-gray-400 text-sm">비교 중…</span>
          </div>
        )}
        {error && (
          <div className="absolute inset-0 flex items-center justify-center z-10">
            <span className="text-red-400 text-sm">{error}</span>
          </div>
        )}
        {fromId === toId && (
          <div className="absolute inset-0 flex items-center justify-center z-10">
            <span className="text-gray-500 text-sm">기준과 비교 버전을 다르게 선택하세요.</span>
          </div>
        )}
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          fitView
          minZoom={0.05}
          maxZoom={2}
        >
          <Background color="#1f2937" gap={24} />
          <Controls />
          <MiniMap nodeColor={() => '#374151'} maskColor="rgba(0,0,0,0.6)" />
        </ReactFlow>
      </div>
    </div>
  )
}

// diff 페이지 루트 — ReactFlowProvider 래핑
export default function DiffPage() {
  return (
    <ReactFlowProvider>
      <DiffPageInner />
    </ReactFlowProvider>
  )
}

// 커뮤니티 게시글에 첨부된 그래프 — 숨김 필터 적용 읽기 전용 뷰어
import { useEffect, useState } from 'react'
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
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { buildLayout, getGroupKey, findCommonPrefix } from '../utils/graphLayout'
import type { RawNode, RawEdge } from '../utils/graphLayout'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'

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

// 숨김 필터를 적용하여 노드를 걸러내는 함수
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

// 게시글 첨부 그래프 뷰어 내부 컴포넌트
function CommunityPostGraphInner() {
  const { postId } = useParams<{ postId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
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

        const { nodes: builtNodes, edges: builtEdges } = buildLayout(filteredNodes, filteredEdges, 'hierarchical', 'name')
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
      <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">{error}</p>
        <button onClick={() => navigate('/community')} className="text-sm underline text-gray-400">
          커뮤니티로
        </button>
      </div>
    )
  }

  return (
    <div className="w-screen h-screen bg-gray-950 flex flex-col">
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

// ReactFlowProvider로 감싼 커뮤니티 게시글 그래프 페이지
export default function CommunityPostGraphPage() {
  return (
    <ReactFlowProvider>
      <CommunityPostGraphInner />
    </ReactFlowProvider>
  )
}

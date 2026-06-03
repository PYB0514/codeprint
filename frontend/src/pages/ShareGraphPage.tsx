// 공개 프로젝트 읽기 전용 그래프 뷰어 (비인증 접근 허용)
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
import { buildLayout } from '../utils/graphLayout'
import type { RawNode, RawEdge } from '../utils/graphLayout'
import type { Node, Edge } from '@xyflow/react'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'

const nodeTypes = { groupNode: GroupNode, sectionNode: SectionNode, fileNode: FileNode }

// 공개 프로젝트 그래프를 읽기 전용으로 표시하는 페이지
function ShareGraphInner() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!projectId) return
    axios
      .get(`/api/share/${projectId}/graph`)
      .then((res) => {
        const raw = res.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }
        const { nodes: builtNodes, edges: builtEdges } = buildLayout(raw.nodes, raw.edges, 'name', 'layer')
        setNodes(builtNodes)
        setEdges(builtEdges)
      })
      .catch(() => setError('프로젝트를 찾을 수 없거나 비공개 상태입니다.'))
      .finally(() => setLoading(false))
  }, [projectId])

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
          <span className="text-gray-400 text-xs">공유된 그래프</span>
          <button
            onClick={() => navigate('/')}
            className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
          >
            로그인하기
          </button>
        </div>
      </div>

      {/* 그래프 */}
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

// ReactFlowProvider로 감싼 공유 그래프 페이지
export default function ShareGraphPage() {
  return (
    <ReactFlowProvider>
      <ShareGraphInner />
    </ReactFlowProvider>
  )
}

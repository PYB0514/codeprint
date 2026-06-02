// 프로젝트 코드 구조를 React Flow로 시각화하는 그래프 페이지
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
} from '@xyflow/react'
import type { Node, Edge } from '@xyflow/react'
import '@xyflow/react/dist/style.css'

interface RawNode {
  id: string
  type: string
  name: string
  filePath: string
  language: string
  posX: number
  posY: number
}

interface RawEdge {
  id: string
  type: string
  source: string
  target: string
  edgeIdentifier: string
}

const NODE_COLORS: Record<string, string> = {
  FILE: '#3b82f6',
  FUNCTION: '#10b981',
  DB_TABLE: '#f59e0b',
  API_ENDPOINT: '#8b5cf6',
}

function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

function layoutNodes(rawNodes: RawNode[]): Node[] {
  const COLS = 6
  const COL_GAP = 220
  const ROW_GAP = 120

  return rawNodes.map((n, i) => ({
    id: n.id,
    position: {
      x: (i % COLS) * COL_GAP,
      y: Math.floor(i / COLS) * ROW_GAP,
    },
    data: {
      label: (
        <div style={{ fontSize: 11, textAlign: 'center' }}>
          <div style={{ fontSize: 9, opacity: 0.6, marginBottom: 2 }}>{n.type}</div>
          <div style={{ fontWeight: 600 }}>{n.name}</div>
          {n.language && <div style={{ fontSize: 9, opacity: 0.5 }}>{n.language}</div>}
        </div>
      ),
    },
    style: {
      background: NODE_COLORS[n.type] ?? '#6b7280',
      color: '#fff',
      border: 'none',
      borderRadius: 8,
      padding: '6px 10px',
      minWidth: 120,
    },
  }))
}

export default function GraphPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { nodes: rawNodes, edges: rawEdges } = res.data as { nodes: RawNode[]; edges: RawEdge[] }

      setNodes(layoutNodes(rawNodes))
      setEdges(
        rawEdges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          label: e.type,
          style: { stroke: '#6b7280' },
          labelStyle: { fontSize: 9, fill: '#9ca3af' },
        } as Edge))
      )
    } catch {
      setError('그래프를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, setNodes, setEdges])

  useEffect(() => {
    fetchGraph()
  }, [fetchGraph])

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
        <button onClick={() => navigate('/dashboard')} className="underline text-sm">
          대시보드로
        </button>
      </div>
    )
  }

  return (
    <div style={{ width: '100vw', height: '100vh', background: '#030712' }}>
      <div className="absolute top-4 left-4 z-10 flex items-center gap-3">
        <button
          onClick={() => navigate('/dashboard')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          ← 대시보드
        </button>
        <span className="text-gray-400 text-sm">
          노드 {nodes.length}개 · 엣지 {edges.length}개
        </span>
      </div>

      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        fitView
        fitViewOptions={{ padding: 0.2 }}
      >
        <Background color="#1f2937" gap={20} />
        <Controls />
        <MiniMap
          nodeColor={(n) => (n.style?.background as string) ?? '#6b7280'}
          style={{ background: '#111827' }}
        />
      </ReactFlow>
    </div>
  )
}

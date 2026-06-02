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
import type { Edge, EdgeMouseHandler } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { buildLayout } from '../utils/graphLayout'
import type { RawNode, RawEdge, LabelMode } from '../utils/graphLayout'

interface EdgeModalInfo {
  edgeIdentifier: string
  type: string
  sourceId: string
  targetId: string
}

function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

export default function GraphPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [rawNodes, setRawNodes] = useState<RawNode[]>([])
  const [counts, setCounts] = useState({ files: 0, funcs: 0, edges: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [edgeModal, setEdgeModal] = useState<EdgeModalInfo | null>(null)
  const [labelMode, setLabelMode] = useState<LabelMode>('name')
  const [rawEdgesCache, setRawEdgesCache] = useState<RawEdge[]>([])

  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`/api/projects/${projectId}/graph`, { headers: authHeaders() })
      const { nodes: rn, edges: re } = res.data as { nodes: RawNode[]; edges: RawEdge[] }
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rn, re, labelMode)
      setRawNodes(rn)
      setRawEdgesCache(re)
      setNodes(layoutNodes)
      setEdges(layoutEdges)
      setCounts({
        files: rn.filter((n) => n.type === 'FILE').length,
        funcs: rn.filter((n) => n.type === 'FUNCTION').length,
        edges: re.length,
      })
    } catch {
      setError('그래프를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [projectId, setNodes, setEdges])

  useEffect(() => { fetchGraph() }, [fetchGraph])

  const toggleLabelMode = useCallback(() => {
    const next: LabelMode = labelMode === 'name' ? 'comment' : 'name'
    setLabelMode(next)
    if (rawNodes.length > 0) {
      const { nodes: layoutNodes, edges: layoutEdges } = buildLayout(rawNodes, rawEdgesCache, next)
      setNodes(layoutNodes)
      setEdges(layoutEdges)
    }
  }, [labelMode, rawNodes, rawEdgesCache, setNodes, setEdges])

  const handleEdgeClick: EdgeMouseHandler<Edge> = useCallback((_event, edge) => {
    const data = edge.data as { edgeIdentifier?: string; type?: string } | undefined
    const sourceNode = rawNodes.find((n) => n.id === edge.source)
    const targetNode = rawNodes.find((n) => n.id === edge.target)
    setEdgeModal({
      edgeIdentifier: data?.edgeIdentifier ?? edge.id,
      type: data?.type ?? 'IMPORT',
      sourceId: sourceNode?.name ?? edge.source,
      targetId: targetNode?.name ?? edge.target,
    })
  }, [rawNodes])

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
    <div style={{ width: '100vw', height: '100vh', background: '#030712' }}>
      {/* 상단 바 */}
      <div className="absolute top-4 left-4 z-10 flex items-center gap-3">
        <button
          onClick={() => navigate('/dashboard')}
          className="bg-gray-800 hover:bg-gray-700 text-white text-sm px-3 py-1.5 rounded-lg"
        >
          ← 대시보드
        </button>
        <span className="text-gray-400 text-sm">
          파일 {counts.files}개 · 함수 {counts.funcs}개 · 엣지 {counts.edges}개
        </span>
        <button
          onClick={toggleLabelMode}
          className="flex items-center gap-1.5 bg-gray-800 hover:bg-gray-700 text-sm px-3 py-1.5 rounded-lg border border-gray-700"
        >
          <span className={labelMode === 'name' ? 'text-white' : 'text-gray-500'}>이름</span>
          <span className="text-gray-600">/</span>
          <span className={labelMode === 'comment' ? 'text-white' : 'text-gray-500'}>주석</span>
        </button>
      </div>

      {/* 범례 */}
      <div className="absolute top-4 right-4 z-10 bg-gray-900 rounded-lg p-3 flex flex-col gap-1.5 text-xs">
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded" style={{ background: '#1e3a5f', border: '1.5px solid #3b82f6' }} />
          <span className="text-gray-400">FILE</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded" style={{ background: '#064e3b', border: '1px solid #10b981' }} />
          <span className="text-gray-400">FUNCTION</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-0.5" style={{ background: '#4b5563' }} />
          <span className="text-gray-400">IMPORT</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-3 h-0.5" style={{ background: '#ef4444' }} />
          <span className="text-gray-400">끊긴 연결</span>
        </div>
      </div>

      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onEdgeClick={handleEdgeClick}
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
        />
      </ReactFlow>

      {/* 엣지 클릭 모달 */}
      {edgeModal && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
          onClick={() => setEdgeModal(null)}
        >
          <div
            className="bg-gray-900 rounded-2xl p-6 w-80 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-sm">연결 상세</h3>
              <button onClick={() => setEdgeModal(null)} className="text-gray-500 hover:text-white text-xs">✕</button>
            </div>
            <div className="flex flex-col gap-3 text-sm">
              <div className="flex items-center gap-2">
                <span className="text-xs bg-blue-900 text-blue-300 px-2 py-0.5 rounded">{edgeModal.type}</span>
              </div>
              <div className="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">출발</p>
                  <p className="text-white font-mono text-xs">{edgeModal.sourceId}</p>
                </div>
                <div className="text-gray-600 text-xs text-center">↓</div>
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">도착</p>
                  <p className="text-white font-mono text-xs">{edgeModal.targetId}</p>
                </div>
              </div>
              <div>
                <p className="text-xs text-gray-500 mb-0.5">식별자</p>
                <p className="text-gray-300 font-mono text-xs break-all">{edgeModal.edgeIdentifier}</p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

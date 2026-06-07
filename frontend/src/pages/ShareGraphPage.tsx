// 공개 프로젝트 읽기 전용 그래프 뷰어 (비인증 접근 허용)
import { useEffect, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
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
import type { RawNode, RawEdge, LabelMode, LayoutPreset } from '../utils/graphLayout'
import type { Node, Edge } from '@xyflow/react'
import GroupNode from '../components/GroupNode'
import SectionNode from '../components/SectionNode'
import FileNode from '../components/FileNode'

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
        const raw = graphRes.data as { graphId: string; nodes: RawNode[]; edges: RawEdge[] }

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

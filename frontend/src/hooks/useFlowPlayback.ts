// 흐름 재생(flow replay) 상태·이펙트·핸들러 — GraphPage/GraphViewerPage/CommunityPostGraphPage 공유
import { useCallback, useEffect, useRef, useState } from 'react'
import type { Node, Edge } from '@xyflow/react'
import type { RawEdge, RawNode } from '../utils/graphLayout'
import {
  type CallTreeNode,
  type PlaybackItem,
  buildCallTree,
  findTreeNode,
  findPathInTree,
  extendToDefaultLeaf,
  pruneTreeToPath,
  pathToPlaybackItems,
} from '../utils/flowPlayback'

interface UseFlowPlaybackOptions {
  rawNodes: RawNode[]
  rawEdges: RawEdge[]
  setNodes: (updater: (nodes: Node[]) => Node[]) => void
  setEdges: (updater: (edges: Edge[]) => Edge[]) => void
  getNodes: () => Node[]
  fitView: (opts: { nodes: { id: string }[]; duration?: number; padding?: number; maxZoom?: number }) => void
  restoreEdgeStyles: (edges: Edge[]) => Edge[]  // 재생 종료 시 기본 엣지 스타일 복원 (페이지별 표시 토글 반영)
  onStart?: () => void  // 재생 시작 시 부수 정리 (예: 다른 사이드바 닫기)
}

const PLAYBACK_SPEED = 1200

export function useFlowPlayback({ rawNodes, rawEdges, setNodes, setEdges, getNodes, fitView, restoreEdgeStyles, onStart }: UseFlowPlaybackOptions) {
  const [playbackItems, setPlaybackItems] = useState<PlaybackItem[]>([])
  const [playbackCursor, setPlaybackCursor] = useState(-1)
  const [playbackPlaying, setPlaybackPlaying] = useState(false)
  const playbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // 재생이 막 시작됐는지 — 클릭 직후 화면이 흐름 루트로 튀지 않도록 첫 fitView를 건너뛰는 플래그
  const playbackJustStarted = useRef(false)
  const [callTree, setCallTree] = useState<CallTreeNode | null>(null)
  const [activePath, setActivePath] = useState<{ nodeIds: string[]; edgeIds: string[]; edgeTypes: string[] }>({ nodeIds: [], edgeIds: [], edgeTypes: [] })
  // 현재 경로의 엣지 ID 집합 — 재생 중 visibility 관리용
  const playbackEdgeIdsRef = useRef<Set<string>>(new Set())
  // 대기 중인 분기 선택 (재생 버튼으로 확정)
  const [pendingBranchNodeId, setPendingBranchNodeId] = useState<string | null>(null)
  // 처음부터 재시작을 위한 루트 노드 ID
  const [playbackRootNodeId, setPlaybackRootNodeId] = useState<string | null>(null)

  // 흐름 재생 — 커서 이동 시 노드/엣지 하이라이트 적용 (노드 중심 스텝)
  useEffect(() => {
    if (playbackItems.length === 0) return

    // 커서까지 지나온 노드만 하이라이트
    const visitedNodeIds = new Set(playbackItems.slice(0, playbackCursor + 1).map((it) => it.id))
    const activeNodeId = playbackCursor >= 0 ? playbackItems[playbackCursor].id : null
    // 커서까지의 incoming 엣지 ID (현재 커서의 incoming 엣지가 "활성" 엣지)
    const visitedEdgeIds = new Set(
      playbackItems.slice(1, playbackCursor + 1).map((it) => it.incomingEdgeId).filter(Boolean) as string[]
    )
    const activeEdgeId = playbackCursor > 0 ? playbackItems[playbackCursor].incomingEdgeId : undefined

    setNodes((nds) => nds.map((n) => {
      if (!visitedNodeIds.has(n.id)) {
        return { ...n, style: { ...(n.style ?? {}), outline: 'none', boxShadow: 'none' }, data: { ...n.data, playbackActive: false, playbackInPath: false } }
      }
      const isActive = n.id === activeNodeId
      const baseStyle = n.style ?? {}
      const playbackStyle = isActive
        ? { ...baseStyle, outline: '2px solid #fbbf24', outlineOffset: '2px', boxShadow: '0 0 10px #fbbf2488' }
        : { ...baseStyle, outline: '1px solid #22d3ee66', outlineOffset: '2px' }
      return { ...n, style: playbackStyle, data: { ...n.data, playbackActive: isActive, playbackInPath: !isActive } }
    }))

    // 경로 엣지 — hidden 해제 + 방문한 엣지 강조 (표시 토글 간섭 없이 직접 제어)
    const allPathEdgeIds = playbackEdgeIdsRef.current
    setEdges((eds) => eds.map((e) => {
      if (!allPathEdgeIds.has(e.id)) return e
      const isActive = e.id === activeEdgeId
      const isVisited = visitedEdgeIds.has(e.id)
      return {
        ...e,
        hidden: false,
        animated: isActive,
        style: {
          strokeWidth: isActive ? 3.5 : isVisited ? 2 : 1,
          stroke: isActive ? '#fbbf24' : isVisited ? '#22d3ee' : '#4b5563',
        },
      }
    }))
  }, [playbackCursor, playbackItems, setNodes, setEdges])

  // 흐름 재생 — 수동/자동 스텝 이동 시 현재 노드 위치로 화면 전환
  useEffect(() => {
    if (playbackItems.length === 0 || playbackCursor < 0) return
    // 클릭으로 막 시작된 재생은 화면을 옮기지 않음 (흐름 루트로 튀는 현상 방지) — 이후 수동/자동 스텝부터 따라감
    if (playbackJustStarted.current) { playbackJustStarted.current = false; return }
    const targetId = playbackItems[playbackCursor].id
    // 현재 화면에 렌더된 노드만 대상으로 이동 (도메인 필터 밖 노드로 빈 곳에 튀는 것 방지)
    if (!getNodes().some(n => n.id === targetId)) return
    fitView({ nodes: [{ id: targetId }], duration: 300, padding: 0.5, maxZoom: 1.5 })
  }, [playbackCursor, playbackItems, fitView, getNodes])

  // 흐름 재생 — 자동 진행 타이머 (분기점에서 자동 일시정지)
  useEffect(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    if (!playbackPlaying || playbackCursor >= playbackItems.length - 1) {
      if (playbackCursor >= playbackItems.length - 1 && playbackPlaying) setPlaybackPlaying(false)
      return
    }
    // 현재 노드가 분기점이면 자동 일시정지
    if (callTree && playbackCursor >= 0) {
      const treeNode = findTreeNode(callTree, playbackItems[playbackCursor].id)
      if (treeNode && treeNode.children.length > 1) {
        setPlaybackPlaying(false)
        return
      }
    }
    playbackTimerRef.current = setTimeout(() => {
      setPlaybackCursor((c) => c + 1)
    }, PLAYBACK_SPEED)
    return () => { if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current) }
  }, [playbackPlaying, playbackCursor, playbackItems, callTree])

  // 흐름 재생 시작 — 호출 트리 빌드 후 기본 경로로 재생 시작
  const startPlayback = useCallback((nodeId: string) => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    const { tree, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes } = buildCallTree(nodeId, rawEdges, rawNodes)
    const items = pathToPlaybackItems(defaultNodeIds, defaultEdgeIds, defaultEdgeTypes, rawNodes)
    const edgeIds = new Set(defaultEdgeIds.filter(Boolean))
    playbackEdgeIdsRef.current = edgeIds
    setCallTree(tree)
    setActivePath({ nodeIds: defaultNodeIds, edgeIds: defaultEdgeIds, edgeTypes: defaultEdgeTypes })
    setPlaybackItems(items)
    playbackJustStarted.current = true
    setPlaybackCursor(0)
    setPlaybackPlaying(false)
    setPendingBranchNodeId(null)
    setPlaybackRootNodeId(nodeId)
    onStart?.()
    setEdges((eds) => eds.map((e) => edgeIds.has(e.id) ? { ...e, hidden: false } : e))
  }, [rawEdges, rawNodes, setEdges, onStart])

  // 흐름 재생 초기화
  const resetPlayback = useCallback(() => {
    if (playbackTimerRef.current) clearTimeout(playbackTimerRef.current)
    playbackEdgeIdsRef.current = new Set()
    setPlaybackPlaying(false)
    setPlaybackCursor(-1)
    setPlaybackItems([])
    setCallTree(null)
    setActivePath({ nodeIds: [], edgeIds: [], edgeTypes: [] })
    setPendingBranchNodeId(null)
    setPlaybackRootNodeId(null)
    setNodes((nds) => nds.map((n) => ({ ...n, style: { ...n.style, outline: 'none', boxShadow: 'none' }, data: { ...n.data, playbackActive: false, playbackInPath: false } })))
    setEdges((eds) => restoreEdgeStyles(eds))
  }, [setNodes, setEdges, restoreEdgeStyles])

  // 분기 즉시 확정 — 버튼 클릭 한 번으로 바로 해당 경로 재생
  const selectBranchImmediate = useCallback((nodeId: string) => {
    setPendingBranchNodeId(nodeId)
    // 다음 틱에서 확정 (state 반영 후)
    setTimeout(() => {
      if (!callTree) return
      const path = findPathInTree(callTree, nodeId)
      if (!path) return
      extendToDefaultLeaf(callTree, nodeId, path.nodeIds, path.edgeIds, path.edgeTypes)
      const items = pathToPlaybackItems(path.nodeIds, path.edgeIds, path.edgeTypes, rawNodes)
      const edgeIds = new Set(path.edgeIds.filter(Boolean))
      playbackEdgeIdsRef.current = edgeIds
      const cloneNode = (n: CallTreeNode): CallTreeNode => ({ ...n, children: n.children.map(cloneNode) })
      const prunedTree = cloneNode(callTree)
      pruneTreeToPath(prunedTree, nodeId)
      setCallTree(prunedTree)
      setActivePath(path)
      setPlaybackItems(items)
      const branchIdx = path.nodeIds.indexOf(nodeId)
      setPlaybackCursor(branchIdx >= 0 ? branchIdx : 0)
      setPlaybackPlaying(true)
      setPendingBranchNodeId(null)
      setEdges((eds) => eds.map((e) => edgeIds.has(e.id) ? { ...e, hidden: false } : e))
    }, 0)
  }, [callTree, rawNodes, setEdges])

  // 대기 중인 분기를 확정하고 해당 위치부터 자동 재생
  const confirmBranch = useCallback((nodeId: string) => {
    if (!callTree) return
    const path = findPathInTree(callTree, nodeId)
    if (!path) return
    extendToDefaultLeaf(callTree, nodeId, path.nodeIds, path.edgeIds, path.edgeTypes)
    const items = pathToPlaybackItems(path.nodeIds, path.edgeIds, path.edgeTypes, rawNodes)
    const edgeIds = new Set(path.edgeIds.filter(Boolean))
    playbackEdgeIdsRef.current = edgeIds
    // callTree 프루닝 — 선택한 분기 외 형제 제거해 auto-play 재정지 방지
    const cloneNode = (n: CallTreeNode): CallTreeNode => ({ ...n, children: n.children.map(cloneNode) })
    const prunedTree = cloneNode(callTree)
    pruneTreeToPath(prunedTree, nodeId)
    setCallTree(prunedTree)
    setActivePath(path)
    setPlaybackItems(items)
    // 분기 노드 인덱스부터 재생 — length-1 로 설정하면 이미 끝에 도달해 애니메이션이 생략됨
    const branchIdx = path.nodeIds.indexOf(nodeId)
    setPlaybackCursor(branchIdx >= 0 ? branchIdx : 0)
    setPlaybackPlaying(true)
    setPendingBranchNodeId(null)
    setEdges((eds) => eds.map((e) => edgeIds.has(e.id) ? { ...e, hidden: false } : e))
  }, [callTree, rawNodes, setEdges])

  return {
    callTree,
    playbackItems,
    playbackCursor,
    playbackPlaying,
    activePath,
    pendingBranchNodeId,
    playbackRootNodeId,
    setPlaybackCursor,
    setPlaybackPlaying,
    setPendingBranchNodeId,
    startPlayback,
    resetPlayback,
    selectBranchImmediate,
    confirmBranch,
  }
}

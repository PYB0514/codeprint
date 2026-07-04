// 흐름 재생(flow replay) 호출 트리 빌드 — GraphPage/ShareGraphPage/CommunityPostGraphPage 공유
import type { RawEdge, RawNode } from './graphLayout'

export interface CallTreeNode {
  nodeId: string
  edgeId?: string
  edgeType?: string
  label: string
  subLabel?: string   // 함수 노드일 때 소속 파일명
  nodeType?: string
  children: CallTreeNode[]
}

// 재생 스텝 = 노드 단위 (엣지 스텝 없음). incomingEdgeType은 전환 레이블용.
export interface PlaybackItem {
  id: string
  incomingEdgeId?: string
  incomingEdgeType?: string
  label?: string       // 함수 주석 또는 이름
  filePath?: string    // 소속 파일 경로
  layer?: string       // DDD 레이어 (interfaces/application/domain/infrastructure)
  nodeType?: string    // FILE / FUNCTION / DB_TABLE / API_ENDPOINT
}

export const CALL_FLOW_TYPES = ['FUNCTION_CALL', 'DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE', 'API_CALL']
const DB_FLOW_TYPES = new Set(['DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE'])

// 트리에서 nodeId 검색
export function findTreeNode(root: CallTreeNode, nodeId: string): CallTreeNode | null {
  if (root.nodeId === nodeId) return root
  for (const c of root.children) { const f = findTreeNode(c, nodeId); if (f) return f }
  return null
}

// 루트에서 targetId까지 경로 반환
export function findPathInTree(root: CallTreeNode, targetId: string): { nodeIds: string[]; edgeIds: string[]; edgeTypes: string[] } | null {
  if (root.nodeId === targetId) return { nodeIds: [root.nodeId], edgeIds: [], edgeTypes: [] }
  for (const child of root.children) {
    const r = findPathInTree(child, targetId)
    if (r) return {
      nodeIds: [root.nodeId, ...r.nodeIds],
      edgeIds: [child.edgeId ?? '', ...r.edgeIds],
      edgeTypes: [child.edgeType ?? '', ...r.edgeTypes],
    }
  }
  return null
}

// startId 노드부터 첫 번째 자식을 따라 리프까지 경로 확장
export function extendToDefaultLeaf(root: CallTreeNode, startId: string, nodeIds: string[], edgeIds: string[], edgeTypes: string[]) {
  let cur = findTreeNode(root, startId)
  while (cur && cur.children.length > 0) {
    const child = cur.children[0]
    nodeIds.push(child.nodeId)
    edgeIds.push(child.edgeId ?? '')
    edgeTypes.push(child.edgeType ?? '')
    cur = child
  }
}

// 클릭한 노드까지의 경로를 남기고 형제 분기 제거 — 클릭 노드 이후(하위)는 모두 보존
export function pruneTreeToPath(root: CallTreeNode, targetId: string): boolean {
  if (root.nodeId === targetId) return true
  for (const child of root.children) {
    if (pruneTreeToPath(child, targetId)) {
      root.children = [child]
      return true
    }
  }
  return false
}

// nodeIds/edgeIds → PlaybackItem[] 변환 — rawNodes로 레이어/라벨/파일 경로 enriching
export function pathToPlaybackItems(nodeIds: string[], edgeIds: string[], edgeTypes: string[], rawNodes?: RawNode[]): PlaybackItem[] {
  return nodeIds.map((id, i) => {
    const raw = rawNodes?.find((n) => n.id === id)
    const filePath = raw?.filePath ?? ''
    // 경로에서 레이어 추출: src/main/java/com/codeprint/{layer}/...
    const layerMatch = filePath.match(/\/(?:main|test)\/(?:java|kotlin)\/[^/]+\/[^/]+\/[^/]+\/([^/]+)/)
    const layer = layerMatch?.[1] ?? ''
    return {
      id,
      incomingEdgeId: i > 0 ? edgeIds[i - 1] : undefined,
      incomingEdgeType: i > 0 ? edgeTypes[i - 1] : undefined,
      label: raw?.comment || raw?.name || id,
      filePath,
      layer,
      nodeType: raw?.type,
    }
  })
}

// CallTreeNode 생성 헬퍼
function makeCallTreeNode(nodeId: string, rawNodes: RawNode[], edgeId?: string, edgeType?: string): CallTreeNode {
  const raw = rawNodes.find((n) => n.id === nodeId)
  const label = raw?.comment ?? raw?.name ?? nodeId
  let subLabel: string | undefined
  if (raw?.type === 'FUNCTION') {
    const file = rawNodes.find((f) => f.type === 'FILE' && f.filePath === raw.filePath)
    subLabel = file?.name
  }
  return { nodeId, edgeId, edgeType, label, subLabel, nodeType: raw?.type, children: [] }
}

// 자손 트리 재귀 빌드 — visited 공유로 중복 방지
// FUNCTION 노드가 직접 DB 엣지 없으면 소속 FILE의 DB 엣지로 폴백 (기존 그래프 호환)
function buildDownstreamTree(nodeId: string, rawEdges: RawEdge[], rawNodes: RawNode[], visited: Set<string>, depth: number): CallTreeNode[] {
  if (depth >= 12) return []
  const result: CallTreeNode[] = []
  for (const e of rawEdges) {
    if (!CALL_FLOW_TYPES.includes(e.type) || e.source !== nodeId || visited.has(e.target)) continue
    visited.add(e.target)
    const child = makeCallTreeNode(e.target, rawNodes, e.id, e.type)
    child.children = buildDownstreamTree(e.target, rawEdges, rawNodes, visited, depth + 1)
    result.push(child)
  }
  // FUNCTION 노드에 직접 DB 엣지가 없으면 소속 FILE의 DB 엣지로 폴백
  const curRaw = rawNodes.find((n) => n.id === nodeId)
  const hasDirectDb = result.some((c) => DB_FLOW_TYPES.has(c.edgeType ?? ''))
  if (curRaw?.type === 'FUNCTION' && !hasDirectDb) {
    const parentFile = rawNodes.find((f) => f.type === 'FILE' && f.filePath === curRaw.filePath)
    if (parentFile && !visited.has(parentFile.id)) {
      for (const e of rawEdges) {
        if (!DB_FLOW_TYPES.has(e.type) || e.source !== parentFile.id || visited.has(e.target)) continue
        visited.add(e.target)
        result.push(makeCallTreeNode(e.target, rawNodes, e.id, e.type))
      }
    }
  }
  return result
}

// 호출 트리 전체 빌드 — upstream 추적 후 전체 트리 생성 + 기본 경로 반환
export function buildCallTree(
  nodeId: string, rawEdges: RawEdge[], rawNodes: RawNode[],
): { tree: CallTreeNode; defaultNodeIds: string[]; defaultEdgeIds: string[]; defaultEdgeTypes: string[] } {
  // 1. FUNCTION_CALL upstream 추적 → 최상위 호출자 탐색
  const upChain: { nodeId: string; edgeId: string; edgeType: string }[] = []
  const visitedUp = new Set([nodeId])
  let cur = nodeId
  for (let i = 0; i < 12; i++) {
    const e = rawEdges.find((e) => e.type === 'FUNCTION_CALL' && e.target === cur && !visitedUp.has(e.source))
    if (!e) break
    visitedUp.add(e.source)
    upChain.unshift({ nodeId: e.source, edgeId: e.id, edgeType: e.type })
    cur = e.source
  }
  const rootFuncId = upChain.length > 0 ? upChain[0].nodeId : nodeId

  // 2. 프론트엔드 진입점 탐색 — upstream chain 전체(루트→nodeId)를 순서대로 확인
  // 가장 상위 함수 중 API_CALL을 받는 FILE이 있으면 그 파일이 컨트롤러 → 프론트가 entry
  let frontendNodeId: string | undefined
  let frontendEdgeId: string | undefined
  for (const chainNodeId of [...upChain.map((u) => u.nodeId), nodeId]) {
    const chainRaw = rawNodes.find((n) => n.id === chainNodeId && n.type === 'FUNCTION')
    if (!chainRaw) continue
    const parentFile = rawNodes.find((f) => f.type === 'FILE' && f.filePath === chainRaw.filePath)
    if (!parentFile) continue
    const apiEdge = rawEdges.find((e) => e.type === 'API_CALL' && e.target === parentFile.id)
    if (apiEdge) { frontendNodeId = apiEdge.source; frontendEdgeId = apiEdge.id; break }
  }

  // 3. 루트 함수부터 전체 트리 빌드 (shared visited로 중복 방지)
  const treeVisited = new Set([rootFuncId])
  const rootNode = makeCallTreeNode(rootFuncId, rawNodes)
  rootNode.children = buildDownstreamTree(rootFuncId, rawEdges, rawNodes, treeVisited, 0)

  // 4. 기본 경로: upstream chain → nodeId → 첫 번째 자식 따라 리프까지
  const defaultNodeIds: string[] = upChain.map((u) => u.nodeId).concat([nodeId])
  const defaultEdgeIds: string[] = upChain.map((u) => u.edgeId)
  const defaultEdgeTypes: string[] = upChain.map((u) => u.edgeType)
  extendToDefaultLeaf(rootNode, nodeId, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes)

  // 5. 프론트엔드 진입점 prepend (컨트롤러 FILE 중간 노드 생략, 직접 연결)
  if (frontendNodeId) {
    const frontendNode = makeCallTreeNode(frontendNodeId, rawNodes)
    rootNode.edgeId = frontendEdgeId; rootNode.edgeType = 'API_CALL'
    frontendNode.children = [rootNode]
    // 클릭한 노드가 루트가 아니면 경로 외 형제 분기 제거
    if (nodeId !== frontendNodeId) pruneTreeToPath(frontendNode, nodeId)
    return {
      tree: frontendNode,
      defaultNodeIds: [frontendNodeId, ...defaultNodeIds],
      defaultEdgeIds: [frontendEdgeId ?? '', ...defaultEdgeIds],
      defaultEdgeTypes: ['API_CALL', ...defaultEdgeTypes],
    }
  }
  // 클릭한 노드가 루트가 아니면 경로 외 형제 분기 제거
  if (nodeId !== rootFuncId) pruneTreeToPath(rootNode, nodeId)
  return { tree: rootNode, defaultNodeIds, defaultEdgeIds, defaultEdgeTypes }
}

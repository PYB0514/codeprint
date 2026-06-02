// 그래프 노드/엣지 레이아웃 계산 — DDD 레이어 컬럼 배치 + dagre 내부 레이아웃
import React from 'react'

import type { Node, Edge } from '@xyflow/react'
import { MarkerType } from '@xyflow/react'

// DDD 레이어 → 컬럼 순서 (왼쪽=외부진입, 오른쪽=데이터)
// 백엔드: infrastructure(0) → domain(1) → application(2) → interfaces(3)
// 프론트: pages(4) → components/hooks(5) → utils(6)
const LAYER_COLUMN: Record<string, number> = {
  infrastructure: 0,
  domain:         1,
  application:    2,
  interfaces:     3,
  pages:          4,
  components:     5,
  hooks:          5,
  utils:          6,
}

export interface RawNode {
  id: string
  type: string
  name: string
  filePath: string
  language: string
  posX: number
  posY: number
  comment?: string
}

export interface RawEdge {
  id: string
  type: string
  source: string
  target: string
  edgeIdentifier: string
}

const FUNC_W = 110
const FUNC_H = 30
const FUNC_PAD = 6
const FILE_PAD_X = 10
const FILE_PAD_TOP = 28
const FILE_PAD_BOTTOM = 10
const FILE_GAP = 20
const GROUP_PAD = 24
const GROUP_HEADER = 36  // 커스텀 헤더 높이에 맞춤
const _GROUP_GAP = 48  // 미사용 — 컬럼 레이아웃으로 교체됨
void _GROUP_GAP

// 공통 prefix 제거 후 DDD 의미 있는 그룹 키 추출
function getGroupKey(filePath: string, commonPrefix: string): string {
  const rel = filePath.startsWith(commonPrefix)
    ? filePath.slice(commonPrefix.length)
    : filePath
  const parts = rel.replace(/\\/g, '/').split('/').filter(Boolean)

  // DDD 레이어 키워드 찾기
  const layers = ['domain', 'application', 'infrastructure', 'interfaces', 'pages', 'components', 'hooks', 'utils']
  for (let i = 0; i < parts.length; i++) {
    if (layers.includes(parts[i])) {
      // 레이어 + 서브패키지 (예: domain/user, infrastructure/persistence)
      return parts[i + 1] ? `${parts[i]}/${parts[i + 1]}` : parts[i]
    }
  }
  // 레이어 못 찾으면 첫 번째 의미있는 폴더
  return parts[0] ?? 'root'
}

// 공통 prefix 계산
function findCommonPrefix(paths: string[]): string {
  if (paths.length === 0) return ''
  const parts = paths[0].replace(/\\/g, '/').split('/')
  let prefix = ''
  for (let depth = 1; depth <= parts.length; depth++) {
    const candidate = parts.slice(0, depth).join('/') + '/'
    if (paths.every((p) => p.replace(/\\/g, '/').startsWith(candidate))) {
      prefix = candidate
    } else break
  }
  return prefix
}

// 함수 수에 따라 파일 노드 박스의 폭/높이/열 수를 계산
function calcFileSize(funcCount: number): { w: number; h: number; cols: number } {
  const cols = Math.max(1, Math.min(3, funcCount === 0 ? 1 : Math.ceil(Math.sqrt(funcCount))))
  const rows = funcCount === 0 ? 0 : Math.ceil(funcCount / cols)
  const w = FILE_PAD_X * 2 + cols * FUNC_W + Math.max(0, cols - 1) * FUNC_PAD
  const h = FILE_PAD_TOP + FILE_PAD_BOTTOM + rows * FUNC_H + Math.max(0, rows - 1) * FUNC_PAD
  return { w, h, cols }
}

export type LabelMode = 'name' | 'comment'
export type LayoutPreset = 'layer' | 'hub'

// 텍스트가 maxLen을 초과하면 말줄임표 + title tooltip이 있는 span 반환, 아니면 문자열 그대로
function labelNode(full: string, maxLen: number): React.ReactNode {
  if (full.length <= maxLen) return full
  const trimmed = full.slice(0, maxLen - 1) + '…'
  return React.createElement('span', { title: full, style: { cursor: 'default' } }, trimmed)
}

// DDD 레이어 컬럼 배치 — 같은 레이어끼리 같은 컬럼에 세로 정렬
function buildLayerPositions(
  groups: Map<string, RawNode[]>,
  groupLayouts: Map<string, { files: Array<{ file: RawNode; x: number; y: number }>; w: number; h: number }>
): Map<string, { x: number; y: number }> {
  const COL_GAP = 64
  const ROW_GAP = 40

  const colGroups = new Map<number, string[]>()
  groups.forEach((_, key) => {
    const layer = key.indexOf('/') >= 0 ? key.slice(0, key.indexOf('/')) : key
    const col = LAYER_COLUMN[layer] ?? 7
    if (!colGroups.has(col)) colGroups.set(col, [])
    colGroups.get(col)!.push(key)
  })

  const sortedCols = Array.from(colGroups.keys()).sort((a, b) => a - b)
  const colMaxW = new Map<number, number>()
  colGroups.forEach((keys, col) => {
    colMaxW.set(col, Math.max(...keys.map((k) => groupLayouts.get(k)!.w)))
  })

  const colStartX = new Map<number, number>()
  let xCursor = 0
  sortedCols.forEach((col) => {
    colStartX.set(col, xCursor)
    xCursor += (colMaxW.get(col) ?? 0) + COL_GAP
  })

  const positions = new Map<string, { x: number; y: number }>()
  colGroups.forEach((keys, col) => {
    let y = 0
    const x = colStartX.get(col) ?? 0
    keys.forEach((key) => {
      positions.set(key, { x, y })
      y += groupLayouts.get(key)!.h + ROW_GAP
    })
  })
  return positions
}

// 연결 허브 배치 — 그룹 간 연결이 많을수록 중앙, 16:9 비율 그리드
function buildHubPositions(
  groups: Map<string, RawNode[]>,
  groupLayouts: Map<string, { files: Array<{ file: RawNode; x: number; y: number }>; w: number; h: number }>,
  rawEdges: RawEdge[],
  fileIdSet: Set<string>,
  fileToGroup: Map<string, string>
): { positions: Map<string, { x: number; y: number }>; isoKeys: string[] } {
  const COL_GAP = 40  // 그룹 간 가로 여백
  const ROW_GAP = 40  // 그룹 간 세로 여백

  // 그룹 간 연결 수 집계
  const connCount = new Map<string, number>()
  groups.forEach((_, k) => connCount.set(k, 0))
  rawEdges
    .filter((e) => fileIdSet.has(e.source) && fileIdSet.has(e.target))
    .forEach((e) => {
      const sg = fileToGroup.get(e.source)
      const tg = fileToGroup.get(e.target)
      if (sg && tg && sg !== tg) {
        connCount.set(sg, (connCount.get(sg) ?? 0) + 1)
        connCount.set(tg, (connCount.get(tg) ?? 0) + 1)
      }
    })

  // 연결 많은 순 정렬
  const sortedGroups = Array.from(groups.keys())
    .sort((a, b) => (connCount.get(b) ?? 0) - (connCount.get(a) ?? 0) || a.localeCompare(b))

  if (sortedGroups.length === 0) return { positions: new Map(), isoKeys: [] }

  // 연결 있는 그룹 vs 고립 그룹 분리
  const hubGroups = sortedGroups.filter((k) => (connCount.get(k) ?? 0) > 0)
  const isoGroups = sortedGroups
    .filter((k) => (connCount.get(k) ?? 0) === 0)
    .sort((a, b) => {
      const layerOf = (key: string) => {
        const layer = key.indexOf('/') >= 0 ? key.slice(0, key.indexOf('/')) : key
        return LAYER_COLUMN[layer] ?? 99
      }
      return layerOf(a) - layerOf(b) || a.localeCompare(b)
    })

  // 그리드 배치 헬퍼 — 그룹 목록을 중심부터 채우는 그리드로 배치, 기준 좌표(ox,oy) 기준 반환
  function placeGrid(keys: string[], ox: number, oy: number): Map<string, { x: number; y: number }> {
    const n = keys.length
    if (n === 0) return new Map()
    const allL = keys.map((k) => groupLayouts.get(k)!)
    const avgW2 = allL.reduce((s, l) => s + l.w, 0) / allL.length
    const avgH2 = allL.reduce((s, l) => s + l.h, 0) / allL.length
    const cols2 = Math.max(1, Math.round(Math.sqrt(n * (16 / 9) * (avgH2 / avgW2))))
    const rows2 = Math.ceil(n / cols2)

    const cx2 = (cols2 - 1) / 2, cy2 = (rows2 - 1) / 2
    const cells2: { i: number; j: number }[] = []
    for (let j = 0; j < rows2; j++)
      for (let i = 0; i < cols2; i++)
        cells2.push({ i, j })
    cells2.sort((a, b) => Math.hypot(a.i - cx2, a.j - cy2) - Math.hypot(b.i - cx2, b.j - cy2) || a.j - b.j || a.i - b.i)

    const assign = new Map<string, { i: number; j: number }>()
    keys.forEach((key, idx) => { if (idx < cells2.length) assign.set(key, cells2[idx]) })

    const colW2 = new Array(cols2).fill(0)
    const rowH2 = new Array(rows2).fill(0)
    keys.forEach((key) => {
      const cell = assign.get(key)!
      const l = groupLayouts.get(key)!
      colW2[cell.i] = Math.max(colW2[cell.i], l.w)
      rowH2[cell.j] = Math.max(rowH2[cell.j], l.h)
    })

    const colX2 = new Array(cols2).fill(0)
    for (let i = 1; i < cols2; i++) colX2[i] = colX2[i - 1] + colW2[i - 1] + COL_GAP
    const rowY2 = new Array(rows2).fill(0)
    for (let j = 1; j < rows2; j++) rowY2[j] = rowY2[j - 1] + rowH2[j - 1] + ROW_GAP

    const tw = colX2[cols2 - 1] + colW2[cols2 - 1]
    const th = rowY2[rows2 - 1] + rowH2[rows2 - 1]

    const pos = new Map<string, { x: number; y: number }>()
    keys.forEach((key) => {
      const cell = assign.get(key)!
      const l = groupLayouts.get(key)!
      pos.set(key, {
        x: ox + colX2[cell.i] + (colW2[cell.i] - l.w) / 2 - tw / 2,
        y: oy + rowY2[cell.j] + (rowH2[cell.j] - l.h) / 2 - th / 2,
      })
    })
    return pos
  }

  // 허브 그룹을 (0,0) 중심으로 배치
  const positions = placeGrid(hubGroups, 0, 0)

  // 허브 그리드 전체 높이 계산 — 고립 그룹 블록 시작 y 결정
  if (isoGroups.length > 0) {
    let hubBottom = 0
    positions.forEach((pos, key) => {
      const l = groupLayouts.get(key)!
      hubBottom = Math.max(hubBottom, pos.y + l.h)
    })
    const isoOriginY = hubBottom + ROW_GAP * 3

      // 고립 그룹은 DDD 레이어 컬럼 기반 배치 (계층 레이아웃과 동일한 방식)
    const isoColGroups = new Map<number, string[]>()
    isoGroups.forEach((key) => {
      const layer = key.indexOf('/') >= 0 ? key.slice(0, key.indexOf('/')) : key
      const col = LAYER_COLUMN[layer] ?? 99
      if (!isoColGroups.has(col)) isoColGroups.set(col, [])
      isoColGroups.get(col)!.push(key)
    })

    const isoSortedCols = Array.from(isoColGroups.keys()).sort((a, b) => a - b)
    const isoColMaxW = new Map<number, number>()
    isoColGroups.forEach((keys, col) => {
      isoColMaxW.set(col, Math.max(...keys.map((k) => groupLayouts.get(k)!.w)))
    })

    const isoColStartX = new Map<number, number>()
    let isoCursor = 0
    isoSortedCols.forEach((col) => {
      isoColStartX.set(col, isoCursor)
      isoCursor += (isoColMaxW.get(col) ?? 0) + COL_GAP
    })

    // 전체 폭 기준 중앙 정렬
    const isoTotalW2 = isoCursor - COL_GAP
    const isoOffsetX = -isoTotalW2 / 2

    isoColGroups.forEach((keys, col) => {
      let y = isoOriginY
      const x = isoOffsetX + (isoColStartX.get(col) ?? 0)
      keys.forEach((key) => {
        positions.set(key, { x, y })
        y += groupLayouts.get(key)!.h + ROW_GAP
      })
    })
  }

  return { positions, isoKeys: isoGroups }
}

// 원시 노드/엣지 데이터를 레이아웃으로 변환하여 React Flow용 노드/엣지 반환
export function buildLayout(
  rawNodes: RawNode[],
  rawEdges: RawEdge[],
  labelMode: LabelMode = 'name',
  layoutPreset: LayoutPreset = 'layer'
): { nodes: Node[]; edges: Edge[] } {
  // 노드 라벨 반환 — 초과 시 말줄임표 + hover tooltip
  const getLabel = (node: RawNode, maxLen = 999): React.ReactNode => {
    const raw = labelMode === 'comment' && node.comment ? node.comment : node.name
    return labelNode(raw, maxLen)
  }
  const fileNodes = rawNodes.filter((n) => n.type === 'FILE')
  const funcNodes = rawNodes.filter((n) => n.type === 'FUNCTION')

  const commonPrefix = findCommonPrefix(fileNodes.map((f) => f.filePath.replace(/\\/g, '/')))

  // 파일별 함수 목록
  const funcsByFile = new Map<string, RawNode[]>()
  fileNodes.forEach((f) => funcsByFile.set(f.id, []))
  funcNodes.forEach((fn) => {
    const parent = fileNodes.find((f) => f.filePath === fn.filePath)
    if (parent) funcsByFile.get(parent.id)?.push(fn)
  })

  // 파일 크기
  const fileSizes = new Map<string, ReturnType<typeof calcFileSize>>()
  fileNodes.forEach((f) => fileSizes.set(f.id, calcFileSize(funcsByFile.get(f.id)?.length ?? 0)))

  // 그룹별 파일 목록
  const groups = new Map<string, RawNode[]>()
  fileNodes.forEach((f) => {
    const key = getGroupKey(f.filePath, commonPrefix)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(f)
  })

  // 그룹 내부 레이아웃 (파일들을 그룹 안에서 그리드 배치)
  const groupLayouts = new Map<string, { files: Array<{ file: RawNode; x: number; y: number }>, w: number; h: number }>()

  groups.forEach((files, key) => {
    const COLS = 3
    let col = 0
    let colX = 0, rowY = 0
    let maxRowH = 0
    const placed: Array<{ file: RawNode; x: number; y: number }> = []
    let totalW = 0

    files.forEach((f) => {
      const size = fileSizes.get(f.id)!
      placed.push({ file: f, x: colX, y: rowY })
      totalW = Math.max(totalW, colX + size.w)  // 배치 직후 right edge 기록
      maxRowH = Math.max(maxRowH, size.h)
      col++
      if (col >= COLS) {
        col = 0
        colX = 0
        rowY += maxRowH + FILE_GAP
        maxRowH = 0
      } else {
        colX += size.w + FILE_GAP
      }
    })
    // 마지막 행
    if (col > 0) rowY += maxRowH

    const gw = GROUP_PAD * 2 + totalW
    const gh = GROUP_HEADER + GROUP_PAD + rowY + GROUP_PAD

    groupLayouts.set(key, { files: placed, w: gw, h: gh })
  })

  const fileIdSet = new Set(fileNodes.map((f) => f.id))
  const fileToGroup = new Map<string, string>()
  fileNodes.forEach((f) => fileToGroup.set(f.id, getGroupKey(f.filePath, commonPrefix)))

  // 파일별 인/아웃 연결 집계 (모달 데이터용)
  type FuncCallEntry = { callerName: string; callerLabel: string; callerNodeId: string; calleeName: string; calleeLabel: string; calleeNodeId: string }
  type ConnEntry = { nodeId: string; name: string; callChain: FuncCallEntry[] }
  const fileIncoming = new Map<string, ConnEntry[]>()
  const fileOutgoing = new Map<string, ConnEntry[]>()
  fileNodes.forEach((f) => { fileIncoming.set(f.id, []); fileOutgoing.set(f.id, []) })

  // FUNCTION_CALL 엣지를 (출발filePath, 도착filePath) 키로 그룹핑
  const funcCallMap = new Map<string, FuncCallEntry[]>()
  rawEdges
    .filter((e) => e.type === 'FUNCTION_CALL')
    .forEach((e) => {
      const srcFunc = funcNodes.find((fn) => fn.id === e.source)
      const tgtFunc = funcNodes.find((fn) => fn.id === e.target)
      if (!srcFunc || !tgtFunc) return
      const key = srcFunc.filePath + '||' + tgtFunc.filePath
      if (!funcCallMap.has(key)) funcCallMap.set(key, [])
      funcCallMap.get(key)!.push({
        callerName: srcFunc.name,
        callerLabel: labelMode === 'comment' && srcFunc.comment ? srcFunc.comment : srcFunc.name,
        callerNodeId: srcFunc.id,
        calleeName: tgtFunc.name,
        calleeLabel: labelMode === 'comment' && tgtFunc.comment ? tgtFunc.comment : tgtFunc.name,
        calleeNodeId: tgtFunc.id,
      })
    })

  rawEdges
    .filter((e) => fileIdSet.has(e.source) && fileIdSet.has(e.target) && e.source !== e.target)
    .forEach((e) => {
      const src = fileNodes.find((f) => f.id === e.source)
      const tgt = fileNodes.find((f) => f.id === e.target)
      if (!src || !tgt) return
      const callChain = funcCallMap.get(src.filePath + '||' + tgt.filePath) ?? []
      fileOutgoing.get(e.source)?.push({ nodeId: e.target, name: tgt.name, callChain })
      fileIncoming.get(e.target)?.push({ nodeId: e.source, name: src.name, callChain })
    })

  // 레이아웃 프리셋에 따라 그룹 위치 계산
  let groupPositions: Map<string, { x: number; y: number }>
  let isoKeys: string[] = []
  if (layoutPreset === 'hub') {
    const hubResult = buildHubPositions(groups, groupLayouts, rawEdges, fileIdSet, fileToGroup)
    groupPositions = hubResult.positions
    isoKeys = hubResult.isoKeys
  } else {
    groupPositions = buildLayerPositions(groups, groupLayouts)
  }

  const result: Node[] = []

  // 고립 그룹 키 셋 (isIso 플래그 부착용)
  const isoKeySet = new Set(isoKeys)

  // 그룹 노드 + 파일 노드 + 함수 노드 생성
  groups.forEach((groupFiles, key) => {
    const layout = groupLayouts.get(key)!
    const pos = groupPositions.get(key)
    if (!pos) return
    const gx = pos.x
    const gy = pos.y
    const isIso = isoKeySet.has(key)

    // 그룹 키에서 layer / sub 분리 (예: "domain/user" → layer="domain", sub="user")
    const slashIdx = key.indexOf('/')
    const layer = slashIdx >= 0 ? key.slice(0, slashIdx) : key
    const sub = slashIdx >= 0 ? key.slice(slashIdx + 1) : ''

    // 그룹 박스 (커스텀 groupNode 타입 사용)
    result.push({
      id: `group-${key}`,
      type: 'groupNode',
      position: { x: gx, y: gy },
      data: { layer, sub, fileCount: groupFiles.length, originalHeight: layout.h, isIso },
      style: { width: layout.w, height: layout.h },
      draggable: true,
    })

    // 파일 노드
    layout.files.forEach(({ file, x, y }) => {
      const size = fileSizes.get(file.id)!
      // 파일 헤더 너비 기준 최대 글자 수 (10px 폰트, 한글 약 6px/char)
      const fileMaxLen = Math.floor((size.w - FILE_PAD_X * 2) / 6)
      result.push({
        id: file.id,
        type: 'fileNode',
        parentId: `group-${key}`,
        extent: 'parent',
        position: { x: GROUP_PAD + x, y: GROUP_HEADER + GROUP_PAD + y },
        data: {
          label: getLabel(file, fileMaxLen),
          name: file.name,
          comment: file.comment,
          isIso,
          incoming: fileIncoming.get(file.id) ?? [],
          outgoing: fileOutgoing.get(file.id) ?? [],
        },
        style: {
          background: '#1e3a5f',
          border: '1.5px solid #3b82f6',
          borderRadius: 8,
          color: '#93c5fd',
          fontSize: 10,
          fontWeight: 700,
          width: size.w,
          height: size.h,
          padding: 0,
        },
      })

      // 함수 노드
      const funcs = funcsByFile.get(file.id) ?? []
      funcs.forEach((fn, i) => {
        const fc = i % size.cols
        const fr = Math.floor(i / size.cols)
        result.push({
          id: fn.id,
          parentId: file.id,
          extent: 'parent',
          position: {
            x: FILE_PAD_X + fc * (FUNC_W + FUNC_PAD),
            y: FILE_PAD_TOP + fr * (FUNC_H + FUNC_PAD),
          },
          // 함수 박스 너비 110px, 좌우 패딩 8px → 102px / ~6px per char ≈ 17자
          data: { label: getLabel(fn, 17), name: fn.name, comment: fn.comment, isIso },
          style: {
            background: '#064e3b',
            border: '1px solid #10b981',
            borderRadius: 5,
            color: '#6ee7b7',
            fontSize: 9,
            width: FUNC_W,
            height: FUNC_H,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0 4px',
            overflow: 'hidden',
          },
        })
      })
    })
  })

  // 허브 레이아웃 — 고립 그룹들을 감싸는 섹션 박스
  if (layoutPreset === 'hub' && isoKeys.length > 0) {
    const PAD = 24
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
    isoKeys.forEach((key) => {
      const pos = groupPositions.get(key)
      const l = groupLayouts.get(key)
      if (!pos || !l) return
      minX = Math.min(minX, pos.x)
      minY = Math.min(minY, pos.y)
      maxX = Math.max(maxX, pos.x + l.w)
      maxY = Math.max(maxY, pos.y + l.h)
    })
    result.push({
      id: '__iso-section__',
      type: 'sectionNode',
      position: { x: minX - PAD, y: minY - PAD - 28 },
      data: { label: '연결 없는 그룹' },
      style: { width: maxX - minX + PAD * 2, height: maxY - minY + PAD * 2 + 28 },
      draggable: false,
      selectable: false,
      zIndex: -10,
    } as Node)
  }

  // 엣지 — 파일 간만, 끊긴 연결 빨간색
  const allNodeIds = new Set(result.map((n) => n.id))

  const edges: Edge[] = rawEdges
    .filter((e) => fileIdSet.has(e.source) && fileIdSet.has(e.target))
    .filter((e) => e.source !== e.target)
    .map((e) => {
      const broken = !allNodeIds.has(e.source) || !allNodeIds.has(e.target)
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        data: { edgeIdentifier: e.edgeIdentifier, type: e.type, broken },
        style: { stroke: broken ? '#ef4444' : '#4b5563', strokeWidth: broken ? 2 : 1.5 },
        markerEnd: { type: MarkerType.ArrowClosed, color: broken ? '#ef4444' : '#4b5563', width: 14, height: 14 },
        zIndex: 0,
        interactionWidth: 0,
      } as Edge
    })

  return { nodes: result, edges }
}

// AI 컨텍스트용 트리 다운로드 — "파일명 — 주석" 형태로 이름과 역할을 함께 표시
export function downloadTreeText(rawNodes: RawNode[]): void {
  const fileNodes = rawNodes.filter((n) => n.type === 'FILE')
  const funcNodes = rawNodes.filter((n) => n.type === 'FUNCTION')

  const tree: Record<string, string[]> = {}
  fileNodes.forEach((f) => {
    const parts = f.filePath.replace(/\\/g, '/').split('/').filter(Boolean)
    for (let i = 0; i < parts.length - 1; i++) {
      const dir = parts.slice(0, i + 1).join('/')
      if (!tree[dir]) tree[dir] = []
    }
    const dir = parts.slice(0, -1).join('/')
    if (!tree[dir]) tree[dir] = []
    tree[dir].push(f.filePath)
  })

  // 이름 — 주석 형태 (주석 없으면 이름만)
  const label = (name: string, comment?: string) =>
    comment ? `${name} — ${comment}` : name

  const lines: string[] = []

  // 디렉터리 경로를 재귀적으로 트리 텍스트로 렌더링
  const renderDir = (dirPath: string, indent: string) => {
    const childDirs = Object.keys(tree)
      .filter((k) => k.substring(0, k.lastIndexOf('/')) === dirPath && k !== dirPath)
      .sort()

    const files = (tree[dirPath] ?? [])
      .map((fp) => fileNodes.find((n) => n.filePath === fp)!)
      .filter(Boolean)
      .sort((a, b) => a.name.localeCompare(b.name))

    const allItems = [...childDirs, ...files.map((f) => f.filePath)]
    allItems.forEach((item, idx) => {
      const isLast = idx === allItems.length - 1
      const branch = isLast ? '└── ' : '├── '
      const childIndent = indent + (isLast ? '    ' : '│   ')

      if (childDirs.includes(item)) {
        lines.push(`${indent}${branch}${item.substring(item.lastIndexOf('/') + 1)}/`)
        renderDir(item, childIndent)
      } else {
        const file = fileNodes.find((n) => n.filePath === item)!
        lines.push(`${indent}${branch}${label(file.name, file.comment)}`)

        funcNodes
          .filter((fn) => fn.filePath === item)
          .sort((a, b) => a.name.localeCompare(b.name))
          .forEach((fn, fi, arr) => {
            const fnBranch = fi === arr.length - 1 ? '└── ' : '├── '
            lines.push(`${childIndent}${fnBranch}${label(fn.name, fn.comment)}`)
          })
      }
    })
  }

  const allPaths = fileNodes.map((f) => f.filePath.replace(/\\/g, '/'))
  const commonPrefix = findCommonPrefix(allPaths)
  const rootName = commonPrefix.replace(/\/$/, '').split('/').pop() || 'project'
  lines.push(`${rootName}/`)

  Object.keys(tree)
    .filter((k) => {
      const rel = k.startsWith(commonPrefix.replace(/\/$/, ''))
        ? k.slice(commonPrefix.replace(/\/$/, '').length).replace(/^\//, '')
        : k
      return rel.split('/').length === 1 && rel !== ''
    })
    .sort()
    .forEach((dir, idx, arr) => {
      const isLast = idx === arr.length - 1
      const branch = isLast ? '└── ' : '├── '
      const childIndent = isLast ? '    ' : '│   '
      lines.push(`${branch}${dir.substring(dir.lastIndexOf('/') + 1)}/`)
      renderDir(dir, childIndent)
    })

  const text = lines.join('\n')
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${rootName}-structure.txt`
  a.click()
  URL.revokeObjectURL(url)
}

// 그래프 노드/엣지 레이아웃 계산 — dagre 기반 방향 그래프 + DDD 폴더 그룹핑
import dagre from '@dagrejs/dagre'
import type { Node, Edge } from '@xyflow/react'

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
const GROUP_HEADER = 26
const GROUP_GAP = 48

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

// 원시 노드/엣지 데이터를 dagre 레이아웃으로 변환하여 React Flow용 노드/엣지 반환
export function buildLayout(rawNodes: RawNode[], rawEdges: RawEdge[], labelMode: LabelMode = 'name'): { nodes: Node[]; edges: Edge[] } {
  const getLabel = (node: RawNode) =>
    labelMode === 'comment' && node.comment ? node.comment : node.name
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
    let col = 0, row = 0
    let colX = 0, rowY = 0
    let maxRowH = 0
    const placed: Array<{ file: RawNode; x: number; y: number }> = []
    let totalW = 0

    files.forEach((f) => {
      const size = fileSizes.get(f.id)!
      placed.push({ file: f, x: colX, y: rowY })
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
      totalW = Math.max(totalW, colX + size.w)
    })
    // 마지막 행
    if (col > 0) rowY += maxRowH

    const gw = GROUP_PAD * 2 + totalW
    const gh = GROUP_HEADER + GROUP_PAD + rowY + GROUP_PAD

    groupLayouts.set(key, { files: placed, w: gw, h: gh })
  })

  // 그룹들을 dagre로 배치
  const gg = new dagre.graphlib.Graph()
  gg.setDefaultEdgeLabel(() => ({}))
  gg.setGraph({ rankdir: 'LR', ranksep: GROUP_GAP, nodesep: GROUP_GAP })

  groupLayouts.forEach((layout, key) => {
    gg.setNode(key, { width: layout.w, height: layout.h })
  })

  // 그룹 간 연결 (파일 간 import 엣지 기반)
  const fileIdSet = new Set(fileNodes.map((f) => f.id))
  const fileToGroup = new Map<string, string>()
  fileNodes.forEach((f) => fileToGroup.set(f.id, getGroupKey(f.filePath, commonPrefix)))

  rawEdges
    .filter((e) => fileIdSet.has(e.source) && fileIdSet.has(e.target))
    .forEach((e) => {
      const sg = fileToGroup.get(e.source)
      const tg = fileToGroup.get(e.target)
      if (sg && tg && sg !== tg) {
        try { gg.setEdge(sg, tg) } catch {}
      }
    })

  dagre.layout(gg)

  const result: Node[] = []

  // 그룹 노드 + 파일 노드 + 함수 노드 생성
  groups.forEach((_, key) => {
    const layout = groupLayouts.get(key)!
    const gPos = gg.node(key)
    if (!gPos) return
    const gx = gPos.x - layout.w / 2
    const gy = gPos.y - layout.h / 2

    // 그룹 박스
    result.push({
      id: `group-${key}`,
      type: 'group',
      position: { x: gx, y: gy },
      data: { label: key },
      style: {
        width: layout.w,
        height: layout.h,
        background: 'rgba(255,255,255,0.02)',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 12,
        fontSize: 11,
        color: 'rgba(255,255,255,0.25)',
        fontWeight: 600,
        paddingTop: 8,
        paddingLeft: 12,
      },
      draggable: true,
    })

    // 파일 노드
    layout.files.forEach(({ file, x, y }) => {
      const size = fileSizes.get(file.id)!
      result.push({
        id: file.id,
        parentId: `group-${key}`,
        extent: 'parent',
        position: { x: GROUP_PAD + x, y: GROUP_HEADER + GROUP_PAD + y },
        data: { label: getLabel(file), name: file.name, comment: file.comment },
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
          data: { label: getLabel(fn), name: fn.name, comment: fn.comment },
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
        data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
        style: { stroke: broken ? '#ef4444' : '#4b5563', strokeWidth: broken ? 2 : 1.5 },
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
  const rootName = commonPrefix.replace(/\/$/, '').split('/').pop() ?? 'project'
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

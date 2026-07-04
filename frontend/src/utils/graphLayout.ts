// 그래프 노드/엣지 레이아웃 계산 — 계층형(DDD 레이어 컬럼) / 도메인형(바운디드 컨텍스트 박스) 이중 레이아웃
import React from 'react'

import type { Node, Edge } from '@xyflow/react'
import { MarkerType } from '@xyflow/react'
import { WARNING_META } from '../components/WarningPanel'

// DDD 레이어별 색상 팔레트 — 파일/함수 노드 색상 구분용 (계층형·도메인 공통 적용)
const LAYER_PALETTE: Record<string, { accent: string; fileBg: string; fileText: string; funcBg: string; funcText: string }> = {
  domain:         { accent: '#3b82f6', fileBg: '#1a2e4a', fileText: '#93c5fd', funcBg: '#1e3a5f', funcText: '#bfdbfe' },
  application:    { accent: '#ca8a04', fileBg: '#2d2200', fileText: '#fde047', funcBg: '#3a2800', funcText: '#fef08a' },
  infrastructure: { accent: '#a855f7', fileBg: '#22103a', fileText: '#d8b4fe', funcBg: '#2d1454', funcText: '#e9d5ff' },
  interfaces:     { accent: '#10b981', fileBg: '#052e1b', fileText: '#6ee7b7', funcBg: '#064e3b', funcText: '#a7f3d0' },
  pages:          { accent: '#06b6d4', fileBg: '#062030', fileText: '#67e8f9', funcBg: '#0c2d3d', funcText: '#a5f3fc' },
  components:     { accent: '#0ea5e9', fileBg: '#061e2e', fileText: '#7dd3fc', funcBg: '#0c2636', funcText: '#bae6fd' },
  hooks:          { accent: '#f97316', fileBg: '#2d1500', fileText: '#fdba74', funcBg: '#3d1f00', funcText: '#fed7aa' },
  utils:          { accent: '#f97316', fileBg: '#2d1500', fileText: '#fdba74', funcBg: '#3d1f00', funcText: '#fed7aa' },
}
const DEFAULT_LAYER_PALETTE = { accent: '#3b82f6', fileBg: '#1e3a5f', fileText: '#93c5fd', funcBg: '#064e3b', funcText: '#6ee7b7' }

// DDD 레이어 → 컬럼 순서 (왼쪽=프론트, 오른쪽=DB) — 요청 흐름 방향
// pages → components → hooks/utils → interfaces → application → domain → infrastructure → database
const LAYER_COLUMN: Record<string, number> = {
  pages:          0,
  components:     1,
  hooks:          2,
  utils:          2,
  interfaces:     3,
  application:    4,
  domain:         5,
  infrastructure: 6,
}

// 섹션 박스 병합 규칙 — hooks+utils만 같은 섹션으로 묶음 (pages/components는 각자 독립 섹션)
const LAYER_SECTION_KEY: Record<string, string> = {
  hooks: 'hooks',
  utils: 'hooks',
}

export interface ColumnInfo {
  fieldName: string
  columnName: string
  javaType: string
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
  columns?: ColumnInfo[]
  bgColor?: string
  userLabel?: string
  userNote?: string
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
export function getGroupKey(filePath: string, commonPrefix: string): string {
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
  // 레이어 못 찾으면(비DDD 프로젝트) 실제 폴더 구조로 그룹핑 — src/lib/crates/packages/pkg 같은
  // 의미 없는 래퍼 디렉터리는 건너뛰고 그 다음 세그먼트(진짜 모듈명, 예: ripgrep의 crates/core → core)를
  // 키로 쓴다. 래퍼를 다 건너뛰어도 남는 디렉터리가 없으면(서브디렉터리 없이 파일이 바로 있음, 예: gin의
  // 루트 .go 파일들) 파일마다 박스가 갈라지지 않도록 공통 'root' 키로 묶는다.
  const NON_SEMANTIC_WRAPPER_DIRS = new Set(['src', 'lib', 'crates', 'packages', 'pkg'])
  let dirParts = parts.slice(0, -1)
  while (dirParts.length > 0 && NON_SEMANTIC_WRAPPER_DIRS.has(dirParts[0])) dirParts = dirParts.slice(1)
  return dirParts[0] ?? 'root'
}

// 공통 prefix 계산
export function findCommonPrefix(paths: string[]): string {
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
export type LayoutPreset = 'layer' | 'domain'

// 도메인으로 사용하기 부적절한 구조적 폴더명 (너무 일반적이어서 도메인 식별 불가)
const NON_DOMAIN_FOLDERS = new Set([
  'impl', 'dto', 'entity', 'vo', 'config',
  'mapper', 'exception', 'exceptions', 'constant', 'constants', 'enums',
  'persistence', 'repository', 'repositories',
  'com', 'org', 'net', 'io', 'co', 'kr',
  'util', 'utils', 'helper', 'helpers', 'lib', 'libs', 'generated',
  // 기술 분류용 구조 폴더 — 도메인이 아니라 전달 방식(예: interfaces/api)일 뿐
  'api', 'rest', 'web', 'websocket', 'ws', 'controller', 'controllers',
  'endpoint', 'endpoints', 'graphql', 'v1', 'v2', 'handler', 'handlers',
])

// 도메인 판별 시 제거할 기술 접미사 (예: GraphController → Graph, ShareGraphPage → ShareGraph)
// 백엔드 클래스 접미사 + 프론트 UI 래퍼 접미사 모두 포함
const CLASS_SUFFIXES = [
  'RestController', 'Controller', 'ServiceImpl', 'Service', 'RepositoryImpl',
  'Repository', 'Configuration', 'Config', 'Entity', 'Request', 'Response',
  'Mapper', 'Handler', 'Facade', 'Factory', 'Provider', 'Adapter', 'Port',
  'Exception', 'Listener', 'Event', 'Validator', 'Filter', 'Resolver',
  'Converter', 'Manager', 'Runner', 'Scheduler', 'Client', 'Gateway',
  'Command', 'Query', 'Dto', 'Vo', 'Impl',
  'Page', 'View', 'Screen', 'Modal', 'Panel', 'Section', 'Card',
  'Banner', 'Overlay', 'Tour', 'Layout', 'Context', 'Hook', 'Form',
  'Header', 'Footer',
]

// 토큰이 알려진 도메인인지 — 단/복수형 + 동사→명사 파생을 흡수 (teams→team, donate→donation, pay→payment)
function resolveDomain(token: string, knownDomains: Set<string>): string | null {
  if (knownDomains.has(token)) return token
  if (token.endsWith('s') && knownDomains.has(token.slice(0, -1))) return token.slice(0, -1)
  if (token.endsWith('es') && knownDomains.has(token.slice(0, -2))) return token.slice(0, -2)
  // 동사형 파일명 토큰 → 명사형 도메인 (영어 -ion/-ment 명사화): donate→donation, create→creation, pay→payment
  if (token.length >= 3) {
    const noun = token.endsWith('e') ? token.slice(0, -1) + 'ion' : token + 'ion'
    if (knownDomains.has(noun)) return noun
    if (knownDomains.has(token + 'ment')) return token + 'ment'
  }
  return null
}

// 파일명에서 도메인을 유추 — 알려진 도메인 집합에 매칭될 때만 반환 (없는 도메인을 만들지 않으므로 파편화 없음)
function domainFromFilename(filePath: string, knownDomains: Set<string>): string | null {
  const fileName = filePath.replace(/\\/g, '/').split('/').pop() ?? ''
  let base = fileName.replace(/\.[^.]+$/, '')
  for (const suf of CLASS_SUFFIXES) {
    if (base.length > suf.length && base.endsWith(suf)) { base = base.slice(0, -suf.length); break }
  }
  const tokens = base.replace(/([a-z0-9])([A-Z])/g, '$1 $2').split(/[\s_-]+/).filter(Boolean).map(t => t.toLowerCase())

  // 1. 선두 토큰부터 누적 매칭 — 복합어 도메인 우선 (예: UserAccount → useraccount)
  let best: string | null = null
  let acc = ''
  for (const t of tokens) {
    acc += t
    const m = resolveDomain(acc, knownDomains)
    if (m) best = m
  }
  if (best) return best

  // 2. 선두 매칭 실패 시 개별 토큰에서 도메인 탐색 (예: ShareGraphPage → graph, CreateProjectModal → project)
  for (const t of tokens) {
    const m = resolveDomain(t, knownDomains)
    if (m) return m
  }
  return null
}

// 경로 기반으로 확실히 식별되는 도메인 집합을 수집 — 파일명 유추의 화이트리스트로 사용
export function buildKnownDomains(filePaths: string[], commonPrefix: string): Set<string> {
  const set = new Set<string>()
  filePaths.forEach((p) => {
    const d = extractDomain(p, commonPrefix)
    if (d !== 'common') set.add(d)
  })
  return set
}

//파일 경로에서 도메인을 동적으로 추출 — 어떤 프로젝트 구조에서도 작동
export function extractDomain(filePath: string, commonPrefix: string, knownDomains?: Set<string>): string {
  const rel = filePath.startsWith(commonPrefix) ? filePath.slice(commonPrefix.length) : filePath
  const parts = rel.replace(/\\/g, '/').split('/').filter(Boolean)

  const layerKeywords = new Set([
    'domain', 'application', 'infrastructure', 'interfaces',
    'pages', 'components', 'hooks',
    'features', 'modules', 'views', 'screens',
  ])

  // 1. 레이어 키워드 이후 첫 번째 의미 있는 서브폴더를 도메인으로 사용
  for (let i = 0; i < parts.length; i++) {
    if (!layerKeywords.has(parts[i])) continue
    for (let j = i + 1; j < parts.length - 1; j++) {
      const sub = parts[j].toLowerCase()
      if (!NON_DOMAIN_FOLDERS.has(sub) && sub.length > 1) return sub
    }
    break
  }

  // 2. 구조 폴더(interfaces/api 등)만 있어 경로로 도메인을 못 찾으면 파일명에서 유추
  //    — 알려진 도메인에 매칭될 때만. 예: GraphController → graph
  if (knownDomains) {
    const fromName = domainFromFilename(filePath, knownDomains)
    if (fromName) return fromName
  }

  // 레이어 키워드 기반 구조가 없으면 common으로 분류 — 파일명 기반 추출은 파편화 유발
  return 'common'
}

// 도메인 뷰 색상 팔레트 — 12색 순환
const PALETTE: ReadonlyArray<{ color: string; opaqueColor: string }> = [
  { color: '#3b82f6', opaqueColor: 'rgba(15,30,60,0.98)' },
  { color: '#10b981', opaqueColor: 'rgba(5,30,20,0.98)' },
  { color: '#8b5cf6', opaqueColor: 'rgba(25,10,50,0.98)' },
  { color: '#f59e0b', opaqueColor: 'rgba(40,25,5,0.98)' },
  { color: '#06b6d4', opaqueColor: 'rgba(5,25,35,0.98)' },
  { color: '#e879f9', opaqueColor: 'rgba(35,5,40,0.98)' },
  { color: '#f97316', opaqueColor: 'rgba(40,15,5,0.98)' },
  { color: '#4ade80', opaqueColor: 'rgba(5,35,15,0.98)' },
  { color: '#fb7185', opaqueColor: 'rgba(40,10,15,0.98)' },
  { color: '#a78bfa', opaqueColor: 'rgba(20,10,40,0.98)' },
  { color: '#34d399', opaqueColor: 'rgba(5,30,20,0.98)' },
  { color: '#fbbf24', opaqueColor: 'rgba(40,30,0,0.98)' },
]
const COMMON_COLOR = { color: '#6b7280', opaqueColor: 'rgba(20,20,20,0.98)' }

// 발견된 도메인 이름 목록으로 색상 맵을 동적 생성 — 같은 이름 = 항상 같은 색상 (알파벳 정렬 인덱스)
export function buildDomainColorMap(
  domains: Iterable<string>
): Map<string, { color: string; opaqueColor: string }> {
  const map = new Map<string, { color: string; opaqueColor: string }>()
  const sorted = [...new Set(domains)].filter(d => d !== 'common').sort()
  sorted.forEach((d, i) => map.set(d, PALETTE[i % PALETTE.length]))
  map.set('common', COMMON_COLOR)
  return map
}

// 도메인 뷰 — 바운디드 컨텍스트별로 그룹을 묶어 그리드 배치, 중앙 칸은 DB 자리로 비움 (전체 16:9 목표)
function buildDomainPositions(
  groups: Map<string, RawNode[]>,
  groupLayouts: Map<string, { files: Array<{ file: RawNode; x: number; y: number }>; w: number; h: number }>,
  commonPrefix: string,
  knownDomains: Set<string>
): {
  positions: Map<string, { x: number; y: number }>
  domainBounds: Map<string, { x: number; y: number; w: number; h: number; domain: string }>
} {
  const GROUP_GAP = 24
  const DOMAIN_PAD = 20
  const DOMAIN_LABEL_H = 28
  const DOMAIN_ROW_GAP = 48

  // 그룹 → 도메인 매핑 (그룹에 속한 첫 번째 파일의 경로로 판단)
  const groupDomain = new Map<string, string>()
  groups.forEach((files, key) => {
    const domain = files.length > 0 ? extractDomain(files[0].filePath, commonPrefix, knownDomains) : 'common'
    groupDomain.set(key, domain)
  })

  // 도메인별 그룹 키 목록
  const domainGroups = new Map<string, string[]>()
  groups.forEach((_, key) => {
    const d = groupDomain.get(key) ?? 'common'
    if (!domainGroups.has(d)) domainGroups.set(d, [])
    domainGroups.get(d)!.push(key)
  })

  // 도메인 내부: 그룹들을 LAYER_COLUMN 순서로 가로 정렬
  const domainInternalLayouts = new Map<string, { w: number; h: number; groupOffsets: Map<string, { x: number; y: number }> }>()
  domainGroups.forEach((keys, _domain) => {
    const sorted = [...keys].sort((a, b) => {
      const layerA = a.indexOf('/') >= 0 ? a.slice(0, a.indexOf('/')) : a
      const layerB = b.indexOf('/') >= 0 ? b.slice(0, b.indexOf('/')) : b
      return (LAYER_COLUMN[layerA] ?? 99) - (LAYER_COLUMN[layerB] ?? 99)
    })

    // 그룹을 한 줄에 몰지 않고 그리드로 줄바꿈 — 도메인 박스가 가로로 무한정 길어지는 것 방지
    // 가로로 약간 넓은 직사각형이 되도록 목표 행 수를 √(n/2.5)로 잡고 그 너비를 넘으면 줄바꿈
    const widths = sorted.map((k) => groupLayouts.get(k)!.w)
    const widest = widths.length ? Math.max(...widths) : 0
    const sumW = widths.reduce((s, w) => s + w + GROUP_GAP, 0)
    const desiredRows = Math.max(1, Math.round(Math.sqrt(sorted.length / 2.5)))
    const maxRowW = Math.max(widest, sumW / desiredRows)

    const offsets = new Map<string, { x: number; y: number }>()
    const top = DOMAIN_PAD + DOMAIN_LABEL_H
    let x = DOMAIN_PAD, y = top, rowH = 0, contentW = 0
    sorted.forEach((key) => {
      const l = groupLayouts.get(key)!
      if (x > DOMAIN_PAD && x + l.w > DOMAIN_PAD + maxRowW) {
        x = DOMAIN_PAD
        y += rowH + GROUP_GAP
        rowH = 0
      }
      offsets.set(key, { x, y })
      x += l.w + GROUP_GAP
      contentW = Math.max(contentW, x - GROUP_GAP)
      rowH = Math.max(rowH, l.h)
    })
    const totalW = contentW + DOMAIN_PAD
    const totalH = (y + rowH) + DOMAIN_PAD

    domainInternalLayouts.set(_domain, { w: totalW, h: totalH, groupOffsets: offsets })
  })

  // 도메인 박스를 1열로 배치 — 오른쪽 끝을 맞춰 우측정렬 (DB 섹션에 최대한 가깝게)
  // common 항상 마지막, 나머지는 이름순
  const domainOrder = [...domainGroups.keys()].sort((a, b) => {
    if (a === 'common') return 1
    if (b === 'common') return -1
    return a.localeCompare(b)
  })

  // 전체 최대 너비 계산 (우측 정렬 기준선)
  let maxDomainW = 0
  domainOrder.forEach((d) => {
    const layout = domainInternalLayouts.get(d)!
    maxDomainW = Math.max(maxDomainW, layout.w)
  })

  const domainBounds = new Map<string, { x: number; y: number; w: number; h: number; domain: string }>()
  const groupPositions = new Map<string, { x: number; y: number }>()

  let curY = 0
  domainOrder.forEach((d) => {
    const layout = domainInternalLayouts.get(d)!
    // 우측정렬: 오른쪽 끝을 maxDomainW에 맞춤
    const dx = maxDomainW - layout.w
    const dy = curY

    domainBounds.set(d, { x: dx, y: dy, w: layout.w, h: layout.h, domain: d })

    layout.groupOffsets.forEach((off, key) => {
      groupPositions.set(key, { x: dx + off.x, y: dy + off.y })
    })

    curY += layout.h + DOMAIN_ROW_GAP
  })

  return { positions: groupPositions, domainBounds }
}

export interface FuncCallEntry {
  callerName: string; callerLabel: string; callerNodeId: string
  calleeName: string; calleeLabel: string; calleeNodeId: string
}

export interface ConnEntry {
  nodeId: string; name: string; callChain: FuncCallEntry[]
}

export interface FileSidebarData {
  nodeId: string
  name: string
  comment?: string
  incoming: ConnEntry[]
  outgoing: ConnEntry[]
}

// 텍스트가 maxLen을 초과하면 말줄임표 + title tooltip이 있는 span 반환, 아니면 문자열 그대로
function labelNode(full: string, maxLen: number): React.ReactNode {
  if (full.length <= maxLen) return full
  const trimmed = full.slice(0, maxLen - 1) + '…'
  return React.createElement('span', { title: full, style: { cursor: 'default' } }, trimmed)
}

// DDD 레이어 컬럼 배치 — 같은 레이어끼리 같은 컬럼에 세로 정렬
// 그룹 수가 많으면 레이어 내 서브컬럼으로 분할
function buildLayerPositions(
  groups: Map<string, RawNode[]>,
  groupLayouts: Map<string, { files: Array<{ file: RawNode; x: number; y: number }>; w: number; h: number }>
): Map<string, { x: number; y: number }> {
  const COL_GAP = 64
  const ROW_GAP = 40
  const SUB_COL_GAP = 40

  const colGroups = new Map<number, string[]>()
  groups.forEach((_, key) => {
    const layer = key.indexOf('/') >= 0 ? key.slice(0, key.indexOf('/')) : key
    const col = LAYER_COLUMN[layer] ?? 7
    if (!colGroups.has(col)) colGroups.set(col, [])
    colGroups.get(col)!.push(key)
  })

  // 레이어 컬럼별 총 너비 계산 (서브컬럼 포함)
  const sortedCols = Array.from(colGroups.keys()).sort((a, b) => a - b)
  const colTotalW = new Map<number, number>()
  colGroups.forEach((keys, col) => {
    const subCols = keys.length > 8 ? 3 : keys.length > 4 ? 2 : 1
    const perSub = Math.ceil(keys.length / subCols)
    let totalW = 0
    for (let s = 0; s < subCols; s++) {
      const subKeys = keys.slice(s * perSub, (s + 1) * perSub)
      if (subKeys.length === 0) continue
      const maxW = Math.max(...subKeys.map((k) => groupLayouts.get(k)!.w))
      totalW += maxW + (s < subCols - 1 ? SUB_COL_GAP : 0)
    }
    colTotalW.set(col, totalW)
  })

  const colStartX = new Map<number, number>()
  let xCursor = 0
  sortedCols.forEach((col) => {
    colStartX.set(col, xCursor)
    xCursor += (colTotalW.get(col) ?? 0) + COL_GAP
  })

  const positions = new Map<string, { x: number; y: number }>()
  colGroups.forEach((keys, col) => {
    const baseX = colStartX.get(col) ?? 0
    const subCols = keys.length > 8 ? 3 : keys.length > 4 ? 2 : 1
    const perSub = Math.ceil(keys.length / subCols)
    let subX = baseX
    for (let s = 0; s < subCols; s++) {
      const subKeys = keys.slice(s * perSub, (s + 1) * perSub)
      if (subKeys.length === 0) continue
      let y = 0
      subKeys.forEach((key) => {
        positions.set(key, { x: subX, y })
        y += groupLayouts.get(key)!.h + ROW_GAP
      })
      const maxW = Math.max(...subKeys.map((k) => groupLayouts.get(k)!.w))
      subX += maxW + SUB_COL_GAP
    }
  })
  return positions
}


// 원시 노드/엣지 데이터를 레이아웃으로 변환하여 React Flow용 노드/엣지 반환
export function buildLayout(
  rawNodes: RawNode[],
  rawEdges: RawEdge[],
  labelMode: LabelMode = 'name',
  layoutPreset: LayoutPreset = 'layer',
  onOpenFileSidebar?: (data: FileSidebarData) => void
): { nodes: Node[]; edges: Edge[] } {
  // 노드 라벨 반환 — userLabel 우선, 없으면 labelMode 적용, 초과 시 말줄임표
  const getLabel = (node: RawNode, maxLen = 999): React.ReactNode => {
    const raw = node.userLabel
      ? node.userLabel
      : (labelMode === 'comment' && node.comment ? node.comment : node.name)
    return labelNode(raw, maxLen)
  }
  const fileNodes = rawNodes.filter((n) => n.type === 'FILE')
  const funcNodes = rawNodes.filter((n) => n.type === 'FUNCTION')

  const commonPrefix = findCommonPrefix(fileNodes.map((f) => f.filePath.replace(/\\/g, '/')))

  // 경로로 확실히 식별되는 도메인 집합 — interfaces/api 컨트롤러를 파일명으로 올바른 도메인에 귀속
  const knownDomains = buildKnownDomains(fileNodes.map((f) => f.filePath), commonPrefix)

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

  // 도메인 뷰 전용: 혼합 그룹(예: interfaces/api)을 파일 도메인별로 분리
  // — 같은 폴더의 컨트롤러들이 각자 올바른 도메인 섹션에 배치되도록
  let effectiveGroups = groups
  let effectiveGroupLayouts = groupLayouts
  if (layoutPreset === 'domain') {
    const splitGroups = new Map<string, RawNode[]>()
    groups.forEach((files, key) => {
      const byDomain = new Map<string, RawNode[]>()
      files.forEach(f => {
        const d = extractDomain(f.filePath, commonPrefix, knownDomains)
        if (!byDomain.has(d)) byDomain.set(d, [])
        byDomain.get(d)!.push(f)
      })
      if (byDomain.size <= 1) {
        splitGroups.set(key, files)
      } else {
        byDomain.forEach((domainFiles, domain) => {
          splitGroups.set(`${key}::${domain}`, domainFiles)
        })
      }
    })

    // 분리된 그룹 레이아웃 재계산
    const splitLayouts = new Map<string, { files: Array<{ file: RawNode; x: number; y: number }>; w: number; h: number }>()
    splitGroups.forEach((files, key) => {
      if (groupLayouts.has(key)) {
        splitLayouts.set(key, groupLayouts.get(key)!)
        return
      }
      const COLS = 3
      let col = 0, colX = 0, rowY = 0, maxRowH = 0
      const placed: Array<{ file: RawNode; x: number; y: number }> = []
      let totalW = 0
      files.forEach(f => {
        const size = fileSizes.get(f.id)!
        placed.push({ file: f, x: colX, y: rowY })
        totalW = Math.max(totalW, colX + size.w)
        maxRowH = Math.max(maxRowH, size.h)
        col++
        if (col >= COLS) { col = 0; colX = 0; rowY += maxRowH + FILE_GAP; maxRowH = 0 }
        else { colX += size.w + FILE_GAP }
      })
      if (col > 0) rowY += maxRowH
      splitLayouts.set(key, {
        files: placed,
        w: GROUP_PAD * 2 + totalW,
        h: GROUP_HEADER + GROUP_PAD + rowY + GROUP_PAD,
      })
    })

    effectiveGroups = splitGroups
    effectiveGroupLayouts = splitLayouts
  }

  const fileIdSet = new Set(fileNodes.map((f) => f.id))

  // 파일별 인/아웃 연결 집계 (사이드바 데이터용)
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
  let domainBoundsResult: Map<string, { x: number; y: number; w: number; h: number; domain: string }> | undefined
  if (layoutPreset === 'domain') {
    const domainResult = buildDomainPositions(effectiveGroups, effectiveGroupLayouts, commonPrefix, knownDomains)
    groupPositions = domainResult.positions
    domainBoundsResult = domainResult.domainBounds
  } else {
    groupPositions = buildLayerPositions(groups, groupLayouts)
  }

  // DDD 레이어 상위 박스 메타 — 섹션 기준점 사전 계산용
  const LAYER_META_PRE: Record<string, { label: string; color: string; opaqueColor: string }> = {
    infrastructure: { label: 'Infrastructure', color: '#a855f7', opaqueColor: 'rgba(30,10,50,0.98)'  },
    domain:         { label: 'Domain',         color: '#3b82f6', opaqueColor: 'rgba(15,30,60,0.98)'  },
    application:    { label: 'Application',    color: '#eab308', opaqueColor: 'rgba(40,30,5,0.98)'   },
    interfaces:     { label: 'Interfaces',     color: '#10b981', opaqueColor: 'rgba(5,30,20,0.98)'   },
    pages:          { label: 'Pages',          color: '#06b6d4', opaqueColor: 'rgba(5,25,35,0.98)'   },
    components:     { label: 'Components',     color: '#0ea5e9', opaqueColor: 'rgba(5,20,40,0.98)'   },
    hooks:          { label: 'Hooks / Utils',  color: '#f97316', opaqueColor: 'rgba(40,15,5,0.98)'   },
  }
  const LAYER_PAD_PRE = 20
  const LAYER_LABEL_H = 24

  // 레이어별 그룹 키 수집 — 병합 레이어는 섹션 키 기준으로 묶음 (pages+components → 'pages', hooks+utils → 'hooks')
  const layerGroupKeysPre = new Map<string, string[]>()
  groups.forEach((_, key) => {
    const layer = key.indexOf('/') >= 0 ? key.slice(0, key.indexOf('/')) : key
    const sectionKey = LAYER_SECTION_KEY[layer] ?? layer
    if (!layerGroupKeysPre.has(sectionKey)) layerGroupKeysPre.set(sectionKey, [])
    layerGroupKeysPre.get(sectionKey)!.push(key)
  })

  // 레이어별 섹션 기준점 (좌상단 절대 좌표) — 그룹 노드 상대 좌표 변환용 (layer 모드 전용)
  const layerSectionOrigins = new Map<string, { x: number; y: number; w: number; h: number }>()
  if (layoutPreset === 'layer') {
    layerGroupKeysPre.forEach((keys, layer) => {
      // 비DDD 레이어도 섹션 박스 표시 — LAYER_META_PRE에 없으면 폴백 처리
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      keys.forEach((key) => {
        const p = groupPositions.get(key); const l = groupLayouts.get(key)
        if (!p || !l) return
        minX = Math.min(minX, p.x); minY = Math.min(minY, p.y)
        maxX = Math.max(maxX, p.x + l.w); maxY = Math.max(maxY, p.y + l.h)
      })
      if (minX === Infinity) return
      layerSectionOrigins.set(layer, {
        x: minX - LAYER_PAD_PRE,
        y: minY - LAYER_PAD_PRE - LAYER_LABEL_H,
        w: maxX - minX + LAYER_PAD_PRE * 2,
        h: maxY - minY + LAYER_PAD_PRE * 2 + LAYER_LABEL_H,
      })
    })
  }

  const result: Node[] = []

  // 섹션 노드를 먼저 삽입 — React Flow는 parentId 노드보다 부모가 먼저 있어야 올바르게 렌더링
  // 미지의 레이어명에 폴백 색상을 할당 — 비DDD 프로젝트 계층 뷰 지원
  const LAYER_FALLBACK_COLORS = ['#6b7280', '#64748b', '#78716c', '#71717a', '#737373']
  let fallbackColorIdx = 0
  const getFallbackLayerMeta = (layer: string) => ({
    label: layer.charAt(0).toUpperCase() + layer.slice(1),
    color: LAYER_FALLBACK_COLORS[fallbackColorIdx++ % LAYER_FALLBACK_COLORS.length],
    opaqueColor: 'rgba(20,20,20,0.98)',
  })

  if (layoutPreset === 'layer') {
    layerSectionOrigins.forEach((bounds, layer) => {
      const meta = LAYER_META_PRE[layer] ?? getFallbackLayerMeta(layer)
      result.push({
        id: `layer-section-${layer}`,
        type: 'sectionNode',
        position: { x: bounds.x, y: bounds.y },
        data: { label: meta.label, color: meta.color, opaqueColor: meta.opaqueColor, layer, origW: bounds.w, origH: bounds.h },
        style: { width: bounds.w, height: bounds.h },
        draggable: true,
        selectable: false,
        zIndex: -20,
      } as Node)
    })
  } else if (layoutPreset === 'domain' && domainBoundsResult) {
    // 도메인 섹션 노드 삽입 — 바운디드 컨텍스트별 박스
    const domColorMap = buildDomainColorMap([...domainBoundsResult.keys()])
    domainBoundsResult.forEach((bounds, domain) => {
      const palette = domColorMap.get(domain) ?? COMMON_COLOR
      result.push({
        id: `domain-section-${domain}`,
        type: 'sectionNode',
        position: { x: bounds.x, y: bounds.y },
        data: { label: domain.charAt(0).toUpperCase() + domain.slice(1), color: palette.color, opaqueColor: palette.opaqueColor, layer: domain, origW: bounds.w, origH: bounds.h },
        style: { width: bounds.w, height: bounds.h },
        draggable: true,
        selectable: false,
        zIndex: -20,
      } as Node)
    })
  }

  // 그룹 노드 + 파일 노드 + 함수 노드 생성
  // 도메인 뷰: 분리된 그룹(effectiveGroups) 사용, 계층 뷰: 원본 groups 사용
  const renderGroups = layoutPreset === 'domain' ? effectiveGroups : groups
  const renderLayouts = layoutPreset === 'domain' ? effectiveGroupLayouts : groupLayouts
  renderGroups.forEach((groupFiles, key) => {
    const layout = renderLayouts.get(key)!
    const pos = groupPositions.get(key)
    if (!pos) return
    const gx = pos.x
    const gy = pos.y

    // 그룹 키에서 layer / sub 분리 — split 키(예: "interfaces/api::graph")는 :: 앞 부분만 사용
    const pureKey = key.includes('::') ? key.slice(0, key.indexOf('::')) : key
    const slashIdx = pureKey.indexOf('/')
    const layer = slashIdx >= 0 ? pureKey.slice(0, slashIdx) : pureKey
    const sub = slashIdx >= 0 ? pureKey.slice(slashIdx + 1) : ''

    // 섹션 기준점 결정 — layer 모드: DDD 레이어 섹션 / domain 모드: 도메인 섹션
    let parentSectionId: string | undefined
    let groupPos: { x: number; y: number }

    if (layoutPreset === 'domain' && domainBoundsResult) {
      const fileDomain = groupFiles.length > 0 ? extractDomain(groupFiles[0].filePath, commonPrefix, knownDomains) : 'common'
      const domainBounds = domainBoundsResult.get(fileDomain)
      if (domainBounds) {
        parentSectionId = `domain-section-${fileDomain}`
        groupPos = { x: gx - domainBounds.x, y: gy - domainBounds.y }
      } else {
        groupPos = { x: gx, y: gy }
      }
    } else {
      const sectionKey = LAYER_SECTION_KEY[layer] ?? layer
      const sectionOrigin = layerSectionOrigins.get(sectionKey)
      parentSectionId = sectionOrigin ? `layer-section-${sectionKey}` : undefined
      groupPos = sectionOrigin ? { x: gx - sectionOrigin.x, y: gy - sectionOrigin.y } : { x: gx, y: gy }
    }

    const isDomainMode = layoutPreset === 'domain' && domainBoundsResult && parentSectionId

    // 그룹 박스 — 도메인 뷰에서는 생략 (파일이 도메인 섹션 배경 위에 절대 좌표로 배치됨)
    if (!isDomainMode) {
      result.push({
        id: `group-${key}`,
        type: 'groupNode',
        ...(parentSectionId ? { parentId: parentSectionId } : {}),
        position: groupPos,
        data: { layer, sub, fileCount: groupFiles.length, originalHeight: layout.h },
        style: { width: layout.w, height: layout.h },
        draggable: true,
      })
    }

    // 파일 노드 — 도메인 뷰: 도메인 섹션 기준 상대 좌표, 계층 뷰: group 안에 상대 좌표
    layout.files.forEach(({ file, x, y }) => {
      const size = fileSizes.get(file.id)!
      // 파일 헤더 너비 기준 최대 글자 수 (10px 폰트, 한글 약 6px/char)
      const fileMaxLen = Math.floor((size.w - FILE_PAD_X * 2) / 6)
      // isDomainMode: groupPos는 도메인 섹션 기준 상대 좌표이므로 그대로 사용
      const filePos = isDomainMode
        ? { x: groupPos!.x + GROUP_PAD + x, y: groupPos!.y + GROUP_HEADER + GROUP_PAD + y }
        : { x: GROUP_PAD + x, y: GROUP_HEADER + GROUP_PAD + y }
      const fileDomain = extractDomain(file.filePath, commonPrefix, knownDomains)
      const palette = LAYER_PALETTE[layer] ?? DEFAULT_LAYER_PALETTE
      result.push({
        id: file.id,
        type: 'fileNode',
        ...(isDomainMode ? { parentId: parentSectionId! } : { parentId: `group-${key}`, extent: 'parent' as const }),
        position: filePos,
        data: {
          label: getLabel(file, fileMaxLen),
          name: file.name,
          comment: file.comment,
          userLabel: file.userLabel,
          userNote: file.userNote,
          layer,
          domain: fileDomain,
          incoming: fileIncoming.get(file.id) ?? [],
          outgoing: fileOutgoing.get(file.id) ?? [],
          onOpenSidebar: onOpenFileSidebar ? () => onOpenFileSidebar({
            nodeId: file.id,
            name: file.name,
            comment: file.comment,
            incoming: fileIncoming.get(file.id) ?? [],
            outgoing: fileOutgoing.get(file.id) ?? [],
          }) : undefined,
        },
        style: {
          background: palette.fileBg,
          border: `1.5px solid ${palette.accent}`,
          borderRadius: 8,
          color: palette.fileText,
          fontSize: 10,
          fontWeight: 700,
          width: size.w,
          height: size.h,
          padding: 0,
        },
      })

      // 함수 노드 — 도메인/계층 뷰 모두 파일의 자식으로 배치 (파일 안 상대 좌표).
      // 파일을 드래그하면 함수도 자식으로 함께 이동해 빈 박스로 분리되지 않는다.
      const funcs = funcsByFile.get(file.id) ?? []
      funcs.forEach((fn, i) => {
        const fc = i % size.cols
        const fr = Math.floor(i / size.cols)
        const fnRelX = FILE_PAD_X + fc * (FUNC_W + FUNC_PAD)
        const fnRelY = FILE_PAD_TOP + fr * (FUNC_H + FUNC_PAD)
        result.push({
          id: fn.id,
          parentId: file.id,
          extent: 'parent' as const,
          position: { x: fnRelX, y: fnRelY },
          // 함수 박스 너비 110px, 좌우 패딩 8px → 102px / ~6px per char ≈ 17자
          data: { label: getLabel(fn, 17), name: fn.name, comment: fn.comment, userLabel: fn.userLabel, userNote: fn.userNote, layer, domain: fileDomain },
          style: {
            background: palette.funcBg,
            border: `1px solid ${palette.accent}80`,
            borderRadius: 5,
            color: palette.funcText,
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

  // DB_TABLE 노드 배치
  // 도메인 뷰: 그리드 중앙 칸에 테이블을 격자로 / 그 외: 전체 그룹 오른쪽 세로 열
  const dbTableNodes = rawNodes.filter((n) => n.type === 'DB_TABLE')
  if (dbTableNodes.length > 0) {
    const DB_W = 160
    const DB_H = 48
    const DB_GAP = 16
    const DB_SECTION_LABEL_H = 28
    const dbTableStyle = {
      background: '#3b0a0a',
      border: '1.5px solid #ef4444',
      borderRadius: 8,
      color: '#fca5a5',
      fontSize: 11,
      fontWeight: 700,
      width: DB_W,
      height: DB_H,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '0 8px',
      textAlign: 'center' as const,
    }

    // 도메인 뷰·계층 뷰 공통 — 전체 그룹 경계 우측에 세로 열 배치
    {
      let allGroupsMaxX = 0, allMinY = Infinity, allMaxY = -Infinity
      groupPositions.forEach((pos, key) => {
        const l = effectiveGroupLayouts.get(key)
        if (!l) return
        allGroupsMaxX = Math.max(allGroupsMaxX, pos.x + l.w)
        allMinY = Math.min(allMinY, pos.y)
        allMaxY = Math.max(allMaxY, pos.y + l.h)
      })
      const allCenterY = allMinY !== Infinity ? (allMinY + allMaxY) / 2 : 0
      const dbSectionX = allGroupsMaxX + 80
      const dbSectionY = allCenterY - (dbTableNodes.length * (DB_H + DB_GAP)) / 2

      const sectionW = DB_W + 40
      const sectionH = DB_SECTION_LABEL_H + dbTableNodes.length * (DB_H + DB_GAP) + DB_GAP
      result.push({
        id: 'layer-section-database',
        type: 'sectionNode',
        position: { x: dbSectionX - 20, y: dbSectionY - DB_SECTION_LABEL_H - 20 },
        data: { label: 'Database', color: '#ef4444', opaqueColor: 'rgba(40,5,5,0.98)', layer: 'database', origW: sectionW, origH: sectionH + 20 },
        width: sectionW,
        height: sectionH + 20,
        style: { width: sectionW, height: sectionH + 20 },
        draggable: false,
        selectable: false,
        zIndex: -20,
      } as Node)

      dbTableNodes.forEach((dbNode, i) => {
        result.push({
          id: dbNode.id,
          position: { x: dbSectionX, y: dbSectionY + i * (DB_H + DB_GAP) },
          data: { label: dbNode.name },
          width: DB_W,
          height: DB_H,
          style: dbTableStyle,
        })
      })
    }
  }

  // 엣지 — 파일 간 IMPORT 엣지, 끊긴 연결 빨간색
  const allNodeIds = new Set(result.map((n) => n.id))
  const funcIdSet = new Set(funcNodes.map((f) => f.id))
  const dbTableIdSet = new Set(dbTableNodes.map((n) => n.id))

  const importEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'IMPORT' && fileIdSet.has(e.source) && fileIdSet.has(e.target))
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

  // FUNCTION_CALL 엣지 — 함수 노드 간 호출 관계, amber 점선
  const callEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'FUNCTION_CALL' && funcIdSet.has(e.source) && funcIdSet.has(e.target))
    .filter((e) => e.source !== e.target)
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
      style: { stroke: '#f59e0b', strokeWidth: 1.2, strokeDasharray: '5 4' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#f59e0b', width: 10, height: 10 },
      zIndex: 1,
    } as Edge))

  // INSTANTIATION 엣지 — 파일 간 new ClassName() 관계, 보라색 점선
  const instEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'INSTANTIATION' && fileIdSet.has(e.source) && fileIdSet.has(e.target))
    .filter((e) => e.source !== e.target)
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
      style: { stroke: '#a855f7', strokeWidth: 1.2, strokeDasharray: '3 4' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#a855f7', width: 10, height: 10 },
      zIndex: 0,
      interactionWidth: 0,
    } as Edge))

  // DB 엣지 색상 맵 — 타입별 CRUD 구분
  const DB_EDGE_COLORS: Record<string, string> = {
    DB_READ:   '#22d3ee', // cyan  — 읽기
    DB_CREATE: '#4ade80', // green — 생성
    DB_UPDATE: '#facc15', // yellow — 수정
    DB_DELETE: '#f87171', // light red — 삭제
    DB_WRITE:  '#f97316', // orange — 레거시 (기존 데이터 호환)
  }

  const DB_EDGE_TYPES = new Set(['DB_READ', 'DB_WRITE', 'DB_CREATE', 'DB_UPDATE', 'DB_DELETE'])

  const dbReadEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'DB_READ' && fileIdSet.has(e.source) && dbTableIdSet.has(e.target))
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
      style: { stroke: '#22d3ee', strokeWidth: 1.5, strokeDasharray: '5 4' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#22d3ee', width: 10, height: 10 },
      zIndex: 0,
    } as Edge))

  const dbWriteEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'DB_WRITE' && fileIdSet.has(e.source) && dbTableIdSet.has(e.target))
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
      style: { stroke: '#f97316', strokeWidth: 1.5, strokeDasharray: '5 4' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#f97316', width: 10, height: 10 },
      zIndex: 0,
    } as Edge))

  // DB_CREATE / DB_UPDATE / DB_DELETE 엣지
  const dbCrudEdges: Edge[] = rawEdges
    .filter((e) => DB_EDGE_TYPES.has(e.type) && e.type !== 'DB_READ' && e.type !== 'DB_WRITE'
      && fileIdSet.has(e.source) && dbTableIdSet.has(e.target))
    .map((e) => {
      const color = DB_EDGE_COLORS[e.type] ?? '#22d3ee'
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
        style: { stroke: color, strokeWidth: 1.5, strokeDasharray: '5 4' },
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 10, height: 10 },
        zIndex: 0,
      } as Edge
    })

  // 프론트 파일 → 백엔드 컨트롤러 API_CALL 엣지
  const apiCallEdges: Edge[] = rawEdges
    .filter((e) => e.type === 'API_CALL' && fileIdSet.has(e.source) && fileIdSet.has(e.target))
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { edgeIdentifier: e.edgeIdentifier, type: e.type },
      style: { stroke: '#e879f9', strokeWidth: 1.5, strokeDasharray: '6 3' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#e879f9', width: 10, height: 10 },
      zIndex: 0,
    } as Edge))


  return { nodes: result, edges: [...importEdges, ...callEdges, ...instEdges, ...dbReadEdges, ...dbWriteEdges, ...dbCrudEdges, ...apiCallEdges] }
}

// 그래프 뷰 줌 한계 — GraphPage/ShareGraphPage 공통
export const GRAPH_MIN_ZOOM = 0.05
export const GRAPH_MAX_ZOOM = 2

// 노드 이름/주석 검색 — 파일명 · 함수명 · 주석 매치, 최대 limit개
export function searchNodes(rawNodes: RawNode[], query: string, limit = 10): RawNode[] {
  const q = query.trim().toLowerCase()
  if (!q) return []
  return rawNodes
    .filter((n) => n.type !== 'GROUP' && (n.name.toLowerCase().includes(q) || (n.comment ?? '').toLowerCase().includes(q)))
    .slice(0, limit)
}

// DB 엣지 타입 판별
export function isDbEdgeType(t: string | undefined): boolean {
  return t === 'DB_READ' || t === 'DB_WRITE' || t === 'DB_CREATE' || t === 'DB_UPDATE' || t === 'DB_DELETE'
}

// 엣지 타입별 hidden 여부 적용
export function applyEdgeVisibility(
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

  const md = `# ${rootName} — 프로젝트 구조\n\n\`\`\`\n${lines.join('\n')}\n\`\`\`\n`
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${rootName}-structure.md`
  a.click()
  URL.revokeObjectURL(url)
}

// 경고 목록을 타입별로 그룹핑하여 마크다운 파일로 다운로드
// 라벨은 WARNING_META 단일 소스 재사용 — 자체 맵 중복 시절 신규 타입 누락(8/15종)이 있었음
export function downloadWarningsMd(warningList: { type: string; nodeIds: string[]; message: string }[]): void {
  const grouped = new Map<string, string[]>()
  for (const w of warningList) {
    if (!grouped.has(w.type)) grouped.set(w.type, [])
    grouped.get(w.type)!.push(w.message)
  }
  const lines = [`# 구조 경고 리포트\n`, `> 총 ${warningList.length}개 경고\n`]
  for (const [type, msgs] of grouped.entries()) {
    const label = WARNING_META[type] ? `${WARNING_META[type].label} (${type})` : type
    lines.push(`\n## ${label} (${msgs.length}개)\n`)
    msgs.forEach(m => lines.push(`- ${m.replace(/^[^:]+:\s*/, '')}`))
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'codeprint-warnings.md'
  a.click()
  URL.revokeObjectURL(a.href)
}

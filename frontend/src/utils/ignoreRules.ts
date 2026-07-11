// 경고 예외(IGNORE) 패턴 규칙 — 글로브 추론·매칭과 architecture-intent API 라운드트립
// (modules·rules는 보존하고 ignore만 교체 저장해 데이터 손실을 막는다)
import axios from 'axios'

export interface IgnoreRule {
  type: string
  from: string
  to: string
}

// 레이어 디렉터리 이름들 — 파일 경로에서 이 세그먼트를 찾아 넓은(레이어 단위) 글로브를 추론한다.
// 백엔드 검출기 별칭과 동일 어휘(우선순위 순: 더 구체적인 영속화 먼저).
const LAYER_DIRS = [
  'persistence', 'mybatis', 'repository', 'jpa', 'dao',
  'infrastructure', 'infra', 'adapter',
  'application', 'usecase',
  'interfaces', 'presentation',
  'domain', 'core', 'model',
]

// 파일 경로 → 넓은 글로브 추론. 예: .../application/article/X.java → **/application/**
// 알려진 레이어 세그먼트가 있으면 그 레이어 전체를, 없으면 파일이 든 디렉터리를 대상으로 한다.
export function inferGlob(filePath: string | undefined): string {
  if (!filePath) return ''
  const p = filePath.replace(/\\/g, '/')
  const segs = p.split('/').filter(Boolean)
  for (const layer of LAYER_DIRS) {
    if (segs.includes(layer)) return `**/${layer}/**`
  }
  // 레이어 미발견 — 파일이 든 디렉터리 기준
  const dir = segs.length >= 2 ? segs[segs.length - 2] : null
  return dir ? `**/${dir}/**` : ''
}

// 글로브 → 정규식 (백엔드 ArchitectureIntent.globToPattern과 동일 규칙: ** = 임의, * = / 제외, ? = / 제외 한 글자)
function globToRegExp(glob: string): RegExp {
  let re = '^'
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i]
    if (c === '*') {
      if (glob[i + 1] === '*') { re += '.*'; i++ } else { re += '[^/]*' }
    } else if (c === '?') {
      re += '[^/]'
    } else if ('\\.[]{}()+-^$|'.includes(c)) {
      re += '\\' + c
    } else {
      re += c
    }
  }
  return new RegExp(re + '$')
}

// 글로브가 경로에 매치되는지
export function globMatch(glob: string, path: string): boolean {
  if (!glob || glob === '*') return true
  return globToRegExp(glob).test(path)
}

interface MatchableWarning {
  type: string
  nodeIds: string[]
}

// 후보 규칙이 주어진 경고 목록 중 몇 건에 매치되는지 — 클라이언트 미리보기 카운트(서버 왕복 0)
export function countMatches(
  rule: IgnoreRule,
  warnings: MatchableWarning[],
  fileOf: (nodeId: string) => string,
): number {
  return warnings.filter(w => {
    if (rule.type && rule.type !== '*' && rule.type !== w.type) return false
    const from = w.nodeIds[0] ? fileOf(w.nodeIds[0]) : ''
    const to = w.nodeIds[1] ? fileOf(w.nodeIds[1]) : ''
    if (rule.from && rule.from !== '*' && !globMatch(rule.from, from)) return false
    if (rule.to && rule.to !== '*' && !globMatch(rule.to, to)) return false
    return true
  }).length
}

// 프로젝트의 IGNORE 규칙 로드 — 없으면 빈 목록
export async function loadIgnoreRules(projectId: string): Promise<IgnoreRule[]> {
  try {
    const res = await axios.get(`/api/projects/${projectId}/architecture-intent`)
    return (res.data.ignore ?? []).map((g: { type?: string; from?: string; to?: string }) => ({
      type: g.type ?? '', from: g.from ?? '', to: g.to ?? '',
    }))
  } catch {
    return []
  }
}

// 예외 규칙 변경 이력 한 건
export interface AuditLogEntry {
  username: string
  action: 'ADD' | 'REMOVE'
  ruleType: string
  ruleFrom: string
  ruleTo: string
  createdAt: string
}

// 예외 규칙 변경 이력 조회 — 소유자만(비소유자는 403이므로 실패 시 빈 목록)
export async function loadAuditLog(projectId: string): Promise<AuditLogEntry[]> {
  try {
    const res = await axios.get(`/api/projects/${projectId}/architecture-intent/audit-log`)
    return res.data
  } catch {
    return []
  }
}

// IGNORE 규칙 저장 — modules·rules는 현재 값을 그대로 라운드트립해 보존하고 ignore만 교체한다
export async function saveIgnoreRules(projectId: string, ignore: IgnoreRule[]): Promise<void> {
  const cur = await axios.get(`/api/projects/${projectId}/architecture-intent`).catch(() => null)
  const data = cur?.data ?? { modules: [], rules: [] }
  const body = {
    modules: (data.modules ?? []).map((m: { name: string; globs?: string[] }) => ({ name: m.name, globs: m.globs ?? [] })),
    rules: data.rules ?? [],
    ignore,
  }
  await axios.put(`/api/projects/${projectId}/architecture-intent`, body)
}

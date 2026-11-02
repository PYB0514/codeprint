// 파일 저장 시 Codeprint 구조 경고를 인라인 진단(Diagnostics)으로 표시하는 확장 (내부 개발용, 마켓플레이스 미배포)
import * as vscode from 'vscode'
import * as path from 'path'
import * as fs from 'fs'
import { spawn, ChildProcess } from 'child_process'

interface CodeprintWarning {
  type: string
  severity?: 'HIGH' | 'MEDIUM' | 'LOW'
  message: string
  file?: string
  line?: number
  col?: number
  endCol?: number
  fingerprint?: string
}

const SEVERITY_MAP: Record<string, vscode.DiagnosticSeverity> = {
  HIGH: vscode.DiagnosticSeverity.Error,
  MEDIUM: vscode.DiagnosticSeverity.Warning,
  LOW: vscode.DiagnosticSeverity.Information,
}

let watcherProcess: ChildProcess | undefined
let diagnostics: vscode.DiagnosticCollection

export function activate(context: vscode.ExtensionContext) {
  diagnostics = vscode.languages.createDiagnosticCollection('codeprint')
  context.subscriptions.push(diagnostics)

  const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath
  if (!workspaceRoot) return

  // 번들된 fat jar(java -jar)로 독립 실행 — Codeprint 레포 자신뿐 아니라 어떤 워크스페이스든 동작(2026-08-04, jar화)
  const jarPath = path.join(context.extensionPath, 'bin', 'codeprint-watch.jar')
  if (!fs.existsSync(jarPath)) {
    vscode.window.showWarningMessage(`Codeprint Watch: ${jarPath}을 찾을 수 없어 비활성화됩니다.`)
    return
  }

  // 출력은 워크스페이스가 아니라 확장 전용 저장소에 — 사용자 프로젝트에 build/ 폴더를 남기지 않는다
  const storageDir = context.globalStorageUri.fsPath
  fs.mkdirSync(storageDir, { recursive: true })
  const outputFile = path.join(storageDir, 'build', 'codeprint-local', 'watch-warnings.json')
  fs.mkdirSync(path.dirname(outputFile), { recursive: true })

  watcherProcess = spawn('java', ['-jar', jarPath, workspaceRoot], { cwd: storageDir })
  watcherProcess.stdout?.on('data', (chunk) => console.log(`[codeprint watchLocal] ${chunk}`))
  watcherProcess.stderr?.on('data', (chunk) => console.error(`[codeprint watchLocal] ${chunk}`))
  watcherProcess.on('error', (err) => {
    vscode.window.showWarningMessage(`Codeprint Watch: java 실행 실패(${err.message}) — JDK 17+ 설치·PATH 등록을 확인하세요.`)
  })
  context.subscriptions.push({ dispose: () => watcherProcess?.kill() })

  // 이전 실행의 상태 파일이 남아 있으면 이번 플랫폼 판정과 섞이므로 기동 전에 지운다
  const statusFile = path.join(path.dirname(outputFile), 'watch-status.json')
  fs.rmSync(statusFile, { force: true })

  // watch-warnings.json이 아직 없을 수 있어(최초 분석 진행 중) 부모 디렉터리를 감시 — 파일이 나타나거나 갱신되면 반영
  let debounceTimer: ReturnType<typeof setTimeout> | undefined
  let statusShown = false
  fs.watch(path.dirname(outputFile), (_event, filename) => {
    if (filename === path.basename(statusFile) && !statusShown) {
      statusShown = warnNativeFallback(statusFile)
      return
    }
    if (filename !== path.basename(outputFile)) return
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => reloadDiagnostics(outputFile, workspaceRoot), 300)
  })
}

export function deactivate() {
  watcherProcess?.kill()
}

// native 폴백 경고 표시
// 폴백은 조용히 정확도를 떨어뜨리므로(C/C++는 결과 자체가 빔) 사용자에게 명시한다. 읽기 성공 시 true.
function warnNativeFallback(statusFile: string): boolean {
  let status: { platform?: string; nativeUnavailable?: string[] }
  try {
    status = JSON.parse(fs.readFileSync(statusFile, 'utf8'))
  } catch {
    return false // 쓰는 도중일 수 있음 — 다음 이벤트에서 재시도
  }
  const unavailable = status.nativeUnavailable ?? []
  if (unavailable.length > 0) {
    vscode.window.showWarningMessage(
      `Codeprint Watch: 이 플랫폼(${status.platform})에서 ${unavailable.join(', ')} 정밀 분석(tree-sitter)을 쓸 수 없어 ` +
      `정확도가 낮은 폴백으로 동작합니다. C/C++ 파일은 분석되지 않습니다.`)
  }
  return true
}

// 워닝 JSON을 읽어 파일별로 그룹핑한 뒤 DiagnosticCollection에 반영 — 파일 line은 1-indexed, VS Code Range는 0-indexed
function reloadDiagnostics(outputFile: string, workspaceRoot: string) {
  let warnings: CodeprintWarning[]
  try {
    warnings = JSON.parse(fs.readFileSync(outputFile, 'utf8'))
  } catch {
    return // 최초 분석 중이거나 쓰는 도중(부분 쓰기)일 수 있음 — 다음 파일 변경 이벤트에서 재시도
  }

  const byFile = new Map<string, vscode.Diagnostic[]>()
  for (const w of warnings) {
    if (!w.file) continue
    const line = Math.max(0, (w.line ?? 1) - 1)
    // 컬럼 정보(Java/TS/JS FUNCTION 노드)가 있으면 식별자 범위만, 없으면 줄 전체를 밑줄
    const range = w.col != null && w.endCol != null
      ? new vscode.Range(line, w.col, line, w.endCol)
      : new vscode.Range(line, 0, line, 200)
    const severity = SEVERITY_MAP[w.severity ?? 'MEDIUM'] ?? vscode.DiagnosticSeverity.Warning
    const diagnostic = new vscode.Diagnostic(range, `[${w.type}] ${w.message}`, severity)
    diagnostic.source = 'Codeprint'
    if (!byFile.has(w.file)) byFile.set(w.file, [])
    byFile.get(w.file)!.push(diagnostic)
  }

  diagnostics.clear()
  for (const [relPath, diags] of byFile) {
    diagnostics.set(vscode.Uri.file(path.join(workspaceRoot, relPath)), diags)
  }
}

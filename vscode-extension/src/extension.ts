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

  // watch-warnings.json이 아직 없을 수 있어(최초 분석 진행 중) 부모 디렉터리를 감시 — 파일이 나타나거나 갱신되면 반영
  let debounceTimer: ReturnType<typeof setTimeout> | undefined
  fs.watch(path.dirname(outputFile), (_event, filename) => {
    if (filename !== path.basename(outputFile)) return
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => reloadDiagnostics(outputFile, workspaceRoot), 300)
  })
}

export function deactivate() {
  watcherProcess?.kill()
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

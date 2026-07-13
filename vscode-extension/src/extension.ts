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

  const backendDir = path.join(workspaceRoot, 'backend')
  const gradlew = path.join(backendDir, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
  if (!fs.existsSync(gradlew)) {
    vscode.window.showWarningMessage(`Codeprint Watch: ${gradlew}을 찾을 수 없어 비활성화됩니다.`)
    return
  }

  const outputFile = path.join(backendDir, 'build', 'codeprint-local', 'watch-warnings.json')
  fs.mkdirSync(path.dirname(outputFile), { recursive: true })

  watcherProcess = spawn(gradlew, ['watchLocal', '-PanalysisDir=..'], { cwd: backendDir, shell: true })
  watcherProcess.stdout?.on('data', (chunk) => console.log(`[codeprint watchLocal] ${chunk}`))
  watcherProcess.stderr?.on('data', (chunk) => console.error(`[codeprint watchLocal] ${chunk}`))
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
    const range = new vscode.Range(line, 0, line, 200)
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

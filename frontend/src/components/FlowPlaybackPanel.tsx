// 흐름 재생 패널 UI — GraphPage/GraphViewerPage/CommunityPostGraphPage 공유
import { useMemo } from 'react'
import { type RawNode, getGroupKey, findCommonPrefix } from '../utils/graphLayout'
import { type CallTreeNode, type PlaybackItem, findTreeNode, findPathInTree, extendToDefaultLeaf } from '../utils/flowPlayback'

// PascalCase 함수명이 생성자를 뜻하지 않는 언어 — Go·C#은 공개(exported) 함수 전체가 관례상 PascalCase
const PASCAL_CASE_IS_NOT_CONSTRUCTOR = new Set(['Go', 'C#'])

interface FlowPlaybackPanelProps {
  callTree: CallTreeNode | null
  playbackItems: PlaybackItem[]
  playbackCursor: number
  playbackPlaying: boolean
  activePath: { nodeIds: string[]; edgeIds: string[]; edgeTypes: string[] }
  pendingBranchNodeId: string | null
  playbackRootNodeId: string | null
  rawNodes: RawNode[]
  setPlaybackCursor: (updater: (c: number) => number) => void
  setPlaybackPlaying: (updater: (p: boolean) => boolean) => void
  setPendingBranchNodeId: (updater: (id: string | null) => string | null) => void
  resetPlayback: () => void
  selectBranchImmediate: (nodeId: string) => void
  confirmBranch: (nodeId: string) => void
  startPlayback: (nodeId: string) => void
}

export function FlowPlaybackPanel({
  callTree, playbackItems, playbackCursor, playbackPlaying, activePath, pendingBranchNodeId, playbackRootNodeId,
  rawNodes, setPlaybackCursor, setPlaybackPlaying, setPendingBranchNodeId, resetPlayback, selectBranchImmediate, confirmBranch, startPlayback,
}: FlowPlaybackPanelProps) {
  // 도메인 뱃지: 파일 경로 구조에서 그룹 키 추출 (그래프 범례·섹션과 동일한 getGroupKey 사용 — 특정 프로젝트 도메인명에 고정되지 않음)
  // callTree와 무관해 조기 return보다 먼저 계산 — Hooks 규칙(모든 렌더에서 동일한 순서로 호출)을 지키기 위함
  const commonPrefix = useMemo(
    () => findCommonPrefix(rawNodes.filter((n) => n.type === 'FILE').map((n) => n.filePath)),
    [rawNodes]
  )

  if (!callTree) return null

  const cur = playbackCursor >= 0 ? playbackItems[playbackCursor] : null
  const total = playbackItems.length

  // 흐름 목적: 루트 노드의 주석 또는 이름
  const rootRaw = rawNodes.find((n) => n.id === callTree.nodeId)
  const flowTitle = rootRaw?.comment || rootRaw?.name || '흐름 재생'

  const rootPath = rootRaw?.filePath ?? ''
  const rootGroupKey = rootPath ? getGroupKey(rootPath, commonPrefix) : null
  const domainLabel = rootGroupKey && rootGroupKey !== 'root' ? rootGroupKey.charAt(0).toUpperCase() + rootGroupKey.slice(1) : null

  // 분기점 감지
  const curTreeNode = cur ? findTreeNode(callTree, cur.id) : null
  const branchChildren = (curTreeNode?.children.length ?? 0) > 1 ? curTreeNode!.children : []

  return (
    <div className="bg-gray-800/60 border border-gray-700 rounded-xl overflow-hidden">
      {/* 헤더 — 흐름 목적 + 도메인 뱃지 */}
      <div className="flex items-center justify-between px-3 pt-3 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-amber-400 text-xs">▶</span>
          <span className="text-white text-xs font-semibold truncate">{flowTitle}</span>
          {domainLabel && (
            <span className="flex-shrink-0 text-[9px] px-1.5 py-0.5 rounded-full bg-blue-900/50 text-blue-300 border border-blue-700/40">
              {domainLabel}
            </span>
          )}
        </div>
        <button onClick={resetPlayback} className="flex-shrink-0 text-gray-600 hover:text-gray-400 text-xs ml-2" title="닫기">✕</button>
      </div>

      {/* 전체 단계 도트 — 클릭하면 해당 스텝으로 이동 */}
      {total > 1 && (
        <div className="px-3 pb-2">
          <div className="flex items-center gap-0.5 flex-wrap">
            {playbackItems.map((item, i) => {
              const isCur = i === playbackCursor
              const isPast = i < playbackCursor
              const isLast = i === total - 1
              const hasBranchHere = (() => {
                const tn = findTreeNode(callTree, item.id)
                return (tn?.children.length ?? 0) > 1
              })()
              return (
                <div key={item.id} className="flex items-center gap-0.5">
                  <button
                    onClick={() => { setPlaybackCursor(() => i); setPlaybackPlaying(() => false) }}
                    title={item.label}
                    className={`w-2.5 h-2.5 rounded-full transition-all duration-200 flex-shrink-0 ${
                      isCur ? 'bg-amber-400 scale-125 ring-2 ring-amber-400/30' :
                      isPast ? 'bg-amber-700/60' :
                      'bg-gray-600 hover:bg-gray-500'
                    } ${hasBranchHere ? 'ring-1 ring-blue-400/50' : ''}`}
                  />
                  {!isLast && (
                    <div className={`w-2 h-px flex-shrink-0 ${isPast || isCur ? 'bg-amber-700/50' : 'bg-gray-700'}`} />
                  )}
                </div>
              )
            })}
            <span className="ml-1.5 text-[9px] text-gray-500 tabular-nums whitespace-nowrap">
              {playbackCursor < 0 ? `0 / ${total}` : `${playbackCursor + 1} / ${total} 단계`}
            </span>
          </div>
        </div>
      )}

      {/* 현재 스텝 카드 */}
      {cur && (
        <div className="mx-3 mb-2 bg-gray-900/70 rounded-lg px-3 py-2.5 flex flex-col gap-1">
          <p className="text-emerald-300 text-xs font-semibold leading-snug">{cur.label}</p>
          {cur.filePath && (
            <p className="text-[10px] text-gray-500 truncate" title={cur.filePath}>
              {cur.filePath.split('/').pop()}
            </p>
          )}
        </div>
      )}

      {/* 마지막 스텝 — 결과 설명 (생성자·DB 조작 등 비개발자 친화적 안내) */}
      {cur && playbackCursor === total - 1 && (() => {
        const rawNode = rawNodes.find(n => n.id === cur.id)
        const name = rawNode?.name || ''
        const comment = rawNode?.comment || ''
        let msg: string | null = null
        let clr = '#4ade80'
        if (cur.nodeType === 'FUNCTION' && /^[A-Z]/.test(name) && !PASCAL_CASE_IS_NOT_CONSTRUCTOR.has(rawNode?.language ?? '')) {
          // PascalCase 함수 = 생성자로 판단 (Go·C#은 공개 함수 전부가 PascalCase라 제외)
          msg = `${comment || name} 객체가 반환됩니다`
        } else if (cur.nodeType === 'DB_TABLE') {
          const et = cur.incomingEdgeType
          if (et === 'DB_READ') { msg = `${comment || name} 데이터를 읽습니다`; clr = '#22d3ee' }
          else if (et === 'DB_CREATE' || et === 'DB_WRITE') { msg = `${comment || name}에 저장됩니다`; clr = '#4ade80' }
          else if (et === 'DB_UPDATE') { msg = `${comment || name} 데이터가 수정됩니다`; clr = '#facc15' }
          else if (et === 'DB_DELETE') { msg = `${comment || name} 데이터가 삭제됩니다`; clr = '#f87171' }
        }
        if (!msg) return null
        return (
          <div className="mx-3 mb-2 rounded-lg px-3 py-2 flex items-center gap-2"
               style={{ background: clr + '18', border: `1px solid ${clr}40` }}>
            <span style={{ color: clr }} className="text-xs flex-shrink-0">✓</span>
            <span className="text-[11px] leading-snug" style={{ color: clr }}>{msg}</span>
          </div>
        )
      })()}

      {/* 분기 선택 — 현재 스텝에서 경로가 갈릴 때 */}
      {branchChildren.length > 0 && (
        <div className="mx-3 mb-2 flex flex-col gap-1">
          <p className="text-[9px] text-gray-500">흐름이 분기됩니다 — 경로를 선택하세요</p>
          {branchChildren.map((child) => {
            const childRaw = rawNodes.find((n) => n.id === child.nodeId)
            const isPending = pendingBranchNodeId === child.nodeId
            const isConfirmed = activePath.nodeIds.includes(child.nodeId) && !pendingBranchNodeId
            // 이 분기를 선택했을 때의 예상 단계 수 계산 (in-place 수정 방지를 위해 복사본 사용)
            const branchStepCount = (() => {
              if (!callTree) return null
              const p = findPathInTree(callTree, child.nodeId)
              if (!p) return null
              const ids = [...p.nodeIds], eIds = [...p.edgeIds], eTypes = [...p.edgeTypes]
              extendToDefaultLeaf(callTree, child.nodeId, ids, eIds, eTypes)
              return ids.length
            })()
            return (
              <button
                key={child.nodeId}
                onClick={() => selectBranchImmediate(child.nodeId)}
                className={`text-left text-[10px] px-2.5 py-1.5 rounded-lg border transition-colors ${
                  isPending
                    ? 'border-blue-500/60 bg-blue-900/30 text-blue-300'
                    : isConfirmed
                    ? 'border-amber-600/60 bg-amber-900/20 text-amber-300'
                    : 'border-gray-700 text-gray-400 hover:border-gray-600 hover:text-gray-200 hover:bg-gray-700/30'
                }`}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate">{childRaw?.comment || childRaw?.name || child.nodeId}</span>
                  {branchStepCount != null && (
                    <span className={`flex-shrink-0 tabular-nums ${isPending ? 'text-blue-400' : 'text-gray-600'}`}>{branchStepCount}단계</span>
                  )}
                </span>
              </button>
            )
          })}
        </div>
      )}

      {/* 재생 컨트롤 */}
      <div className="flex items-center gap-1 px-3 pb-3">
        <button
          onClick={() => { setPendingBranchNodeId(() => null); setPlaybackCursor((c) => Math.max(0, c - 1)) }}
          disabled={playbackCursor <= 0}
          className="text-gray-500 hover:text-white disabled:opacity-25 px-2 py-1.5 rounded hover:bg-gray-700 text-sm"
        >←</button>
        <button
          onClick={() => {
            if (pendingBranchNodeId) { confirmBranch(pendingBranchNodeId) }
            else if (playbackCursor >= total - 1) { if (playbackRootNodeId) startPlayback(playbackRootNodeId) }
            else setPlaybackPlaying((p) => !p)
          }}
          className="flex-1 text-xs bg-amber-500/20 hover:bg-amber-500/30 text-amber-400 border border-amber-700/40 rounded-lg py-1.5"
        >
          {pendingBranchNodeId ? '▶ 이 경로로 재생' : playbackPlaying ? '⏸ 일시정지' : playbackCursor >= total - 1 ? '↺ 처음부터' : '▶ 재생'}
        </button>
        <button
          onClick={() => setPlaybackCursor((c) => Math.min(total - 1, c + 1))}
          disabled={playbackCursor >= total - 1}
          className="text-gray-500 hover:text-white disabled:opacity-25 px-2 py-1.5 rounded hover:bg-gray-700 text-sm"
        >→</button>
      </div>
    </div>
  )
}

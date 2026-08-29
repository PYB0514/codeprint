// 리컨실러 T1 수동 트리거 버튼 — 타입 게이팅(MISSING_TRANSACTIONAL_DELETE만)·BYOK 게이팅(onFixAttempt 전달 시만) 회귀 테스트
import { describe, it, expect, vi, beforeAll } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../i18n'
import WarningPanel, { type FixAttemptResult } from './WarningPanel'

// jsdom의 navigator.language(보통 en-US)로 LanguageDetector가 en을 고를 수 있어, 한국어 문구로 단언하기 전에 명시 고정
beforeAll(async () => { await i18n.changeLanguage('ko') })

const missingTransactionalWarning = {
  type: 'MISSING_TRANSACTIONAL_DELETE',
  severity: 'HIGH' as const,
  nodeIds: ['node-1'],
  message: 'deleteByUserId: @Transactional 누락',
}

const highFanOutWarning = {
  type: 'HIGH_FAN_OUT',
  severity: 'LOW' as const,
  nodeIds: ['node-2'],
  message: 'run — 14개 함수를 호출',
}

describe('WarningPanel — 자동수정 시도 버튼', () => {
  it('MISSING_TRANSACTIONAL_DELETE + onFixAttempt 전달 시에만 버튼이 보인다', () => {
    render(<WarningPanel warnings={[missingTransactionalWarning]} onFixAttempt={vi.fn()} />)
    expect(screen.getByText('자동수정 시도')).toBeInTheDocument()
  })

  it('지원하지 않는 룰 타입(HIGH_FAN_OUT)에는 onFixAttempt가 있어도 버튼이 안 보인다', () => {
    render(<WarningPanel warnings={[highFanOutWarning]} onFixAttempt={vi.fn()} />)
    expect(screen.queryByText('자동수정 시도')).not.toBeInTheDocument()
  })

  it('onFixAttempt가 없으면(BYOK 미등록 등) MISSING_TRANSACTIONAL_DELETE여도 버튼이 안 보인다', () => {
    render(<WarningPanel warnings={[missingTransactionalWarning]} />)
    expect(screen.queryByText('자동수정 시도')).not.toBeInTheDocument()
  })

  it('클릭하면 onFixAttempt(nodeId)를 호출하고 SUCCESS 결과(근거+diff)를 표시한다', async () => {
    const result: FixAttemptResult = { outcome: 'SUCCESS', diff: '+ @Transactional', rationale: '삭제 쿼리에 트랜잭션 경계 추가', reason: null }
    const onFixAttempt = vi.fn().mockResolvedValue(result)
    render(<WarningPanel warnings={[missingTransactionalWarning]} onFixAttempt={onFixAttempt} />)

    await userEvent.click(screen.getByText('자동수정 시도'))

    expect(onFixAttempt).toHaveBeenCalledWith('node-1')
    await waitFor(() => expect(screen.getByText('삭제 쿼리에 트랜잭션 경계 추가')).toBeInTheDocument())
    expect(screen.getByText('+ @Transactional')).toBeInTheDocument()
  })

  it('SKIPPED 결과는 사유만 표시한다(diff·복사 버튼 없음)', async () => {
    const result: FixAttemptResult = { outcome: 'SKIPPED', diff: null, rationale: null, reason: 'BYOK 키 미등록' }
    const onFixAttempt = vi.fn().mockResolvedValue(result)
    render(<WarningPanel warnings={[missingTransactionalWarning]} onFixAttempt={onFixAttempt} />)

    await userEvent.click(screen.getByText('자동수정 시도'))

    await waitFor(() => expect(screen.getByText(/BYOK 키 미등록/)).toBeInTheDocument())
    expect(screen.queryByText('복사')).not.toBeInTheDocument()
  })
})

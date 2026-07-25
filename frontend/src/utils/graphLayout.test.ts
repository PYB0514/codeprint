// applyLayerModeNodeVisibility 회귀 테스트 — 반복-C(ERROR_TRACKER.md, React Flow extent:'parent' hidden 미전파) 재발 방지
import { describe, it, expect } from 'vitest'
import type { Node } from '@xyflow/react'
import { applyLayerModeNodeVisibility } from './graphLayout'

// layer-section → group → file → function 4단계 체인 노드를 만드는 헬퍼
function makeChain(layer: string): Node[] {
  const sectionId = `layer-section-${layer}`
  const groupId = `group-${layer}`
  const fileId = `file-${layer}`
  const funcId = `func-${layer}`
  return [
    { id: sectionId, position: { x: 0, y: 0 }, data: {} },
    { id: groupId, parentId: sectionId, position: { x: 0, y: 0 }, data: {} },
    { id: fileId, parentId: groupId, extent: 'parent', position: { x: 0, y: 0 }, data: {} },
    { id: funcId, parentId: fileId, extent: 'parent', position: { x: 0, y: 0 }, data: {} },
  ]
}

describe('applyLayerModeNodeVisibility', () => {
  it('opaque 레이어의 group·file·function 3단계 전부에 hidden:true를 적용한다', () => {
    const nodes = [...makeChain('domain'), ...makeChain('infrastructure')]

    const result = applyLayerModeNodeVisibility(nodes, new Set(['domain']))

    const byId = new Map(result.map((n) => [n.id, n]))
    expect(byId.get('group-domain')?.hidden).toBe(true)
    expect(byId.get('file-domain')?.hidden).toBe(true)
    expect(byId.get('func-domain')?.hidden).toBe(true)
  })

  it('opaque가 아닌 레이어의 하위 노드는 hidden 처리하지 않는다', () => {
    const nodes = [...makeChain('domain'), ...makeChain('infrastructure')]

    const result = applyLayerModeNodeVisibility(nodes, new Set(['domain']))

    const byId = new Map(result.map((n) => [n.id, n]))
    expect(byId.get('group-infrastructure')?.hidden).toBeFalsy()
    expect(byId.get('file-infrastructure')?.hidden).toBeFalsy()
    expect(byId.get('func-infrastructure')?.hidden).toBeFalsy()
  })

  it('layer-section 노드 자신은 hidden 되지 않고 data.opaque만 갱신된다', () => {
    const nodes = makeChain('domain')

    const result = applyLayerModeNodeVisibility(nodes, new Set(['domain']))

    const section = result.find((n) => n.id === 'layer-section-domain')
    expect(section?.hidden).toBe(false)
    expect((section?.data as { opaque?: boolean }).opaque).toBe(true)
  })

  it('이미 hidden:true가 섞여있는 라이브 노드 배열에서 opaque를 해제하면 hidden:false로 복원한다(toggleLayerOpaque 재사용 시나리오)', () => {
    const nodes = makeChain('domain')
    const opaqueApplied = applyLayerModeNodeVisibility(nodes, new Set(['domain']))

    // 토글 OFF — 같은 함수를 다시 호출(라이브 배열 재사용, applyPresetConfig처럼 매번 새 layoutNodes가 아님)
    const restored = applyLayerModeNodeVisibility(opaqueApplied, new Set())

    const byId = new Map(restored.map((n) => [n.id, n]))
    expect(byId.get('group-domain')?.hidden).toBe(false)
    expect(byId.get('file-domain')?.hidden).toBe(false)
    expect(byId.get('func-domain')?.hidden).toBe(false)
  })
})

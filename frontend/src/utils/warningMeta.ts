// 경고 타입별 색상/severity 메타 및 라벨·설명 조회 — WarningPanel.tsx에서 분리(react-refresh/only-export-components)
import i18n from '../i18n'

// label/desc는 번역 대상이라 getWarningLabel/getWarningDesc로 분리 — color/severity만 정적 데이터로 유지
export const WARNING_META: Record<string, { color: string; severity: string }> = {
  CYCLIC_IMPORT:                { color: '#f97316', severity: 'HIGH'   },
  BROKEN_INTERFACE_CHAIN:       { color: '#ef4444', severity: 'MEDIUM' },
  ASYNC_SELF_CALL:              { color: '#eab308', severity: 'HIGH' },
  DB_LAYER_BYPASS:              { color: '#8b5cf6', severity: 'HIGH'   },
  CROSS_CONTEXT_IMPORT:         { color: '#06b6d4', severity: 'HIGH'   },
  CROSS_FEATURE_IMPORT:         { color: '#0ea5e9', severity: 'HIGH'   },
  FEATURE_LAYER_VIOLATION:      { color: '#6366f1', severity: 'HIGH'   },
  DOMAIN_IMPORTS_INFRA:         { color: '#ef4444', severity: 'HIGH'   },
  INTERFACES_IMPORTS_INFRA:     { color: '#f97373', severity: 'MEDIUM' },
  CROSS_DOMAIN_CALL:            { color: '#f43f5e', severity: 'MEDIUM' },
  MISSING_CONVERTER_MIGRATION:  { color: '#ec4899', severity: 'MEDIUM' },
  MISSING_TRANSACTIONAL_DELETE: { color: '#84cc16', severity: 'HIGH' },
  DEAD_CODE:                    { color: '#6b7280', severity: 'LOW'    },
  HIGH_FAN_OUT:                 { color: '#f59e0b', severity: 'LOW'    },
  LAYERED_REVERSE_DEPENDENCY:   { color: '#dc2626', severity: 'HIGH'   },
  LAYERED_BYPASS:               { color: '#14b8a6', severity: 'MEDIUM' },
  INTENT_DRIFT:                 { color: '#a855f7', severity: 'HIGH'   },
  SHARED_DATABASE_ACCESS:       { color: '#0891b2', severity: 'MEDIUM' },
  SERVICE_CALL_CHAIN:           { color: '#c026d3', severity: 'MEDIUM' },
}

// 경고 타입 라벨 — React 컴포넌트 밖(graphLayout.ts의 MD 내보내기)에서도 호출되므로 훅 대신 i18n 인스턴스 직접 사용
export function getWarningLabel(type: string): string {
  return i18n.t(`warningPanel.types.${type}.label`, { ns: 'workspace', defaultValue: type })
}

// 경고 타입 설명 — 위와 동일한 이유로 훅 대신 i18n 인스턴스 직접 사용
export function getWarningDesc(type: string): string {
  return i18n.t(`warningPanel.types.${type}.desc`, { ns: 'workspace', defaultValue: '' })
}

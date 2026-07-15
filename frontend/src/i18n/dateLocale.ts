// 현재 i18n 언어에 맞는 Intl 로케일 문자열 반환 — toLocaleDateString 등에 사용
import i18n from './index'

export function currentDateLocale(): string {
  return i18n.language.startsWith('ko') ? 'ko-KR' : 'en-US'
}

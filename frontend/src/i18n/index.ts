// i18next 초기화 — 브라우저 언어 자동감지 + localStorage 수동 선택 저장
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import commonKo from './locales/ko/common.json'
import commonEn from './locales/en/common.json'
import landingKo from './locales/ko/landing.json'
import landingEn from './locales/en/landing.json'
import miscKo from './locales/ko/misc.json'
import miscEn from './locales/en/misc.json'
import legalKo from './locales/ko/legal.json'
import legalEn from './locales/en/legal.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      ko: { common: commonKo, landing: landingKo, misc: miscKo, legal: legalKo },
      en: { common: commonEn, landing: landingEn, misc: miscEn, legal: legalEn },
    },
    fallbackLng: 'ko',
    supportedLngs: ['ko', 'en'],
    ns: ['common', 'landing', 'misc', 'legal'],
    defaultNS: 'common',
    detection: {
      // localStorage에 사용자가 직접 고른 값이 있으면 그걸 우선, 없으면 브라우저 언어 감지
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'codeprint_lang',
      caches: ['localStorage'],
    },
    interpolation: { escapeValue: false },
  })

export default i18n

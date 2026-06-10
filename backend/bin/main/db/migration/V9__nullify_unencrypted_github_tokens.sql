-- AES 도입(V6) 이전에 저장된 평문 토큰 일괄 제거
-- 판별 기준: 표준 Base64 문자셋(A-Z a-z 0-9 + / =) 외 문자 포함 → 미암호화 토큰
-- 해당 계정은 다음 GitHub OAuth 로그인 시 AES 암호화 토큰으로 자동 재저장됨
UPDATE users
SET github_access_token = NULL
WHERE github_access_token IS NOT NULL
  AND github_access_token ~ '[^A-Za-z0-9+/=]';

-- github_access_token 컬럼명 변경 — AES 암호화 적용 명시 (V6에서 @Convert 추가, V9에서 평문 데이터 제거 완료)
-- 컬럼명 규칙: 암호화 저장 컬럼은 _encrypted 접미사 사용
ALTER TABLE users RENAME COLUMN github_access_token TO github_access_token_encrypted;

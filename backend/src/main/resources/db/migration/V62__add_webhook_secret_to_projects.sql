-- PR 게이트 셀프서비스 연결용 프로젝트별 webhook 서명 시크릿 (연결 전엔 NULL)
ALTER TABLE projects ADD COLUMN webhook_secret VARCHAR(64);

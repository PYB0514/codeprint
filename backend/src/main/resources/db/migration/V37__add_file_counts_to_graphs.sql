-- 대형 레포 분석 절단 안내용 — 분석된 파일 수와 전체 대상 파일 수 (기존 그래프는 NULL = 배너 미표시)
ALTER TABLE graphs ADD COLUMN analyzed_file_count INTEGER;
ALTER TABLE graphs ADD COLUMN total_file_count INTEGER;

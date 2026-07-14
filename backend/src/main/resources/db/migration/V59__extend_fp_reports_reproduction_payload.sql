-- 오탐 신고에 재현 검증용 구조적 필드(발생 위치·메시지)와 최선노력 코드 스니펫을 추가 — 자가개선 루프 선결 구성요소 ③
-- code_snippet은 GitHub 공개 레포에서만 채워짐(비공개 레포·로컬 경로는 null) — 신고 자체는 항상 성공해야 하므로 전부 nullable

ALTER TABLE fp_reports
    ADD COLUMN message TEXT,
    ADD COLUMN file_path VARCHAR(500),
    ADD COLUMN line INT,
    ADD COLUMN col INT,
    ADD COLUMN end_col INT,
    ADD COLUMN code_snippet TEXT;

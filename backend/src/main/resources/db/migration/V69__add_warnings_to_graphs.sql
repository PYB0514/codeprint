-- 그래프 구조 경고(20종) 사전계산 결과 저장 — 콜드스타트 시 detect() 재계산(엣지 수만 개) 회피용
-- nullable: 기존 그래프는 null(미계산), 최초 조회 시 지연 계산되어 채워짐
ALTER TABLE graphs ADD COLUMN warnings jsonb;

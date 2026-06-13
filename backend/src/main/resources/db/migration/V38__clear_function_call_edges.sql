-- FUNCTION_CALL 엣지 identifier 체계 변경 (B-8) — callee 파일명 포함으로 재생성 필요
DELETE FROM graph_edges WHERE edge_type = 'FUNCTION_CALL';

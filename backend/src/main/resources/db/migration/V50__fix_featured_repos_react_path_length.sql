-- facebook/react는 Windows에서 git clone 시 깊은 테스트 픽스처 경로가 MAX_PATH를 넘어 체크아웃 실패 → axios/axios로 교체
-- project_id도 함께 초기화 — 이미 생성된 프로젝트는 이름·URL이 facebook/react로 고정돼 있어 재사용 불가, 다음 갱신 시 새로 생성
UPDATE featured_repos SET repo_full_name = 'axios/axios', project_id = NULL, stars = NULL, description = NULL
WHERE repo_full_name = 'facebook/react';

-- 요금제 재편(Pro/Team 3단계 → Desktop 라이센스 단일)에 따라 기존 유료 플랜 값을 DESKTOP으로 통합
update users
set plan = 'DESKTOP'
where plan in ('PRO', 'TEAM_STARTER', 'TEAM_GROWTH', 'TEAM_BUSINESS');

update teams
set plan = 'DESKTOP'
where plan in ('PRO', 'TEAM_STARTER', 'TEAM_GROWTH', 'TEAM_BUSINESS');

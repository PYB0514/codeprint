// 서비스 버전별 패치노트를 공개하는 페이지
import AppHeader from '../components/AppHeader'

interface Release {
  version: string
  date: string
  title: string
  type: 'major' | 'feature' | 'fix' | 'infra'
  items: { category: string; desc: string }[]
}

const RELEASES: Release[] = [
  {
    version: 'v0.34.0',
    date: '2026-06-11',
    title: '그래프 노드 코멘트',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'node_comments 테이블(V30) + GET/POST/DELETE /api/graphs/:id/nodes/:nodeId/comments API 추가' },
      { category: '프론트', desc: '함수 노드 클릭 사이드바 하단 코멘트 섹션 — 작성·삭제·목록 표시' },
    ],
  },
  {
    version: 'v0.33.0',
    date: '2026-06-11',
    title: '유저 프로필 — 공개 프로젝트 목록',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GET /api/users/:id/projects — 공개 프로젝트 목록 API 추가' },
      { category: '프론트', desc: '유저 프로필 페이지에 공개 프로젝트 섹션 추가 — 클릭 시 공유 그래프로 이동' },
    ],
  },
  {
    version: 'v0.32.0',
    date: '2026-06-11',
    title: '경고 패턴 확장 (데드 코드·과도한 의존)',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'DEAD_CODE 경고 — 아무 곳에서도 호출되지 않는 함수 감지 (API 엔드포인트·생성자·테스트 제외)' },
      { category: '백엔드', desc: 'HIGH_FAN_OUT 경고 — 10개 초과 함수 호출 시 단일 책임 원칙 위반 가능성 경고' },
      { category: '프론트', desc: 'WarningPanel에 두 신규 타입 레이블·색상 추가' },
    ],
  },
  {
    version: 'v0.31.0',
    date: '2026-06-11',
    title: 'Web Push 알림',
    type: 'feature',
    items: [
      { category: '프론트', desc: '쪽지·설정 페이지에 "브라우저 푸시" 토글 추가 — 브라우저 닫혀도 알림 수신' },
      { category: '프론트', desc: 'Service Worker (sw.js) 등록 — Push API 이벤트 수신 및 클릭 시 /messages 이동' },
      { category: '백엔드', desc: 'push_subscriptions 테이블 (V29 마이그레이션) + VAPID 기반 구독 등록·해제 API' },
      { category: '백엔드', desc: 'DM 전송 시 수신자 구독에 Web Push 발송 (알림 수신 설정 ON인 경우)' },
    ],
  },
  {
    version: 'v0.30.0',
    date: '2026-06-11',
    title: 'AI 코드 생성',
    type: 'feature',
    items: [
      { category: '프론트', desc: '우측 사이드바 AI 섹션에 "코드" 버튼 추가 — 선택 함수의 코드 스텁을 AI가 생성' },
      { category: '프론트', desc: '생성된 코드는 녹색 코드블록으로 표시, 설명과 코드를 동시에 확인 가능' },
      { category: '백엔드', desc: 'POST /api/ai/generate-code — 함수명·호출 컨텍스트 기반 코드 스텁 생성 API' },
    ],
  },
  {
    version: 'v0.29.0',
    date: '2026-06-11',
    title: 'AI 누락 패턴 감지',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 좌측 사이드바에 🤖 AI 분석 섹션 추가 — Claude API로 누락 패턴 자동 감지' },
      { category: '프론트', desc: '감지 항목: 에러 처리 누락 / 입력 검증 누락 / 테스트 누락 / 레이어 위반 / 로깅 누락 (5가지)' },
      { category: '백엔드', desc: 'POST /api/ai/graphs/{graphId}/analyze — 그래프 직렬화 후 Claude API 전송, DetectedIssue 목록 반환' },
    ],
  },
  {
    version: 'v0.28.0',
    date: '2026-06-11',
    title: '팀채팅 + 브라우저 알림',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 페이지에 💬 팀채팅 버튼 추가 — 인증 유저 간 실시간 채팅 패널' },
      { category: '프론트', desc: '백그라운드 탭에서 새 메시지 수신 시 브라우저 Notification 알림' },
      { category: '프론트', desc: '알림 설정 — 팀채팅/쪽지 알림 개별 ON/OFF (MessagesPage 설정 패널)' },
      { category: '백엔드', desc: 'user_notification_settings 테이블 (V28) + 알림 설정 조회·수정 API' },
      { category: '백엔드', desc: 'WebSocket 팀채팅 핸들러 추가 — 인증된 유저만 메시지 전송 가능' },
    ],
  },
  {
    version: 'v0.27.0',
    date: '2026-06-11',
    title: '유저 간 쪽지',
    type: 'feature',
    items: [
      { category: '프론트', desc: '헤더에 ✉ 쪽지 아이콘 + 읽지 않은 쪽지 badge 추가' },
      { category: '프론트', desc: '/messages 페이지 — 받은 쪽지함(대화 상대별), 대화 스레드, 읽음 처리' },
      { category: '백엔드', desc: 'direct_messages 테이블 (V27 마이그레이션) + 전송/받은함/스레드/읽음 API' },
    ],
  },
  {
    version: 'v0.26.0',
    date: '2026-06-11',
    title: '공유 그래프 실시간 채팅',
    type: 'feature',
    items: [
      { category: '프론트', desc: '공유 그래프 뷰에 💬 채팅 패널 추가 — 접속자 모두 실시간 메시지 교환 가능' },
      { category: '백엔드', desc: 'STOMP /app/graph/{graphId}/chat 채널 — 인증/비인증 모두 참여 가능' },
    ],
  },
  {
    version: 'v0.25.0',
    date: '2026-06-11',
    title: '게시글 좋아요',
    type: 'feature',
    items: [
      { category: '프론트', desc: '커뮤니티 게시글 목록 및 상세에 ♡ 좋아요 버튼 추가 — 클릭 시 실시간 카운트 반영' },
      { category: '백엔드', desc: 'post_likes 테이블 (V26 마이그레이션) + POST/DELETE /api/community/posts/{id}/like API' },
    ],
  },
  {
    version: 'v0.24.0',
    date: '2026-06-11',
    title: '팔로잉 피드',
    type: 'feature',
    items: [
      { category: '프론트', desc: '커뮤니티 페이지에 전체 / 팔로잉 탭 추가 — 팔로우한 유저의 게시글만 필터링' },
      { category: '백엔드', desc: 'GET /api/community/posts?feed=following — 팔로잉 유저 게시글 최신순 반환' },
    ],
  },
  {
    version: 'v0.23.0',
    date: '2026-06-10',
    title: '유저 팔로우',
    type: 'feature',
    items: [
      { category: '프론트', desc: '유저 프로필 페이지에 팔로우/언팔로우 버튼 추가' },
      { category: '프론트', desc: '팔로워 수 + 팔로잉 수 표시' },
      { category: '백엔드', desc: 'user_follows 테이블 (V25 마이그레이션) + POST/DELETE/GET 팔로우 API' },
    ],
  },
  {
    version: 'v0.22.0',
    date: '2026-06-10',
    title: '프로필 사진 & 배경 이미지',
    type: 'feature',
    items: [
      { category: '프론트', desc: '설정 페이지에서 프로필 사진 업로드 가능 — 헤더에 아바타 이미지 표시' },
      { category: '프론트', desc: '배경 이미지 업로드 시 사이트 전체 배경으로 적용 (로그인 유지 시 자동 복원)' },
      { category: '백엔드', desc: 'POST/DELETE /api/users/me/avatar, /api/users/me/background 엔드포인트 추가' },
      { category: '백엔드', desc: 'users 테이블에 avatar_url, graph_bg_url 컬럼 추가 (V24 마이그레이션)' },
    ],
  },
  {
    version: 'v0.21.0',
    date: '2026-06-10',
    title: '노드 배경색 커스터마이징',
    type: 'feature',
    items: [
      { category: '프론트', desc: '노드 클릭 시 우측 사이드바에 색상 팔레트 표시 — 9가지 색상으로 노드 배경색 변경 가능' },
      { category: '백엔드', desc: 'node_styles 테이블 + PUT /api/graphs/{graphId}/nodes/{nodeId}/style 엔드포인트 추가' },
      { category: '백엔드', desc: '그래프 조회 응답에 bgColor 필드 포함 — 재로드 시에도 색상 유지' },
    ],
  },
  {
    version: 'v0.20.0',
    date: '2026-06-10',
    title: 'API 엔드포인트 노드 클릭 시 흐름 재생 시작',
    type: 'feature',
    items: [
      { category: '프론트', desc: 'API_ENDPOINT 노드 클릭 시 callee 호출 체인 사이드바 + 흐름 재생 자동 시작 — 이전에는 클릭해도 아무 반응 없었음' },
    ],
  },
  {
    version: 'v0.18.3',
    date: '2026-06-10',
    title: 'HTTP 캐시·Docker 최적화',
    type: 'infra',
    items: [
      { category: '백엔드', desc: 'Cache-Control 헤더 추가 — 그래프 API 5분 브라우저 캐시(private), 공개 그래프 5분 public 캐시' },
      { category: '백엔드', desc: 'gzip 압축 활성화 — JSON/JS/CSS 응답 1KB 이상 자동 압축' },
      { category: '인프라', desc: 'Dockerfile 의존성 레이어 분리 — build.gradle 변경 없으면 Gradle 의존성 캐시 재사용' },
      { category: '인프라', desc: 'docker-compose backend profile 추가 — docker compose --profile backend up 으로 선택적 실행 가능' },
    ],
  },
  {
    version: 'v0.18.2',
    date: '2026-06-10',
    title: '성능 최적화 4종 — 번들 분할·서버 캐시·N+1 해소·React.memo',
    type: 'infra',
    items: [
      { category: '프론트', desc: 'React.lazy 코드 스플리팅 — 초기 번들 834KB → 283KB (gzip 257→93KB)' },
      { category: '프론트', desc: '대시보드·그래프 페이지 prefetch — 랜딩 페이지 로드 후 백그라운드 사전 로딩' },
      { category: '백엔드', desc: 'Caffeine 인메모리 캐시 — 그래프 노드·엣지 10분 TTL, 최대 200개 그래프 캐시' },
      { category: '백엔드', desc: 'N+1 쿼리 해소 — 그래프 버전 목록 API에서 분석 정보 개별 조회 → 일괄 조회로 개선' },
      { category: '프론트', desc: 'React.memo — FileNode·GroupNode·SectionNode 불필요한 리렌더링 방지' },
    ],
  },
  {
    version: 'v0.18.1',
    date: '2026-06-10',
    title: '흐름 재생 UX 4종 개선 — 분기 애니메이션·도메인 흐름·결과 카드',
    type: 'fix',
    items: [
      { category: '프론트', desc: '분기 선택 후 재생 시 분기 노드부터 애니메이션 — 이전에는 마지막 스텝으로 즉시 점프하던 버그 수정' },
      { category: '프론트', desc: '도메인 범례 흐름 목록 — 진입점 함수 나열 대신 실제 호출 체인 미리보기(A → B → C) 표시' },
      { category: '프론트', desc: '종착점 결과 카드 추가 — 생성자 종착 시 "X 객체가 반환됩니다", DB 종착 시 읽기/쓰기/수정/삭제 결과 메시지 표시' },
      { category: '프론트', desc: '흐름 재생 시작 시 도메인 요약 사이드바 자동 닫힘 — 재생 컨트롤이 가려지지 않도록' },
    ],
  },
  {
    version: 'v0.18.0',
    date: '2026-06-10',
    title: '도메인 범례 클릭 시 흐름 재생 목록 표시',
    type: 'feature',
    items: [
      { category: '프론트', desc: '도메인 범례 버튼 클릭 시 해당 도메인의 진입점 흐름 목록을 사이드바에 표시' },
      { category: '프론트', desc: '흐름 클릭 시 해당 진입점에서 자동 재생 시작 + fitView 이동' },
    ],
  },
  {
    version: 'v0.17.2',
    date: '2026-06-10',
    title: '흐름 재생 속도·outline 누적 버그 수정',
    type: 'fix',
    items: [
      { category: '프론트', desc: '재생 간격 1.0초 → 1.2초 — 빠른 전환으로 인한 시각적 혼란 개선' },
      { category: '프론트', desc: '노드 outline이 다음 흐름 시작 시 누적되던 버그 수정 — 항상 초기화 후 적용' },
    ],
  },
  {
    version: 'v0.17.1',
    date: '2026-06-10',
    title: '흐름 재생 분기 선택 UX 개선 — 클릭 후 재생 버튼으로 확정',
    type: 'fix',
    items: [
      { category: '프론트', desc: '분기 클릭 즉시 재생 → 클릭으로 경로 미리보기 후 ▶ 버튼으로 확정하는 2단계 UX로 변경' },
      { category: '프론트', desc: '분기 대기 중 취소 버튼 추가 — 다른 분기 선택 또는 현재 경로 유지 가능' },
    ],
  },
  {
    version: 'v0.17.0',
    date: '2026-06-10',
    title: '흐름 재생 전면 재설계 — 뷰포트 전환·분기 트리·DB 연결',
    type: 'feature',
    items: [
      { category: '프론트', desc: '재생 중 fitView 자동 전환 — 현재 스텝 노드가 항상 화면 중앙에 위치' },
      { category: '프론트', desc: '분기 트리 패널 추가 — 호출 분기를 시각적 트리로 표시, 클릭으로 경로 전환' },
      { category: '프론트', desc: 'DB 연결 흐름 포함 — DB_READ/DB_WRITE 엣지를 따라 DB 테이블까지 재생 경로 확장' },
      { category: '프론트', desc: '진입점 자동 탐색 — 선택 노드에 callers가 없으면 upstream을 역추적해 최상위 진입점에서 시작' },
    ],
  },
  {
    version: 'v0.16.0',
    date: '2026-06-10',
    title: '도메인 뷰 섹션 클릭 시 흐름 사이드바 + 타이틀 스케일 수정',
    type: 'feature',
    items: [
      { category: '프론트', desc: '도메인 뷰 섹션 박스 클릭 시 해당 도메인의 API/진입점 함수 목록과 흐름 재생 버튼 사이드바 표시' },
      { category: '프론트', desc: '섹션 타이틀 역스케일 제거 — 줌 아웃 시 타이틀이 비대해지던 버그 수정' },
    ],
  },
  {
    version: 'v0.15.8',
    date: '2026-06-10',
    title: '배포 환경 로그인 불가 수정 — SameSite=None 쿠키',
    type: 'fix',
    items: [
      { category: '백엔드', desc: 'ResponseCookie SameSite=None; Secure 추가 — HTTPS 배포 환경에서 cross-site 쿠키 전달 실패 수정' },
    ],
  },
  {
    version: 'v0.15.7',
    date: '2026-06-10',
    title: 'Refresh Token 메커니즘 도입 — 세션 자동 갱신',
    type: 'infra',
    items: [
      { category: '보안', desc: 'Refresh Token 발급 — 로그인 시 7일 만료 HttpOnly 쿠키로 발급, SHA-256 해시만 DB 저장' },
      { category: '보안', desc: 'Token Rotation 적용 — 갱신 시 기존 토큰 폐기 후 신규 발급, 재사용 불가' },
      { category: '프론트', desc: 'axios 인터셉터 추가 — 401 응답 시 자동 토큰 갱신 후 원래 요청 재시도' },
    ],
  },
  {
    version: 'v0.15.6',
    date: '2026-06-10',
    title: '로그아웃 후 새로고침 시 로그인 유지되는 버그 수정',
    type: 'fix',
    items: [
      { category: '보안', desc: '로그아웃 쿠키 삭제 실패 버그 수정 — Vite 프록시 origin 불일치로 Set-Cookie가 무시되던 문제 해결' },
      { category: '백엔드', desc: '/api/auth/logout-redirect 엔드포인트 추가 — 브라우저가 백엔드에 직접 접속해 쿠키 만료 후 메인으로 리다이렉트' },
    ],
  },
  {
    version: 'v0.15.5',
    date: '2026-06-10',
    title: '도메인 뷰 섹션 박스 저줌 가시성 개선',
    type: 'fix',
    items: [
      { category: '프론트', desc: '섹션 박스 보더 투명도 33% → 60%, 실선으로 변경 — 줌아웃 시 서브픽셀 렌더링 문제 해결' },
      { category: '프론트', desc: '섹션 라벨 크기 13px → 18px, 투명도 80% → 93% — 낮은 줌에서도 선명하게 표시' },
    ],
  },
  {
    version: 'v0.15.4',
    date: '2026-06-10',
    title: '흐름 재생 UX 단순화 — 목적 중심 표시',
    type: 'fix',
    items: [
      { category: '프론트', desc: '흐름 제목: 루트 노드의 한국어 주석을 그대로 표시 (개발 용어 없음)' },
      { category: '프론트', desc: '도메인 뱃지: 파일 경로에서 자동 추출 (user / graph / auth 등)' },
      { category: '프론트', desc: '스텝 점: 전체 단계 수·현재 위치 한눈에 파악, 점 클릭으로 특정 단계 이동' },
      { category: '프론트', desc: '현재 단계 카드: 함수 이름 + 파일명만 표시 — DDD 레이어 바·edge type 제거' },
      { category: '프론트', desc: '분기 버튼 레이블을 "이 다음 경로를 선택하세요"로 변경 (전문 용어 제거)' },
    ],
  },
  {
    version: 'v0.15.3',
    date: '2026-06-09',
    title: '도메인 뷰 1열 레이아웃 + 카스케이드 드래그 수정',
    type: 'fix',
    items: [
      { category: '프론트', desc: '도메인 뷰 섹션 박스를 1열 세로 배치로 변경 — 큰 도메인도 잘리지 않음' },
      { category: '프론트', desc: '섹션 박스 드래그 시 내부 노드가 함께 이동 (parentId 기반 카스케이드 수정)' },
      { category: '프론트', desc: '공통(common) 파일 재매핑 — FILENAME_DOMAIN_MAP 키워드 보강으로 오분류 감소' },
      { category: '프론트', desc: '혼합 그룹 도메인별 분리 — 단일 도메인 파일만 해당 섹션에 배치' },
    ],
  },
  {
    version: 'v0.15.2',
    date: '2026-06-10',
    title: 'JWT HttpOnly 쿠키 전환 — XSS 보안 강화',
    type: 'infra',
    items: [
      { category: '보안', desc: 'JWT를 localStorage에서 HttpOnly 쿠키로 이전 — XSS로 토큰 탈취 불가' },
      { category: '백엔드', desc: 'OAuth2 로그인 성공 시 쿠키 발급 후 /dashboard 직접 리다이렉트' },
      { category: '백엔드', desc: '/api/auth/logout 엔드포인트 추가 — 쿠키 만료 처리' },
      { category: '프론트', desc: '18개 파일의 authHeaders() 함수 및 localStorage JWT 참조 전부 제거' },
    ],
  },
  {
    version: 'v0.15.1',
    date: '2026-06-09',
    title: '토스페이먼츠 Pro 결제 통합',
    type: 'fix',
    items: [
      { category: '백엔드', desc: 'Stripe 제거 — 토스페이먼츠 결제 승인 API로 전환 (Pro 플랜 9,900원)' },
      { category: '백엔드', desc: 'toss_payment_orders 테이블 추가 — 결제 주문 멱등성 처리 (Flyway V10)' },
      { category: '프론트', desc: '대시보드 "업그레이드" 버튼에서 토스페이먼츠 결제창 직접 호출' },
    ],
  },
  {
    version: 'v0.15.0',
    date: '2026-06-09',
    title: '그래프 페이지에서 바로 재분석',
    type: 'feature',
    items: [
      { category: '프론트', desc: '"새 커밋" 배너에서 대시보드로 이동 없이 바로 재분석 시작 — 완료 후 그래프 자동 새로고침' },
    ],
  },
  {
    version: 'v0.14.2',
    date: '2026-06-09',
    title: '도메인 뷰 섹션 라벨 가시성 개선',
    type: 'fix',
    items: [
      { category: '프론트', desc: '도메인 뷰 섹션 라벨이 줌 아웃 시에도 일정한 크기로 표시됨 — useViewport 역스케일 적용' },
    ],
  },
  {
    version: 'v0.14.1',
    date: '2026-06-09',
    title: '도메인 뷰 재설계 — 바운디드 컨텍스트 단위 박스',
    type: 'fix',
    items: [
      { category: '프론트', desc: '도메인 뷰에서 중간 레이어 그룹 박스 제거 — 파일 노드가 바운디드 컨텍스트 섹션에 직접 배치됨' },
      { category: '프론트', desc: 'User / Project / Graph / Analysis / Community 등 DDD 도메인 단위로 시각화' },
    ],
  },
  {
    version: 'v0.14.0',
    date: '2026-06-09',
    title: '경고 MD 내보내기',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '뷰 프리셋 기본값 허브 → 도메인으로 변경 (Flyway V19 마이그레이션 포함)' },
      { category: '프론트', desc: '경고 패널 헤더에 ↓ MD 버튼 — 경고 목록을 마크다운 리포트로 다운로드' },
    ],
  },
  {
    version: 'v0.13.1',
    date: '2026-06-09',
    title: '검색 하이라이트 — 일치 노드 강조',
    type: 'fix',
    items: [
      { category: '프론트', desc: '검색어 입력 시 일치 노드만 선명하게, 나머지 노드는 반투명 처리' },
      { category: '프론트', desc: '검색 지우면 전체 그래프 원복 — 빠른 위치 파악에 유용' },
    ],
  },
  {
    version: 'v0.13.0',
    date: '2026-06-09',
    title: '노드 타입 가시성 필터',
    type: 'feature',
    items: [
      { category: '프론트', desc: '좌측 사이드바 노드 섹션에 FILE / FUNCTION / DB / API 타입별 표시/숨김 토글 추가' },
      { category: '프론트', desc: '대규모 그래프에서 원하는 노드 타입만 집중해서 볼 수 있음' },
    ],
  },
  {
    version: 'v0.12.1',
    date: '2026-06-09',
    title: '공유 그래프에 런타임 경고 표시',
    type: 'fix',
    items: [
      { category: '백엔드', desc: '공개 공유 그래프 API에 경고 감지 추가 — 비인증 뷰어에서도 경고 확인 가능' },
      { category: '프론트', desc: '공유 그래프 페이지 좌측에 런타임 경고 패널 표시 (경고 없으면 숨김)' },
    ],
  },
  {
    version: 'v0.12.0',
    date: '2026-06-09',
    title: '그래프 키보드 단축키',
    type: 'feature',
    items: [
      { category: '프론트', desc: '/ — 검색창 포커스, Esc — 사이드바 닫기, F — 전체 화면 맞춤, L — 라벨 이름/주석 전환' },
      { category: '프론트', desc: '상단 바 ? 버튼 — 호버 시 단축키 목록 팝업 표시' },
    ],
  },
  {
    version: 'v0.11.1',
    date: '2026-06-09',
    title: '경고 항목 클릭 → 노드 이동',
    type: 'fix',
    items: [
      { category: '프론트', desc: '경고 패널 항목 클릭 시 해당 노드로 그래프 자동 이동 — 경고 원인 노드를 한 클릭으로 확인' },
    ],
  },
  {
    version: 'v0.11.0',
    date: '2026-06-09',
    title: '노드 검색',
    type: 'feature',
    items: [
      { category: '프론트', desc: '좌측 사이드바에 노드 검색 입력란 추가 — 파일명·함수명·한국어 주석으로 실시간 필터링' },
      { category: '프론트', desc: '검색 결과 클릭 시 해당 노드로 그래프 자동 이동 (fitView)' },
    ],
  },
  {
    version: 'v0.10.6',
    date: '2026-06-09',
    title: '경고 엣지 황색 하이라이트',
    type: 'fix',
    items: [
      { category: '백엔드', desc: '경고 응답에 edgeIds 필드 추가 — CYCLIC_IMPORT, ASYNC_SELF_CALL, DB_LAYER_BYPASS, CROSS_CONTEXT_IMPORT 경고의 관련 엣지 ID 포함' },
      { category: '프론트', desc: '경고 관련 엣지에 황색 스트로크 + 애니메이션 표시 — 경고 원인 엣지를 그래프에서 즉시 식별' },
    ],
  },
  {
    version: 'v0.10.5',
    date: '2026-06-09',
    title: '경고 노드 클릭 시 사이드바 상세 표시',
    type: 'fix',
    items: [
      { category: '프론트', desc: '경고가 있는 노드 클릭 시 우측 사이드바에 해당 경고 상세 표시 — 경고 타입, 메시지, 색상 구분' },
    ],
  },
  {
    version: 'v0.10.4',
    date: '2026-06-09',
    title: '경고 패널 타입별 그룹핑 UI',
    type: 'fix',
    items: [
      { category: '프론트', desc: '경고 패널을 타입별로 그룹핑 — CYCLIC_IMPORT/BROKEN_INTERFACE_CHAIN 등 6가지 타입별 섹션, 접기/펼치기 토글' },
      { category: '프론트', desc: '각 경고 타입에 색상 레이블과 설명 추가 — 경고 원인을 즉시 파악 가능' },
    ],
  },
  {
    version: 'v0.10.3',
    date: '2026-06-09',
    title: '@Convert 컨버터 마이그레이션 누락 감지',
    type: 'fix',
    items: [
      { category: '백엔드', desc: 'MISSING_CONVERTER_MIGRATION 경고 — @Convert 어노테이션이 있는 JPA Entity 컬럼 감지, 기존 데이터 Flyway 마이그레이션 누락 가능성 경고' },
      { category: '백엔드', desc: 'StaticCodeAnalyzer: @Convert 어노테이션 붙은 필드 추출, ColumnInfo.hasConverter 필드 추가' },
    ],
  },
  {
    version: 'v0.10.2',
    date: '2026-06-09',
    title: 'DDD 컨텍스트 경계 위반 감지',
    type: 'fix',
    items: [
      { category: '백엔드', desc: 'CROSS_CONTEXT_IMPORT 경고 — application/{contextA}가 domain/{contextB}를 직접 IMPORT할 때 감지 (DDD 컨텍스트 간 직접 객체 참조 위반)' },
    ],
  },
  {
    version: 'v0.10.1',
    date: '2026-06-09',
    title: '런타임 경고 패턴 확장',
    type: 'fix',
    items: [
      { category: '백엔드', desc: '@Async 자기 호출 감지 — 같은 파일 내 @Async 메서드 직접 호출 시 경고 (Spring 프록시 우회 패턴)' },
      { category: '백엔드', desc: 'DB 레이어 우회 감지 — interfaces/application 레이어가 infrastructure/persistence를 직접 호출 시 경고' },
      { category: '백엔드', desc: '정적 분석 엔진에 @Async 메서드 추출 추가 — StaticCodeAnalyzer → ParsedFile → GraphBuilder 파이프라인' },
    ],
  },
  {
    version: 'v0.10.0',
    date: '2026-06-09',
    title: '런타임 경고 감지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '순환 의존 감지 — IMPORT 엣지 DFS 탐지, 사이클 발견 시 경고 생성' },
      { category: '백엔드', desc: '인터페이스 체인 끊김 감지 — 인터페이스 메서드에 구현체 엣지 없으면 경고' },
      { category: '프론트', desc: '경고 노드 황색 테두리 — 문제 있는 파일/함수 즉시 식별' },
      { category: '프론트', desc: '경고 패널 — 좌측 사이드바에 경고 목록 표시 (경고 있을 때만 노출)' },
    ],
  },
  {
    version: 'v0.9.0',
    date: '2026-06-09',
    title: '실시간 협업',
    type: 'feature',
    items: [
      { category: '프론트', desc: '협업 패널 — 그래프 헤더에서 세션 생성 및 초대 코드(8자리) 공유' },
      { category: '프론트', desc: '다른 참가자 커서 실시간 오버레이 표시 (이름 라벨 + 색상)' },
      { category: '프론트', desc: '참가자 아바타 — 현재 세션에 참가한 사용자 목록 표시' },
      { category: '프론트', desc: '/collab/join 페이지 — 초대 코드 입력 후 그래프에 바로 참가' },
      { category: '백엔드', desc: 'STOMP WebSocket — 커서 위치·노드 선택·입장/퇴장 이벤트 브로드캐스트' },
      { category: '백엔드', desc: 'Free 플랜 최대 6명 협업 제한 (Pro 연동 예정)' },
    ],
  },
  {
    version: 'v0.8.0',
    date: '2026-06-08',
    title: '계층형 / 도메인 이중 뷰',
    type: 'feature',
    items: [
      { category: '프론트', desc: '도메인 뷰 추가 — 바운디드 컨텍스트(project/user/graph/analysis 등) 단위로 파일 그룹핑' },
      { category: '프론트', desc: '계층형 ↔ 도메인 뷰 토글 버튼 — 좌측 사이드바 레이아웃 섹션에서 전환 가능' },
      { category: '프론트', desc: '도메인 뷰 범례 — 10개 바운디드 컨텍스트별 색상 표시' },
      { category: '프론트', desc: '허브 프리셋 제거, 범례 레이블 계층형 레이어로 정정' },
    ],
  },
  {
    version: 'v0.7.0',
    date: '2026-06-08',
    title: '후원 기능 & 흐름 재생 재설계',
    type: 'feature',
    items: [
      { category: '결제', desc: '토스페이먼츠 후원 기능 — /donate 페이지, 금액 선택 + 결제 모달, 후원 목록 공개' },
      { category: '프론트', desc: '흐름 재생 재설계 — 노드 중심 스텝(엣지는 메타데이터), 레이어 전환 레이블(호출/HTTP 요청/DB 저장)' },
      { category: '프론트', desc: 'AppHeader 자체 페칭으로 전환 — 모든 페이지에서 일관된 헤더' },
      { category: '버그 수정', desc: 'OAuth2 GitHub 로그인 오류 수정 — SessionCreationPolicy.STATELESS → IF_REQUIRED' },
      { category: '버그 수정', desc: 'PKIX SSL 오류 수정 — Windows-ROOT 트러스트스토어 적용' },
    ],
  },
  {
    version: 'v0.6.0',
    date: '2026-06-07',
    title: 'AI 코드 설명 — Claude / ChatGPT / Gemini',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'Claude / ChatGPT / Gemini 다중 제공자 지원 — API 키 등록·교체·삭제, AES-GCM 암호화 저장' },
      { category: '프론트', desc: '/settings 페이지 — AI 제공자별 API 키 관리 카드 UI' },
      { category: '프론트', desc: '그래프 함수 노드 사이드바에 AI 설명 요청 섹션 추가' },
      { category: '인프라', desc: 'Dockerfile에 git 설치 추가 — Railway 배포 환경 분석 실패 수정' },
    ],
  },
  {
    version: 'v0.5.0',
    date: '2026-06-06',
    title: '관리자 시스템 & 커뮤니티 고도화',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '관리자 역할(ADMIN) 시스템 — 사용자 통계, 계정 정지/복구, /admin/** 보호' },
      { category: '프론트', desc: '/admin 대시보드 — 통계 카드, 사용자 목록, 정지/복구 버튼' },
      { category: '백엔드', desc: '공지사항 시스템 — 어드민 작성, 전체 사용자 상단 배너 표시' },
      { category: '프론트', desc: '커뮤니티 게시글 검색 (제목/본문, 300ms 디바운스)' },
      { category: '프론트', desc: '그래프 페이지 온보딩 투어 (react-joyride, 5단계, 완료 기억)' },
      { category: '법적', desc: '/terms, /privacy, /contact 페이지, 쿠키 동의 배너 (GDPR/개보법 대응)' },
      { category: '테스트', desc: 'AnalysisResult 상태 전이 + User/UserPlan 경계 조건 단위 테스트 17개' },
    ],
  },
  {
    version: 'v0.4.0',
    date: '2026-06-05',
    title: '흐름 시각화 & 소셜 기능',
    type: 'feature',
    items: [
      { category: '프론트', desc: '흐름 자동 시각화 — 함수 호출 경로 재생 컨트롤, 분기 트리 패널, 분기점 자동 일시정지' },
      { category: '프론트', desc: '그래프 뷰 프리셋 4슬롯 — 현재 뷰포트/노드 위치 저장·불러오기' },
      { category: '프론트', desc: '공유 그래프 프리셋 연동 — ?preset={slot}&userId={userId} URL로 저장 뷰 공유' },
      { category: '프론트', desc: '게시글 북마크(☆/★) + /bookmarks 페이지' },
      { category: '프론트', desc: '/users/:id 공개 프로필 페이지 — GitHub 아바타, 작성 게시글 목록' },
      { category: '프론트', desc: 'GitHub 재연결 UX — 토큰 만료 시 노란 배너 + 1클릭 재연결' },
      { category: '백엔드', desc: 'FUNCTION_CALL 인터페이스→구현체 우선 매핑, 회귀 테스트 추가' },
    ],
  },
  {
    version: 'v0.3.0',
    date: '2026-06-04',
    title: '그래프 심화 분석 & 보안 강화',
    type: 'feature',
    items: [
      { category: '분석', desc: 'C#, Ruby, PHP, Swift 지원 추가 — 총 11개 언어' },
      { category: '분석', desc: 'DB 구조 시각화 — @Entity/Prisma 추출, DB_TABLE 노드, DB_READ/WRITE/CREATE/UPDATE/DELETE 엣지 색상 구분' },
      { category: '분석', desc: 'API_CALL 엣지 — 프론트 axios 호출 → 백엔드 컨트롤러 연결' },
      { category: '프론트', desc: '그래프 버전 diff — 두 분석 결과 노드/엣지 변경 시각화' },
      { category: '프론트', desc: 'Primary branch 설정 — ★ 버튼, freshness 뱃지 (최신 / 업데이트 필요)' },
      { category: '보안', desc: '보안 헤더 6종 (CSP/HSTS/X-Frame-Options 등), 레이트 리미팅 (Bucket4j IP별)' },
      { category: '보안', desc: 'JWT 1h 단축, S3 URL 15min, 파일 10MB 제한, 소유권 검증 전면 적용' },
      { category: '인프라', desc: 'Sentry 에러 트래킹 (백엔드 + 프론트), /changelog 패치노트 페이지' },
    ],
  },
  {
    version: 'v0.2.0',
    date: '2026-06-03',
    title: '배포 & 커뮤니티',
    type: 'infra',
    items: [
      { category: '인프라', desc: 'GitHub Actions CI/CD — PR마다 컴파일+타입체크, main 머지 시 Railway/Vercel 자동 배포' },
      { category: '인프라', desc: '브랜치 보호 규칙 — CI 통과 없이 main 머지 불가' },
      { category: '인프라', desc: 'AWS S3 연동 — presigned URL로 브라우저 직접 업로드, 커뮤니티 이미지 첨부' },
      { category: '백엔드', desc: 'Stripe Pro 플랜 Checkout + Webhook (결제 완료/취소 시 플랜 자동 전환)' },
      { category: '프론트', desc: '커뮤니티 게시판 — 게시글/댓글 CRUD, 그래프 연결 게시, 이미지 첨부' },
      { category: '프론트', desc: '공개/비공개 토글 + 공유 URL + 읽기 전용 그래프 뷰어 (/share/:projectId)' },
      { category: '프론트', desc: '서비스 메인 랜딩 페이지' },
    ],
  },
  {
    version: 'v0.1.0',
    date: '2026-06-02',
    title: 'MVP 출시',
    type: 'major',
    items: [
      { category: '인프라', desc: 'Spring Boot 3 + DDD 아키텍처 (domain / application / infrastructure / interfaces)' },
      { category: '인프라', desc: 'Docker PostgreSQL + Flyway 마이그레이션' },
      { category: '백엔드', desc: 'GitHub OAuth2 + JWT 인증' },
      { category: '분석', desc: 'Tree-sitter 기반 정적 코드 분석 엔진 — 파일 구조, import, 함수 추출, 한글 주석 매핑' },
      { category: '분석', desc: '비동기 분석 처리 (@Async) + 진행률 API, FUNCTION_CALL/INSTANTIATION 엣지' },
      { category: '프론트', desc: 'React Flow 시각화 — DDD 레이어 그룹핑, 계층형 레이아웃, 엣지 호버 모달' },
      { category: '프론트', desc: '노드 라벨 ↔ 한글 주석 토글, 노드 드래그 위치 저장, 그래프 PNG 내보내기' },
      { category: '프론트', desc: '브랜치 선택 분석, 엣지 전체 흐름 추적(DFS), 사이드바 리사이즈' },
    ],
  },
]

const TYPE_BADGE: Record<Release['type'], { label: string; color: string }> = {
  major: { label: 'MAJOR', color: 'bg-white text-black' },
  feature: { label: 'FEATURE', color: 'bg-gray-700 text-gray-200' },
  fix: { label: 'FIX', color: 'bg-orange-900 text-orange-200' },
  infra: { label: 'INFRA', color: 'bg-blue-900 text-blue-200' },
}

const CATEGORY_COLOR: Record<string, string> = {
  '프론트': 'text-emerald-400',
  '백엔드': 'text-blue-400',
  '인프라': 'text-yellow-400',
  '분석': 'text-purple-400',
  '보안': 'text-red-400',
  '테스트': 'text-gray-400',
  '결제': 'text-pink-400',
  '법적': 'text-gray-400',
}

// 패치노트 페이지
export default function ChangelogPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">패치노트</h1>
          <p className="text-gray-500 text-sm">Codeprint의 버전별 업데이트 내역</p>
        </div>

        <div className="flex flex-col gap-10">
          {RELEASES.map((release) => {
            const badge = TYPE_BADGE[release.type]
            return (
              <div key={release.version} className="flex gap-6">
                {/* 버전 타임라인 */}
                <div className="flex flex-col items-center">
                  <div className="w-2.5 h-2.5 rounded-full bg-gray-400 mt-1.5 shrink-0" />
                  <div className="w-px flex-1 bg-gray-800 mt-2" />
                </div>

                {/* 릴리즈 내용 */}
                <div className="flex-1 pb-2">
                  <div className="flex items-center gap-3 mb-1">
                    <span className="font-bold text-lg">{release.version}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded ${badge.color}`}>
                      {badge.label}
                    </span>
                  </div>
                  <p className="text-gray-300 font-medium mb-0.5">{release.title}</p>
                  <p className="text-gray-600 text-xs mb-4">{release.date}</p>

                  <ul className="flex flex-col gap-2">
                    {release.items.map((item, i) => (
                      <li key={i} className="flex items-start gap-2 text-sm">
                        <span className={`shrink-0 text-xs font-medium w-10 mt-0.5 ${CATEGORY_COLOR[item.category] ?? 'text-gray-400'}`}>
                          {item.category}
                        </span>
                        <span className="text-gray-400 leading-relaxed">{item.desc}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )
          })}
        </div>
      </main>
    </div>
  )
}

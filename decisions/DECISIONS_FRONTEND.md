# 프론트엔드 시행착오 & 설계 결정

---

## 네이밍 체계 확정 — "그래프 뷰어"(화면)·"스냅샷"(첨부물)·"링크 공유"(토글) (2026-07-07)

대체: PR-B(2026-07-03)의 가시성 토글 라벨 "비공개 — 링크로만 접근" → "링크 공유"로 개명. 화면명 "공유 그래프"(ShareGraphPage) → "그래프 뷰어"(GraphViewerPage).

**문제.** "공유 그래프"라는 이름이 편집 가능한 실시간 협업과 뷰어 성격의 공유를 뭉뚱그려, 사용자(소유자) 스스로 팀 유료 모델과의 충돌 여부를 혼동하는 일이 발생(2026-07-07 세션). 또 "비공개" 라벨은 "나만 본다"로 오독될 여지가 있었음 — 실제 설계는 구 `/share` 단독 링크 공유의 계승(Context96, 링크 소지자 열람 가능)이라 라벨과 기대가 어긋남.

**도달 과정.** ①"뷰어 공유 그래프" — 길어서 기각. ②"확장 스냅샷" — "확장"이 무엇의 확장인지 새 질문만 만들고 상호작용성 뉘앙스를 못 살려 기각. ③"보기 전용 그래프"·"열람본" — 정확하나 카드 라벨("📊 ___ 1")에서 무너져 기각. ④최종: **도구와 내용물을 분리** — 화면(도구)은 "그래프 뷰어"(PDF 뷰어 관습 = 읽기 전용 + 확대·탐색 가능이라는 상식 내장, 사용자 제안), 게시글 첨부물(내용물)은 기존 도메인 용어 "스냅샷" 유지(post_graph_snapshots·카드 라벨과 일치). 스냅샷의 "정지 이미지" 오해는 첫 만남 지점(공유 모달) 한 줄 설명으로 해소 — 단어 교체보다 싸고, 개발자 타깃에겐 스냅샷=시점 전체 사본(DB/VM 스냅샷)이 표준 의미.

**결과.** ShareGraphPage.tsx→GraphViewerPage.tsx(라우트 `/share/:projectId`·API·DB 값 `PRIVATE`는 하위호환 위해 불변), 관련 주석 상호참조 일괄 갱신(ChangelogPage 과거 항목은 일지형이라 보존), 토글 라벨 "링크 공유" + 선택 시 "링크가 있는 사람은 누구나 볼 수 있습니다" 캡션, 공유 모달에 스냅샷 설명 1줄 추가. Changelog v0.117.3.

---

## 요금제 개명(Pro · Desktop)·개인 결제 임시 중단 — v1.0 리뷰 제품 결정 반영 (2026-07-05)

**문제.** v1.0 UX 리뷰에서 요금제 표기와 실체의 불일치 3건 확정. ①"Desktop 라이센스"라는 이름이 미출시 데스크탑 앱을 암시(실효 혜택은 협업 인원 해제·팀 생성뿐) ②"₩9,900/월" 표기인데 실제는 1회성 결제(정기결제는 Toss 계약 대기, 2026-07-01 기지 항목) ③마이페이지 업그레이드 배너가 PR #426에서 무료로 재분류된 기능(AI·버전 히스토리)을 유료 혜택으로 광고 + 약관 제4조에도 같은 낡은 혜택 서술 잔존(#426이 랜딩만 고치고 약관을 놓침).

**결정(사용자, 4지선다 질문으로 확정).** ①이름을 "Pro · Desktop"으로 바꾸고 데스크탑 앱은 "출시 예정"으로 명시 ②개인 결제는 정기결제 도입 전까지 **임시 비활성화**(버튼 disabled + 안내 문구, 팀 결제는 유지) — "/월 유지"·"1회 결제 표기 정정" 안은 탈락(전자는 표기 부정직, 후자는 곧 정기결제로 되돌릴 표기를 두 번 바꾸는 churn) ③마이페이지 배너는 판매 중단 상태에서 존재 이유가 없고 문구도 허위라 제거(결제 재개 시 어차피 코드 변경 필요).

**결과.** LandingPage 요금제 카드 개명+비활성화+안내, MyPage 배너·handleUpgrade·Toss import 제거, TermsPage 혜택 실측 정정, TeamsPage·PaymentSuccessPage 명칭 통일. ChangelogPage 과거 릴리즈 노트의 "Desktop 라이센스" 표기는 역사 기록이라 유지. ★스코프 밖 플래그: 팀 좌석도 "/월" 표기+1회 결제로 동일한 불일치가 있으나 팀 결제는 판매 유지 결정이라 이번에 손대지 않음 — 정기결제 도입 시 함께 해소(`V1_UX_GAP_REVIEW.md` V2 참조).

---

## 경고 라벨 맵 중복이 만든 누락 버그 2건 — WARNING_META 단일 소스 통합 (2026-07-05, PR 경고 카피 재작성)

**문제.** v1.0 UX 리뷰(Fable 5)에서 경고 카피를 재작성하려고 노출 지점을 전수 조사하다 중복 소스가 만든 누락 버그 2건 발견. ①`WarningPanel.tsx`의 `WARNING_META`에 INTENT_DRIFT 항목이 없어 "아키텍처 의도" 위반 경고가 패널에 **코드명(INTENT_DRIFT) 그대로** 노출(라인 152 폴백 `{ label: type }` 경유). ②`graphLayout.ts`의 `downloadWarningsMd`가 자체 `WARNING_LABELS` 맵(8종)을 따로 들고 있어 이후 추가된 7종(DEAD_CODE·HIGH_FAN_OUT·CROSS_DOMAIN_CALL·LAYERED_* 2종·DOMAIN_IMPORTS_INFRA·INTENT_DRIFT)이 MD 리포트에서 코드명으로만 표기.

**원인.** 같은 정보(경고 타입→라벨)를 두 곳에서 관리 — 새 경고 타입을 추가할 때 WARNING_META만 갱신하고 MD 내보내기 쪽 맵은 아무도 기억하지 못하는 구조. §1 재사용 원칙이 경고한 "중복 소스 드리프트"의 전형.

**결과.** ①WARNING_META에 INTENT_DRIFT 정식 항목 추가(HIGH, #a855f7). ②`downloadWarningsMd`의 자체 맵을 삭제하고 `WARNING_META`를 import해 단일 소스화(WarningPanel→utils/ignoreRules 외 import 없음을 확인해 순환 없음). 리포트 제목 "런타임 경고 리포트"→"구조 경고 리포트" 오칭도 정정. 아울러 15종 라벨·설명을 "무엇이 문제/왜 위험/뭘 하면 되는지" 구조의 쉬운 말로 재작성하고, HowItWorksPage 가이드에 빠져 있던 5종을 추가해 15종 전부 커버.

---

## 마이페이지(/mypage) 신설 — 라우트 통합 범위 결정 (2026-07-05)

**배경.** Context98에서 사용자가 지시한 두 번째 항목("헤더 닉네임 클릭 시 마이페이지로 이동, 글/프로젝트/설정/팀 통합"). 세션 초입에 미결정 사항을 물었더니 사용자가 "2번(완전 대체)으로 하고 깨지는건 마이그레이션 해. 아니면 설정이나 팀 정도는 남겨도 될거같기도 한데 다른 사이트들 벤치마킹해봐"라고 답 — 처음 고른 선택지를 스스로 헤징하며 벤치마킹을 요청.

**조사.** WebSearch로 SaaS 프로필/설정/팀관리 UX 패턴 확인 — Notion·Slack·Stripe 등은 "프로필(개인 콘텐츠)"과 "설정/계정 관리(구성)"를 별도 영역으로 유지하는 경향이 일반적(설정은 계층적 탐색+검색이 필요한 별도 영역, 프로필은 개인 콘텐츠 허브). 팀 관리도 조직 단위 관리라 개인 프로필과는 별개 취급.

**결정.** 문자 그대로의 "완전 대체"가 아니라 **콘텐츠(내 프로젝트+내 글)만 마이페이지로 통합**하고 **설정/팀관리는 별도 라우트 유지**하는 절충안 채택 — 벤치마킹 결과가 사용자의 헤징을 뒷받침했기 때문. 구체적으로:
- `/dashboard`(내 프로젝트, 콘텐츠) → `/mypage` "프로젝트" 탭으로 완전 흡수, 기존 `DashboardPage.tsx` 삭제(이번 변경으로 생긴 orphan). `/dashboard` 라우트는 `<Navigate to="/mypage" replace />`로 남겨 기존 북마크/외부 링크 보호.
- 새 "글" 탭 — 이전엔 "내가 쓴 글"을 모아보는 화면 자체가 없었음(신규 콘텐츠 통합).
- `/settings`·`/teams`는 그대로 유지. 대신 `/mypage` 헤더에 "설정"·"팀 관리" 바로가기 버튼을 둬서 "합치는게 좋을거같다"는 의도는 접근성으로 충족.
- 헤더(`AppHeader.tsx`) 아바타/닉네임 클릭 대상을 `/settings` → `/mypage`로 변경 + 아바타 없을 때 보이던 닉네임 텍스트도 클릭 가능하게(Context98에서 발견된 기존 갭도 함께 수정).

**구현.** 백엔드 변경 없음 — `GET /api/users/{userId}/posts`(본인 조회 시 비공개 글 포함, 이미 있음)와 `GET /api/projects`(로그인 사용자 소유 프로젝트 전체, `DashboardPage`가 쓰던 것)를 그대로 재사용. `GET /api/users/{userId}/projects`는 확인해보니 `findPublicByUserId`로 **공개 프로젝트만** 반환하는 별개 엔드포인트(다른 사용자 프로필용)라 마이페이지엔 부적합 — 헷갈려서 잘못 재사용했으면 본인 비공개 프로젝트가 안 보이는 회귀가 생길 뻔했음. `MyPage.tsx`는 `DashboardPage.tsx`(프로젝트 탭)+`UserProfilePage.tsx`(글 탭 카드 렌더링)의 기존 로직을 그대로 옮겨 재구성 — 재사용 우선 원칙(§1) 그대로.

**검증.** `tsc -b` 통과. claude-in-chrome(로그인 세션)으로 `/mypage` 진입 → 프로젝트 탭(기존 4개 프로젝트 정상 표시) → 글 탭(게시글 3개, `hasGraph` 배지 포함 정상 표시) → `/dashboard` 직접 접속 시 `/mypage`로 리다이렉트 확인 → 헤더 닉네임 클릭 시 `/mypage` 이동 확인. 콘솔 에러 0.

---

## GraphPage 흐름 재생 → ShareGraphPage·CommunityPostGraphPage 이식 (2026-07-05, PR-흐름재생)

**배경.** Context98 세션에서 사용자가 claude-in-chrome으로 직접 앱을 확인한 뒤 "GraphPage에만 있는 흐름 재생(호출 트리를 단계별로 따라가는 기능)을 공유 그래프에도 넣어달라"고 지시. 이식 범위를 물었더니("ShareGraphPage만? 아니면 CommunityPostGraphPage도?") 사용자가 "쉐어그래프랑 커뮤니티포스트그래프랑 같은거아냐?"라고 반문 — 사용자 입장에서 둘은 구분할 이유가 없는 동일한 "그래프 보기" 경험이라는 뜻으로 받아들여 두 뷰어 모두에 이식하기로 확정.

**설계.** GraphPage.tsx에 있던 순수 함수/타입(`buildCallTree`·`CallTreeNode`·`PlaybackItem` 등, ~190줄)을 `utils/flowPlayback.ts`로, 상태·이펙트·핸들러(`startPlayback`/`resetPlayback`/분기선택 등, ~95줄)를 `hooks/useFlowPlayback.ts`로, 재생 패널 JSX(~180줄)를 `components/FlowPlaybackPanel.tsx`로 추출 — 기존 PR1~PR4(GraphPage/ShareGraphPage 공유 컴포넌트 추출) 패턴을 그대로 재사용. GraphPage.tsx는 이 세 모듈을 다시 import하도록 리팩터링(동작 무변경). 페이지별로 다른 부분(엣지 표시 토글·resetPlayback 시 부가 정리)은 `restoreEdgeStyles`/`onStart` 콜백으로 주입받게 설계해 훅 자체는 페이지 특수 사정과 분리.

**엣지 스타일 복원 방식이 페이지마다 다름 — 실수할 뻔한 지점.** GraphPage의 기존 `resetPlayback`은 엣지 스타일을 `e.data.type`/`broken`으로부터 다시 계산해서 덮어씀(strokeDasharray는 버려짐 — playback 도입 이전부터 있던 기존 동작, 이번에 손대지 않음). ShareGraphPage·CommunityPostGraphPage는 원래 playback이 없었으므로 이 방식을 그대로 베끼면 재생을 한 번이라도 실행한 뒤 엣지의 `strokeDasharray`(타입별 점선 구분)가 사라지는 **새 회귀**가 생길 뻔했음. 대신 두 페이지는 이미 갖고 있던 재료(ShareGraphPage: `buildLayout` 재호출, CommunityPostGraphPage: `builtEdgesCache`)로 엣지를 처음부터 다시 만들어 `restoreEdgeStyles`를 구현 — 기존 레이아웃/라벨 토글 핸들러가 쓰던 것과 동일한 패턴이라 완전히 복원됨.

**검증.** `tsc -b` 통과(3개 페이지 전부). 실 데이터로 브라우저 검증(OAuth 불필요한 두 뷰어): ①ShareGraphPage — gin-gonic/gin 공개 그래프(`/share/{projectId}`)에서 `searchCredential` 함수 노드 클릭 → 흐름 재생 패널 등장(`TestBasicAuthSucceed → ... → searchCredential`, 1/4단계) → `→` 버튼으로 4/4까지 진행 → `↺ 처음부터` 버튼 노출 확인 → 패널 `✕`로 재생 종료 확인. ②CommunityPostGraphPage(스냅샷 뷰어) — 실 게시글(`AdminDigestService` 그래프 첨부)에서 `computeDigest` 함수 클릭 → 재생 패널(1/5단계) + 기존 PR-C 노드 코멘트 섹션이 함께 정상 렌더 확인(회귀 없음). ③GraphPage — 리팩터링 후 `tsc` 통과 + 비로그인 상태로 라우트 진입 시 에러 없이 정상 에러 화면 렌더(모듈 로드 자체는 검증) 확인했으나, 로그인 필요한 실제 재생 인터랙션은 OAuth 계정 필요로 이 세션에서는 미검증 — 머지 전 사용자 확인 필요.

---

## ShareGraphPage 사이드바 접기 + 줌아웃 범위 확대 (2026-07-02, PR #427 머지 직후 사용자 발견)

**문제.** PR #427 머지 직후 사용자가 실사용 중 발견: ShareGraphPage에 사이드바 최소화 버튼이 없고, 마우스 휠로 화면을 축소해도 일정 배율 이하로 안 줄어듦.

**원인.** ①좌/우 `<aside>`가 GraphPage와 달리 조건부 렌더 없는 고정 flex sibling이라 접기/펼치기 자체가 없었음(기능 자체가 이식 안 됨) ②`<ReactFlow>`에 `minZoom`을 지정 안 해 React Flow 기본값(0.5)이 적용 — GraphPage는 `minZoom={0.05}`를 명시.

**수정(재사용 원칙대로 GraphPage 값 그대로 참조).** ①`leftOpen`/`rightOpen` state 추가, 접힘 시 얇은 스트립(펼치기 버튼만)으로 대체 — GraphPage는 절대 위치(absolute overlay) 구조라 그대로 포트하지 않고, ShareGraphPage의 기존 flex 레이아웃 구조는 유지한 채 같은 상호작용(헤더의 접기 버튼 + 접혔을 때 가장자리 펼치기 버튼)만 재현 ②`minZoom={0.05}`·`maxZoom={2}`를 GraphPage와 동일 값으로 추가.

**검증.** claude-in-chrome — 좌/우 각각 접기→얇은 스트립+펼치기 버튼 렌더 확인, 펼치기 클릭으로 복원 확인. 휠 이벤트 시뮬레이션으로 `.react-flow__viewport` transform이 `scale(0.05)`까지 도달 확인(기존 0.5 상한 대비 10배 이상 확장). `tsc -b` 통과, 콘솔 에러 0.

**문제.** 사용자 관찰: "도메인을 계층형으로 분석하는 건 쉬운데 계층형을 DDD로 분석하는 건 무리가 있다" — 계층형 뷰(`getGroupKey`)가 domain/application/infrastructure 같은 DDD 레이어명을 못 찾으면 `parts[0]`(경로 첫 세그먼트)를 그대로 그룹 키로 썼는데, 두 가지 방향으로 퇴화함을 실측 확인: ①gin(Go, 파일이 레포 루트에 바로 있음) → 루트 파일 하나하나가 각각 `parts[0]`(파일명 자체, 서브디렉터리 없어서)가 돼 **범례 46개**(파일당 박스 1개) ②ripgrep(Rust, `crates/{core,printer,...}` 10개 워크스페이스 크레이트) → 모든 파일의 `parts[0]`이 항상 `'crates'`라 **10개 크레이트가 전부 한 박스로 뭉개짐**.

**근본원인.** `GraphWarningService.isDddProject()`는 이미 "DDD 감지 → DDD 전용 검출기, 미감지 → 범용 레이어드 검출기"로 분기하는데(경고 쪽), **시각화(`getGroupKey`) 쪽엔 이 분기가 없어서** DDD 미감지 시에도 같은 알고리즘(첫 세그먼트=그룹)을 그대로 쓰다가 리포별 구조에 따라 양방향으로 망가진 것.

**설계 논의.** 처음엔 ①실제 폴더 구조를 재귀적으로 반영(N단계 중첩) ②언어별 관례 인식(Go 패키지·Rust crate·Python 패키지) 두 방향을 제안했으나, 실측해보니 문제의 정확한 원인은 훨씬 좁았음 — `parts[0]`이 "서브디렉터리가 있으면 그 디렉터리명(맞음)"과 "서브디렉터리가 없으면 파일명 자체(틀림)"를 구분 안 하고, `crates`/`src` 같은 의미 없는 래퍼 디렉터리를 그냥 그룹 키로 써버리는(틀림) 두 가지 좁은 결함이었음. 재귀 중첩이나 언어별 특수 처리 없이 **의미 없는 래퍼 디렉터리를 건너뛰고 그 다음 세그먼트를 그룹 키로 쓰는 것**만으로 두 사례 다 해결됨 — 큰 재설계보다 훨씬 작은 수정으로 충분(CLAUDE.md §1 재사용성/과잉설계 방지 규칙과 같은 맥락, 처음 제안한 두 옵션은 과잉 설계였음).

**수정.** `getGroupKey`(`graphLayout.ts`) — 레이어 키워드 매칭 실패 시, 파일명을 뺀 디렉터리 세그먼트에서 `src`/`lib`/`crates`/`packages`/`pkg` 같은 의미 없는 래퍼를 앞에서부터 건너뛰고 남은 첫 세그먼트를 그룹 키로 사용. 다 건너뛰어도(또는 애초에 서브디렉터리가 없어도) 남는 게 없으면 공통 `'root'` 키로 묶어 루트 파일들이 각자 박스가 되는 것을 방지. DDD 레이어명이 발견되는 프로젝트는 이 폴백 코드에 도달하지 않아 무영향.

**검증.** claude-in-chrome 라이브 3종 대조: gin 범례 46→7개(Root·Binding·Codec·GinS·Internal·Render·Testdata, 루트 .go 파일들이 하나의 ROOT 박스 안에 파일별 카드로 정상 표시), ripgrep 범례 → 10개 크레이트가 각각 분리된 섹션(Cli·Core·Globset·Grep·Ignore·Matcher·Pcre2·Printer·Regex·Searcher) + Root/Fuzz/Brew/Tests, codeprint 자신(DDD 프로젝트) 범례 10개 그대로 무회귀(Application·Backend·Domain·Infrastructure·Interfaces·Frontend·Components·Hooks/Utils·Pages·Database). `tsc -b` 통과, 콘솔 에러 0.

---

## ShareGraphPage 뷰어 기능 확장 1단계 — 레이아웃 전환 + 범례 다중 토글 이식 (2026-07-02)

**문제.** PROGRESS.md 백로그(2026-07-02 Plan)에 따라 GraphPage에는 있고 ShareGraphPage엔 없는 "보기" 기능 중 프론트 전용 2가지(①레이아웃 프리셋 전환 버튼 ②도메인/레이어 다중 토글 범례)를 이식.

**구현.** GraphPage의 `toggleLayoutPreset`/`toggleLayerOpaque`/`toggleDomainOpaque` 로직을 거의 그대로 포트. ShareGraphPage는 원래 `layoutPreset`·`labelMode`·엣지 가시성·`opaqueLayerSet`을 로드 시점 로컬 상수로만 썼던 것(재계산 불가) → state로 승격 + `rawNodesCache`/`rawEdgesCache`를 추가해 `buildLayout` 재호출이 가능하도록 함.

**★ 구현 중 발견·수정한 버그(포팅 과정에서 캐치, 배포 전)**: `toggleDomainOpaque(domain)`은 `n.data.domain`(소문자 키, 예: `payment`)과 정확히 일치해야 섹션·자식 노드를 숨긴다. 그런데 ShareGraphPage의 기존 `availableTabs`(상단 탭바용)는 `sectionNode.data.label`(대문자, 예: `Payment`)에서 만들어진 목록이라, 이걸 그대로 범례 도메인 목록에 재사용하면 대소문자 불일치로 토글이 아무 것도 안 하는 조용한 버그가 될 뻔했음. → `domainSectionKeys`(sectionNode id에서 `domain-section-` 접두어를 제거한 소문자 키 목록)를 별도로 파생해 범례 전용으로 사용, 표시 라벨만 첫 글자 대문자화. 레이어 모드 범례도 동일 문제(고정 8종 배열의 `key`(소문자)를 `availableTabs`(라벨)와 직접 비교하면 항상 빈 배열) — `label` 기준으로 필터링하도록 수정, `LAYER_META_PRE`의 정확한 표기(`Hooks / Utils`, `Infrastructure`)까지 맞춤.

**검증.** `tsc -b` 통과. claude-in-chrome으로 codeprint 자체 공개 그래프(`172463ea-eb9c-493e-9c93-016f06870c25`, 실제 DDD 레이어·도메인 보유)에서 라이브 검증: 레이어 모드 범례 8종 전부 렌더 + Domain 레이어 토글 시 해당 섹션 박스만 회색으로 dim되고 내부 노드 숨김 확인(스크린샷), 레이아웃 전환 버튼으로 계층형→도메인 재빌드 성공(20개 도메인 섹션 정상 렌더), 도메인 범례에서 Payment 토글 → 탭 필터로 확인 시 해당 도메인 콘텐츠 숨김 재확인. 콘솔 에러 0.
**부수 확인**: mini-redis(비-DDD 소형 Rust 레포)로 먼저 테스트했을 때 두 모드 다 범례가 빈 목록으로 보였으나, 이는 버그가 아니라 GraphPage와 동일한 기존 한계(레이어 모드는 고정 8종 DDD 레이어명만 인식, 도메인 모드는 도메인이 1개(`Common`)뿐이면 `availableTabs.length > 2` 게이트에 걸려 범례 자체를 숨김) — 실제 DDD 구조가 있는 프로젝트(codeprint 자신)로 재검증해 정상 확인.
**도구 함정**: `preview_screenshot`(Preview MCP)이 이 페이지에서 반복적으로 30초 타임아웃 — 처음엔 앱이 멈춘 것으로 의심했으나 `preview_eval`로 `document.body.innerText` 직접 확인 결과 페이지는 정상 렌더 중이었음. React Flow 캔버스가 있는 무거운 페이지에서 스크린샷 캡처 자체가 실패하는 도구 한계로 추정(claude-in-chrome 스크린샷은 정상 동작) — 다음에 같은 타임아웃을 보면 앱을 의심하기 전에 `preview_eval`로 먼저 실제 렌더 상태를 확인할 것.

**스코프 제외(계획대로)**: ③버전 기록 열람(신규 백엔드 엔드포인트 필요, 별도 PR) ④스케치 모드(선택, 후순위). ShareGraphPage 516→661줄로 커졌으나 GraphPage 대비 여전히 훨씬 작아 커스텀 훅 추출 리팩토링은 이번 PR에서 보류(추측성 선제 리팩토링 지양, §2).

**★ PR 리뷰 중 사용자 발견·즉시 수정 2건(같은 PR에 반영)**:
1. **범례 아이콘이 전부 동일한 회색이었음** — 포팅 시 GraphPage가 도메인/레이어마다 실제 섹션 색상(`domainColorMap`/레이어별 고정 컬러)을 범례 아이콘에 쓰던 것을 회색 하나로 단순화해버린 회귀. 레이어 범례는 GraphPage의 고정 8색 배열을 그대로 이식(`Domain #3b82f6` 등), 도메인 범례는 실제 `domain-section-*` 노드의 `data.color`를 재사용(`domainSections` memo)하도록 수정 — 재구현 대신 이미 렌더링에 쓰이는 색상 소스를 그대로 참조해 캔버스와 항상 일치 보장.
2. **경고 섹션이 좌측 사이드바를 항상 꽉 채워 범례가 화면 밖으로 밀림** — ShareGraphPage는 PR #327(GraphPage 사이드바 경량화, 경고를 우측 하단 코너 플로팅 기본 접힘 패널로 전환)의 대상이 아니었던 것으로 확인(GraphPage만 반영됨, ShareGraphPage는 그 이후에도 계속 인라인 풀사이즈였음 — 이번 이식 작업으로 새로 드러난 동일 계열의 반영 누락). GraphPage와 동일한 우측 하단 코너 플로팅(기본 접힘 칩, 클릭 시 확장) 패턴으로 교체, 단 AI 분석·suppress/restore/ignore 등 쓰기 액션은 이식 대상이 아니므로 제외하고 `WarningPanel warnings={warnings}` 읽기 전용 렌더만 유지.
- 두 건 다 claude-in-chrome으로 재검증(레이어·도메인 모드 색상 분화 확인, 경고 칩 접힘→확장 정상 동작), `tsc -b` 통과, 콘솔 에러 0.

**★★ 3번째 발견 — 사용자가 "오늘의 공개레포" 5개(gin·sinatra·Newtonsoft.Json·mini-redis·ripgrep)를 직접 순회 검증하다 잡음(더 심각)**: 레이어 모드 범례가 codeprint 자신 말고는 전부 비어 있었음. 원인은 레이어 범례가 `Domain/Application/Infrastructure/...` **고정 8개 이름 배열**이었기 때문 — DDD/Java·Spring 컨벤션 디렉터리명이라, 이 컨벤션을 안 쓰는 실제 오픈소스 레포 대부분(=지금 자동으로 노출 중인 "오늘의 공개레포" 쇼케이스 5개 거의 전부)에서 하나도 매치 안 됨. codeprint 자기 자신 하나로만 검증해 발견 못 한 표본 편향. **이 고정 배열은 GraphPage(로그인 소유자 화면)에도 원래부터 있던 동일 버그**(`GraphPage.tsx` 레이어 범례, 이번에 확인) — ShareGraphPage뿐 아니라 GraphPage도 같이 수정.
- **수정**: 두 페이지 다 고정 배열 대신 실제 렌더된 `layer-section-*` 노드에서 key/label/color를 동적으로 파생(`layerSections` memo, 도메인 범례의 `domainSections`와 동일 패턴) — `buildLayout`의 `getFallbackLayerMeta`가 이미 비DDD 프로젝트에도 파일/폴더 단위 폴백 섹션+색상을 만들어주고 있어(`graphLayout.ts`), 그걸 그대로 읽기만 하면 됨. GraphPage 쪽은 `clickable = availableTabs.includes(key)` 가드도 함께 제거(하드코딩 파일경로 기반 7종 목록 대조용이었는데, 실제 필터링 로직(`tabFilteredNodeIds`)은 임의의 폴백 키도 이미 지원해서 불필요하게 좁은 제약이었음).
- **검증**: gin(파일 단위 폴백 섹션 46개)으로 재확인 — 범례가 실제 파일명 목록으로 가득 채워지고, 토글 클릭 시 정상 dim. `tsc -b` 통과, 콘솔 에러 0.
- **교훈**: 자기 자신(codeprint) 하나로 검증하는 건 "실제 서비스에서 보게 될 프로젝트 분포"를 대표하지 못한다 — 이번처럼 공개 쇼케이스에 노출되는 실제 다양한 언어/컨벤션 레포로 교차검증해야 이런 표본 편향형 버그를 잡을 수 있음.

---

## 랜딩페이지 정리 — 광고 사이드바 제거·요금제 문구 실측 수정·섹션 재배치 (2026-07-02)

**문제.** 사용자 지시로 랜딩페이지(`LandingPage.tsx`) 정리: ①광고 사이드바(좌/우/하단 3곳, 실제 광고 미연동 placeholder) 제거 ②요금제 카드 문구가 실제 코드와 맞는지 확인 ③대문의 그래프 목업(정적 SVG 예시) 삭제 ④"사용법" 섹션을 위로, 그 아래 "오늘의 공개레포" 배치 ⑤"주요 기능" 텍스트 다듬기.

**요금제 문구 실측 감사(★핵심 발견).** 코드 전수 검색(`isPaidPlan`/`isPaid()` 호출부) 결과, 요금제 카드가 Desktop 전용으로 광고하던 "AI 설명/코드 생성", "그래프 버전 히스토리", "경고 MD 내보내기" **3개 항목이 실제로는 백엔드·프론트 어디에도 플랜 게이트가 없어 Free 사용자도 전부 사용 가능**했다. AI는 애초에 BYOK(사용자 본인 API 키) 설계라 코스트가 Codeprint에 없어 처음부터 무료가 맞는 설계(`decisions/DECISIONS_BACKEND.md` "AI 설명 기능 — 다중 제공자" 항목과 일치). 그래프 버전 히스토리(`GraphFacade.getGraphVersionsWithBranch`)·경고 MD 내보내기(`downloadWarningsMd`)도 게이트 코드 자체가 존재하지 않음(구현 시점에 유료화 배선이 빠진 채 남은 것으로 추정, 원인 불명). `isPaidPlan()`을 실제로 참조하는 곳은 전체 백엔드에 **딱 1곳**(`CollaborationApplicationService.joinSession` — Free는 협업 세션 참가자 오너 포함 6명 제한) + 정식 `Team`(유료 좌석) 생성/좌석관리 결제 플로우뿐.
- **사용자 확인 후 결정:** 카드 문구를 코드 현실에 맞게 수정(구현 착수가 아닌 카피 수정 선택). Free 카드에 AI/버전히스토리/MD내보내기·협업 6명 제한을 명시적으로 옮기고, Desktop 카드는 "협업 인원 제한 해제"+"정식 팀 생성·좌석 관리"+"Free 모든 기능 포함"으로 축소. "경고 감지 8종"은 실제로는 HIGH 등급만 8종(전체 경고 타입은 15종, `GraphWarningService`의 `type` 리터럴 카운트로 확인) — "경고 감지 15종 (HIGH 8종 포함)"으로 명확화.
- **후속 논의 필요(기록만, 미착수):** Desktop 라이센스(₩9,900/월)의 실질 차별점이 "협업 인원 제한 해제"+"정식 팀"뿐이라 유료 전환 유인이 약할 수 있음. 실제 게이팅을 추가해 광고했던 기능을 진짜 유료화할지, 아니면 다른 차별점을 설계할지는 별도 제품 결정 필요(정기결제 계약 대기 중이라 이번 세션 범위 밖).

**레이아웃 결정.** 광고 사이드바 제거로 확보된 폭을 활용해 "사용법"+"주요 기능"을 `max-w-4xl` 컨테이너 안에서 `grid-cols-1 lg:grid-cols-2`로 나란히 배치(데스크톱), 모바일은 세로 스택(반응형 그리드 자동 처리). 정적 그래프 목업(SVG, `GraphMockup` 컴포넌트)은 삭제 — 실제 라이브 데이터를 보여주는 "오늘의 공개레포"가 그 역할을 대체하도록 사용법 섹션 바로 아래로 재배치. "오늘의 공개레포" 그리드도 `max-w-2xl sm:grid-cols-2` → `max-w-4xl sm:grid-cols-2 lg:grid-cols-3`로 확장(5개 카드가 3+2로 넓게 배치).

**검증.** `npx tsc -b` 통과. Preview 도구로 데스크톱(1400px) 렌더 확인 — 사용법/주요기능 나란히 배치, 요금제 카드 문구 정상 렌더, 콘솔 에러 없음. "오늘의 공개레포"는 Preview 패널이 백엔드(8080)에 도달 못 하는 기존 제약(Context93 기록)으로 이번엔 미검증 — 프론트 단독 레이아웃(그리드 클래스)만 코드 확인.

---

## ShareGraphPage 대형 레포 빈 화면 — fitView 미호출 + flexbox min-width 버그 2건 (2026-07-02, fix, 부수 발견)

**문제.** "오늘의 공개레포" 라이브 검증 중 gin-gonic/gin(파일 다수) 공유 그래프(`/share/{projectId}`)가 진입 시 완전히 빈 화면으로 보임. 좌측 "노드 검색" 목록도 비어 보였음.

**조사 과정.**
1. 좌측 노드 목록: `indexNodes` 계산 로직(검색어 없으면 전체 노드 표시)은 코드상 정상. 브라우저 JS로 사이드바 DOM의 `textContent`를 직접 추출해보니 실제로는 `auth.go`, `searchCredential` 등 수백 개 항목이 **정상적으로 채워져 있었음** — `text-gray-600` 10~11px 텍스트가 `bg-gray-950` 배경과 대비가 낮아 스크린샷/육안으로 안 보였을 뿐. GraphPage.tsx의 동일 목록도 같은 색상 조합(`text-gray-600`)을 쓰고 있어 앱 전체의 기존 디자인 컨벤션 — **버그 아님, 수정 안 함**.
2. 그래프 캔버스: `.react-flow__viewport`의 `transform`이 로드 후에도 `translate(0,0) scale(1)`(미조정 기본값)에 머물러 있었음. `<ReactFlow fitView>`의 `fitView` prop은 **최초 마운트 시 1회만** 자동 실행되는데, `nodes`/`edges`가 `useNodesState([])`로 빈 배열 시작 후 `axios` 응답이 온 뒤에야 `setNodes`로 채워지는 구조라, 최초 마운트 시점엔 노드가 없어 자동 fitView가 사실상 no-op이었음. `GraphPage.tsx`는 데이터 로드 `.then()` 직후 `setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)`을 명시적으로 호출하는데(1127번째 줄), `ShareGraphPage.tsx`의 동일 위치엔 이 호출이 아예 없었음.
3. 위 fitView 호출을 추가한 뒤에도 여전히 빈 화면 — `.react-flow__pane`의 실제 렌더 폭이 **3270px**로, 뷰포트(1178px)보다 훨씬 넓게 부풀어 있었음. 원인은 flexbox 기본값 `min-width: auto` — 그래프 캔버스를 감싸는 `<div className="flex-1 h-full flex flex-col">`(그래프 캔버스 wrapper)가 부모(`flex-1 flex overflow-hidden`, 1178px로 정상 측정)의 폭 제약을 무시하고 **내부 ReactFlow 콘텐츠 크기만큼 늘어나** 있었음. `fitView`는 이 부풀어진(3270px) 컨테이너 기준으로 "맞춤" 계산을 해서, 실제 화면에 클리핑되는 좌측 1178px 슬라이스엔 콘텐츠가 거의 안 걸리는 위치로 이동해버림. `GraphPage.tsx`의 동일 wrapper(4039번째 줄)는 **이미 `min-w-0`을 갖고 있어** 이 버그가 없었음 — `ShareGraphPage.tsx`만 이 클래스가 누락된 상태.

**결정.** ①`ShareGraphPage.tsx` 데이터 로드 effect에 `setTimeout(() => fitView({ padding: 0.1, duration: 300 }), 300)` 추가(`GraphPage.tsx`와 동일 패턴, `fitView`를 effect 의존성 배열에도 추가) ②그래프 캔버스 wrapper에 `min-w-0` 클래스 추가. 둘 다 기존 `GraphPage.tsx`에 이미 있는 패턴을 그대로 가져온 것 — 신규 메커니즘 도입 없음(§3 외과적 수정).

**결과.** claude-in-chrome 라이브 검증: 수정 전 완전 빈 화면(`.react-flow__pane` 너비 3270px, transform 미조정) → fitView 추가만으로는 여전히 빈 화면(transform은 계산됐으나 부풀어진 컨테이너 기준이라 여전히 어긋남) → `min-w-0` 추가 후 `.react-flow__pane` 너비가 정상 측정되고 파일별 그래프가 화면에 꽉 차게 렌더링됨(auth.go·auth_test.go·benchmarks_test.go 등 실제 노드 확인). `tsc -b` 통과. `GraphPage.tsx`는 애초에 두 패턴 모두 갖고 있어 무영향(같은 버그 없음 확인 완료).

**문제.** 분석이 서버에서 멈춰도(재시작 아닌 행) 프론트는 RUNNING 폴링만 반복하고 "분석 중" 버튼이 비활성이라 사용자가 빠져나올 길이 없었다. (#397이 폴링 단일실패는 복원했으나, 정상 응답이 계속 RUNNING인 경우는 별개.)

**구현 선택 — 폴링 중단 vs 안내만.**
- 한계 시간 후 폴링을 끊고 FAILED 처리하는 안은 탈락: 큰 레포는 정상적으로 오래(수 분) 걸릴 수 있어 진짜 진행 중인 분석을 죽일 위험.
- **채택**: `useAnalysisProgress`가 약 180초(STALL_TICKS 90 × 2s) 연속 RUNNING/PENDING이면 `stalled=true`만 반환하고 **폴링은 계속**. ProjectCard는 `isAnalyzing && stalled`일 때 "분석이 예상보다 오래 걸립니다" 배너 + 재시도 버튼(RUNNING 중에도 새 분석 시작 가능)을 노출. 진행 중 분석을 죽이지 않으면서 탈출구 제공.

**검증.** tsc 통과. ★조건부 UI 양쪽 라이브 확인(CLAUDE.md 규칙4): false=정상 분석 시 배너 없음, true=STALL_TICKS를 일시 2로 낮춰 codeprint 재분석 → RUNNING 13%에서 배너+재시도 렌더 확인(DOM hasBanner/hasRetry=true) → STALL_TICKS 90 복원. ★HMR 함정: 실행 중 폴링 인터벌은 effect deps([analysisId])라 상수 변경을 안 집어 새 분석을 시작해야 새 값 적용 — 하드 리로드+새 분석으로 검증.

**결과.** #396(clone 타임아웃·재기동 stuck 청소)·#397(폴링 단일실패 복원)과 함께 분석 안정성 묶음으로 ChangelogPage v0.100.3.

---

## 프로젝트 생성 시 자동 분석 — 기본 브랜치 해소를 git에 위임 (2026-06-29)

**문제.** 대시보드 빈 상태 카피가 "자동 분석 — 평균 10~30초"를 약속하나, `CreateProjectModal.onCreated`가 목록 추가만 하고 분석을 트리거하지 않았다. time-to-value가 "분석 시작 → 브랜치 피커 → 선택" 4스텝 뒤에 갇혀 활성화 깔때기 이탈 지점(Phase 0)이었다.

**구현 선택 — 트리거 위치.**
- 백엔드(ProjectCommandService가 생성 후 분석 킥오프) 탈락: project 컨텍스트가 analysis 컨텍스트를 직접 호출(Cross-Context 위반)하고, 프론트가 analysisId를 못 받아 폴링 불가.
- **채택**: 프론트에서 트리거. ProjectCard가 이미 진행률 폴링·완료 시 그래프 이동을 소유하므로, DashboardPage가 방금 생성된 프로젝트 id(`autoAnalyzeId`)를 `autoStart` prop으로 전달 → 카드가 마운트 시 ref 1회 가드로 기존 `handleStartAnalysis('')` 호출. 새 API·새 로직 없음.

**기본 브랜치 결정 — main/master 하드코딩 폴백 vs git 위임.**
- Context87 메모는 "primary 있으면 그것, 없으면 main/master 폴백"이었으나, 갓 생성된 프로젝트는 primaryBranch가 항상 null이고 레포 기본 브랜치가 main/master가 아닐 수 있다(develop 등).
- **채택**: `branch: null`로 POST → `RepoCloner.clone(url, null)`이 `--branch` 없이 clone해 git이 **실제 레포 기본 브랜치**를 가져온다. 하드코딩 폴백보다 정확하고 코드도 단순.

**결과.** 생성 즉시 진행률 표시 → 완료 시 회로도 이동. 빈 상태 약속 이행. v0.100.2. ★전체 플로우 런타임 검증은 OAuth 로그인+서버 가동 필요(/loop라 PR 후 사용자 검증).

---

## 서버 오류 traceId 화면 표시 — 토스트 라이브러리 없이 커스텀 이벤트 (2026-06-22)

**문제.** 백엔드가 5xx 응답에 traceId를 넣었으나(#345), 프론트가 화면에 노출하지 않아 사용자가 신고 시 ID를 전달할 수 없었다.

**구현 선택 — 토스트 라이브러리 도입 vs. 커스텀 이벤트 + 경량 컴포넌트.**
- 프로젝트에 토스트 라이브러리가 없음(react-hot-toast 등). 한 기능을 위해 의존성을 추가하는 건 과함(Simplicity First).
- **채택**: `window` 커스텀 이벤트(`app-error`) + 경량 `ErrorToast` 컴포넌트(App에 1회 마운트, 6초 자동 제거). axios interceptor가 5xx+traceId 감지 시 이벤트를 디스패치 → 컴포넌트가 수신·표시. 결합도 0(interceptor는 React 밖, 컴포넌트는 이벤트만 구독).

**범위.** 401 refresh 로직(기존)은 그대로, 5xx 분기만 추가. 4xx는 미표시(입력 오류라 추적 무의미). 라이브 검증: 페이지에서 `app-error` 이벤트 디스패치 → 토스트 정상 렌더 확인(traceId·닫기 버튼). interceptor와 컴포넌트가 동일 이벤트로 연결돼 전체 흐름 보장.

**결과.** 5xx 시 우측 하단에 "추적 ID: xxxxxxxx — 문의 시 알려주세요" 표시. v0.93.5.

---

## common 도메인 정리 — 죽은 POC 삭제 + 동사형 명사화 규칙 (2026-06-21)

**배경.** 자기 그래프 도메인 분포 측정 결과 `common`(미분류 버킷)이 284노드(13.9%)로 3위. 무엇을 끄집어낼 수 있는지 분석.

**조치 1 — 죽은 POC 삭제.** `tools/treesitter/TreeSitter*Poc.java` 9개(42함수)가 common 최대 덩어리. main() standalone·프로덕션 참조 0의 AST 개발용 일회용 probe → 삭제. (저장된 그래프에는 재분석 전까지 51노드로 잔존 — 소스 삭제는 재분석 후 반영.)

**조치 2 — donate→donation (채택).** `resolveDomain`에 영어 명사화(-ion/-ment) 추가: `donate→donation`·`pay→payment`·`create→creation`. 길이≥3 가드 + 알려진 도메인 매칭 시에만. 라이브 검증: donate 3파일이 donation으로, common 284→275. **범용 규칙**이라 모든 분석 프로젝트에 적용(특정 프로젝트 하드코딩 아님).

**탈락시킨 것 (이유 포함).**
- **node→graph·auth→security 별칭 하드코딩.** node/graph, auth/security는 일반 영어로 무관 → Codeprint 전용 별칭을 넣으면 `extractDomain`이 의도한 **범용성을 깨고 남의 프로젝트 분석을 오염**(예: auth·security 도메인을 둘 다 가진 프로젝트 오매칭). 기각.
- **conformance 바운디드 컨텍스트 추출(graph에서 GraphWarningService 등 분리).** `GraphWarningService.detect(List<Node>, List<Edge>, intent)`가 `domain.graph.Node/Edge/EdgeType/NodeType`를 직접 순회 → 별도 컨텍스트로 빼면 §10(도메인 간 도메인 import 금지) 정면 위반. 회피하려면 Node/Edge DTO 중복 또는 shared kernel → 복잡성↑·사용자 가치 0·회귀 위험(소비자 6곳). 경고 감지는 본질적으로 graph 애그리거트 분석이라 graph의 정당한 능력으로 판단. 기각.
- **mcp/auth/conformance 신규 도메인 신설.** 순수 의미 평가: mcp=인터페이스/프로토콜 어댑터(graph 데이터 노출), auth=기존 security의 일부, conformance=기존 graph의 일부 → 진짜 신규 도메인 아님. 신설 무의미. 기각.

**측정 방법.** 라이브 그래프(`/graph` no-store)에 `extractDomain` 충실 포팅 적용해 도메인별 노드 수 집계. graph(373)는 outlier 아님 — analysis(409)가 더 큼. 핵심 도메인(analysis+graph)이 최상위인 건 코드분석·시각화 제품의 건강한 형태로 판단.

---

## 분석 결과 카드 — 경고 패널 스크롤이 rAF/scrollIntoView로 안 되던 문제 (2026-06-17)

**문제.** 분석 완료 결과 카드의 "경고 보기" CTA가 좌측 경고 패널(`#warning-section`, 중첩 `overflow-y-auto` aside 내부)로 스크롤·강조하도록 구현했으나, 라이브 검증에서 스크롤이 전혀 동작하지 않음(scrollTop 0 유지, 강조 ring도 미적용).

**원인(브라우저 격리 테스트로 확정, 추측 아님).**
1. `el.scrollIntoView({behavior:'smooth'})` — 중첩 overflow 컨테이너에서 무시됨(scrollTop 0). `behavior:'auto'`는 동기 호출 시 동작.
2. `requestAnimationFrame` 안에서 `aside.scrollTop = el.offsetTop` 설정도 무시됨 — React Flow가 자체 rAF 렌더 루프를 돌려 같은 프레임에 scrollTop을 리셋하는 것으로 보임. **동기 호출·`setTimeout`은 정상 동작**(scrollTop 780, 보임).

**결정.** `setTimeout(120)` + `aside.scrollTop = el.offsetTop`(offsetParent가 aside라 정합) + ring 강조 1.6s. 격리 테스트: rAF=실패 / setTimeout=성공으로 직접 확인.

**교훈.** React Flow 페이지에서 프로그램 스크롤은 rAF를 피하고 setTimeout으로 프레임 이후 실행한다. 중첩 overflow에선 scrollIntoView보다 offsetTop 직접 설정이 안정적.

---

## 발전사 페이지 `/evolution` — track 필드 전면 태깅 대신 큐레이션 3 arc 채택 (2026-06-14)

**문제.** 패치노트(버전 축)와 별개로 "한 기능이 어떻게 자랐나"(기능 축) 서사를 보여주는 페이지가 필요. 원 스펙은 `Release` 인터페이스에 `track` 필드를 추가해 기존 ~75개 릴리스 전부를 7개 track으로 소급 태깅하는 방식이었다.

**선택지.**
- 탈락 1: **모든 릴리스에 track 태깅(7 track).** 사용자 피드백 — "전부 다 달면 이건 그냥 패치노트 고도화밖에 안 된다." 전체 나열은 버전 타임라인의 재배열에 불과해 "기능이 자란 서사"라는 차별점이 사라짐. 탈락.
- 탈락 2: item 단위 track. RELEASES의 `category`가 프론트/백엔드 레이어와 경고/그래프/분석 도메인이 뒤섞여 있어 자동 분류 부정확 + 한 릴리스가 여러 track에 쪼개져 서사가 흐려짐. 탈락.
- 채택: **플래그십 3개 기능만 큐레이션.** 실제로 여러 버전에 걸쳐 자란 기능(경고엔진·분석엔진·그래프시각화)만 선별, 각 arc의 핵심 마일스톤 버전만 표시. 나머지 릴리스(로그인·커뮤니티 CRUD·인프라 등 일회성·기반 작업)는 태깅 없이 `/changelog`에만 남김.

**구현 — 데이터 단일 소스 유지.** `ChangelogPage.tsx`의 `RELEASES`·`Release`를 export하고, `EvolutionPage`에서 import. arc 정의(`ARCS`: 서사 lede + 마일스톤 버전 문자열 목록)는 편집성 큐레이션이므로 EvolutionPage에 둠 — `Release` 객체마다 track 필드를 심지 않아 ChangelogPage는 export 키워드만 추가(최소 변경). 마일스톤은 `compareVersion`(semver 파싱)으로 오래된→최신 정렬해 성장 방향으로 표시.

**결과.** `/evolution` 3 arc(경고 10·분석 7·그래프 6 마일스톤) 렌더 확인(Chrome). 새 페이지=MINOR(v0.75.0), AppHeader "발전사" 링크(패치노트↔작동 방식 사이). 한 릴리스가 한 track의 마일스톤이 되는 Release 단위 큐레이션이라 서사가 선명.

---

## 경고 suppress(숨기기) UI — 세션 복원 방식 채택 (2026-06-14, C-12)

**문제.** 백엔드(#259)는 `POST /api/projects/{id}/warnings/suppress`(fingerprint)·`DELETE .../suppress/{fingerprint}`를 제공한다. 프론트에서 경고를 숨기고 되돌릴 수 있어야 하는데, 숨긴 경고는 백엔드가 그래프 응답 단계에서 이미 필터링해 프론트로 오지 않으므로 "현재 숨겨진 목록"을 표시할 방법이 없다.

**선택지.**
- 탈락 1: 숨긴 경고 목록 조회용 GET 엔드포인트 신설 → 백엔드 변경 필요. 이번 작업 시점에 백엔드가 가동 중이라 재컴파일이 DevTools 재시작·다운을 유발(이번 세션 1회 발생), 프론트 전용으로 닫고 싶어 탈락.
- 채택: **세션 내 복원.** 숨기기 시 해당 경고를 `warnings`에서 제거하고 `suppressedWarnings`(컴포넌트 상태)로 이동 → 패널 하단 "숨긴 경고(N)" 접이식 목록에서 복원(DELETE) 가능. 이전 세션에 숨긴 경고는 계속 숨겨짐(백로그: 숨김 관리 페이지).

**결과.** `WarningPanel`에 `onSuppress`/`suppressed`/`onRestore` props 추가(소유자 뷰=GraphPage에만 전달, ShareGraphPage 제외). 낙관적 갱신(서버 저장 후 로컬 상태 이동)으로 전체 재분석 없이 패널만 갱신 → 레이아웃 리셋 회피. 전체 경고를 숨겨도 복원 목록이 보이도록 `LeftSection` 표시 조건에 `suppressedWarnings.length > 0` 추가. 소유권은 백엔드가 강제(비소유자 POST는 403).

---

## 도메인 뷰 분류 — 컨트롤러가 가짜 "api" 도메인에 몰리던 문제 (2026-06-13)

**문제.** 도메인 뷰에서 `interfaces/api/` 폴더의 컨트롤러 29개가 전부 "api"라는 단일 도메인 박스에 묶이고, 그 외 다수 파일은 "common"에 쌓여 도메인 구분이 무의미했다.

**원인.** `extractDomain`이 "레이어 키워드 다음 첫 서브폴더 = 도메인" 규칙만 사용. `interfaces/api/GraphController.java`는 레이어가 `interfaces`, 첫 서브폴더가 `api`이므로 도메인이 `api`로 판정됨. 그러나 `api`는 도메인이 아니라 전달 방식(기술 분류)일 뿐이다. 원작자는 파일명 기반 추출이 파편화를 유발한다는 이유로 의도적으로 배제했었다.

**해결 방법 선택.**
- 탈락 1: `api`를 NON_DOMAIN_FOLDERS에 넣기만 함 → 컨트롤러가 전부 `common`으로 빠져 오히려 악화.
- 탈락 2: 파일명에서 무조건 도메인 추출 → `NodeStyleController`→nodestyle, `UserFollowController`→userfollow 식으로 파편화(원작자가 우려한 문제 재현).
- 채택: **2-pass 화이트리스트 매칭.** ①경로로 확실히 식별되는 도메인 집합(`buildKnownDomains`)을 먼저 수집 → ②구조 폴더만 있는 파일은 파일명에서 기술 접미사(Controller/Service 등)를 떼고 PascalCase 토큰을 누적 매칭하되, **알려진 도메인에 일치할 때만** 채택. 일치 없으면 `common`. 파편화 없이 정확도만 확보.

**결과.** 실제 백엔드 219개 파일 검증: "api" 도메인 완전 소멸, 컨트롤러 29개 중 25개가 올바른 바운디드 컨텍스트(graph/community/user/analysis 등)로 귀속. 나머지 4종(Admin/Auth/Dev/Feedback/Mcp/GlobalExceptionHandler 등)은 대응 컨텍스트가 없어 `common` 유지 — DDD상 올바른 분류. `extractDomain`은 `knownDomains` 인자가 없으면 기존 동작(파일명 추론 비활성)이라 하위 호환. GraphPage의 탭 필터·범례·색상 6개 호출부도 동일 `knownDomains`를 공유시켜 레이아웃과 결과 일치 보장.

**후속 — 프론트 페이지/컴포넌트까지 귀속 확장.** 위 수정 직후 사용자 요청: "common의 page 같은 것도 도메인에 붙일 수 없나?" 프론트엔드는 `pages/`·`components/`가 평평한 구조라 경로상 도메인 신호가 없어 대부분 `common`에 남았다.
- 핵심 안전성: **파일명 토큰을 "이미 알려진 도메인 집합"에만 매칭** → 새 도메인을 만들지 않으므로 원작자가 우려한 파편화가 원천적으로 불가능. 이 불변식 덕에 매칭을 공격적으로 해도 안전.
- 3가지 보강: ①UI 래퍼 접미사(Page/View/Modal/Panel/Section/Card/Layout 등) 제거 추가, ②단·복수형 흡수(`resolveDomain` — teams→team, messages→message), ③선두 누적 매칭 실패 시 개별 토큰 스캔(ShareGraphPage→graph, CreateProjectModal→project).
- 결과(백엔드+프론트 267개 파일 검증): `common` 42개로 수렴, 남은 건 전부 도메인 무관(인프라 config, 앱 진입점, AppHeader/Footer/WarningPanel 같은 전역 chrome, Login/Settings/Privacy 등 도메인 없는 페이지). 오귀속 0건.

**후속 2 — 도메인 박스가 가로로 무한정 길어지던 레이아웃.** 사용자 스크린샷: `common`처럼 그룹이 많은 도메인이 한 줄짜리 초장방형 스트립으로 표시됨.
- 원인: `buildDomainPositions`의 도메인 내부 레이아웃이 그룹을 단일 가로 행에 무한 배치(`x += l.w + GROUP_GAP`, y 고정). PR #144 "1열 우측정렬" 설계의 부작용 — 그룹이 적을 땐 괜찮지만 common(그룹 ~18개)에서 폭이 2600px 이상으로 폭주.
- 해결: 그룹을 그리드로 줄바꿈. 목표 행 수를 `round(√(n/2.5))`로 잡아 가로로 약간 넓은 직사각형을 만들고, 누적 폭이 `maxRowW`를 넘으면 다음 행으로. (계수 2.5는 그래프가 세로보다 가로로 읽기 편한 점 반영.)
- 검증: 그룹 수별 시뮬레이션 — 3개→1행(448×148), 18개→3행(880×356), 42개→5행(1456×564). 모두 화면 친화적 비율로 수렴.

---

## 버그

### GraphPage fetchGraph 무한 재요청 루프 (2026-06-11)

**문제.** 그래프 페이지 로드 시 `/api/projects/{id}/graph`가 14회 이상, `/freshness`가 9회 이상 반복 호출됨. 앱이 비정상적으로 느린 원인이었다.

**원인.** React useCallback 의존성 순환.
1. `openFileSidebar` = `useCallback(..., [rawNodes])` — rawNodes에 의존
2. `fetchGraph` = `useCallback(..., [..., openFileSidebar])` — openFileSidebar에 의존
3. `useEffect(() => fetchGraph(), [fetchGraph])` — fetchGraph 변경 시 재실행
4. `fetchGraph` 실행 → `setRawNodes(rn)` 호출 → rawNodes 변경
5. rawNodes 변경 → openFileSidebar 재생성 → fetchGraph 재생성 → useEffect 재실행 → 1번으로

**발견 방법.** Chrome Network 탭에서 /graph 14회, /freshness 9회 호출을 확인. React DevTools가 아닌 실제 네트워크 요청 수로 진단.

**결과.** `openFileSidebarRef = useRef(openFileSidebar)`로 안정적 참조를 유지하고, fetchGraph 의존성 배열에서 openFileSidebar 제거. 페이지 로드 시 /graph 호출 1회로 정상화.

```tsx
// 순환 의존 제거 전
const fetchGraph = useCallback(async () => {
  buildLayout(rn, re, labelMode, layoutPreset, openFileSidebar)  // openFileSidebar 직접 사용
}, [..., openFileSidebar])  // rawNodes → openFileSidebar 변경 시마다 재생성

// 순환 의존 제거 후
const openFileSidebarRef = useRef(openFileSidebar)
useEffect(() => { openFileSidebarRef.current = openFileSidebar }, [openFileSidebar])

const fetchGraph = useCallback(async () => {
  buildLayout(rn, re, labelMode, layoutPreset, openFileSidebarRef.current)  // ref로 접근
}, [...])  // openFileSidebar 제거 → 순환 끊김
```

**면접 포인트.** React 18 + useCallback 의존성 배열에서 함수 참조가 불안정할 때 발생하는 무한 루프 패턴. "Effect가 실행될 때마다 상태를 변경하고, 그 상태가 Effect를 트리거하는 값을 바꾼다" 는 고전적 순환 의존 케이스. useRef로 최신 값을 읽으면서 의존성 배열에서 제외하는 패턴은 React 공식 문서에서도 권장하는 해법.

---

## 도메인 뷰 — Codeprint 전용 하드코딩 → 범용 동적 추출로 전환 (2026-06-12)

**문제.** `extractDomain()` 함수의 `DOMAIN_SUBS` 허용 목록이 Codeprint 자체 도메인 이름(`project`, `user`, `graph`, `analysis`, `community`, …)으로 하드코딩되어 있었다. 다른 사용자의 프로젝트를 분석하면 경로의 서브폴더 이름이 허용 목록과 일치하지 않아 모든 파일이 `common` 박스 하나에 몰리는 문제가 있었다. 즉, 도메인 뷰가 Codeprint 자신의 코드에서만 동작하고 외부 프로젝트에서는 사실상 무용지물이었다. MVP가 완성됐다고 볼 수 없는 상태였다.

**원인.** 초기 개발 시 Codeprint 자체 코드를 테스트용으로 사용하다보니 `DOMAIN_SUBS` 목록을 Codeprint 도메인으로 채웠고, 범용 추출 로직을 별도로 구현하지 않았다.

**결정.** `DOMAIN_SUBS` 허용 목록 제거, 범용 동적 추출로 전환.

- **레이어 키워드** (`domain`, `application`, `infrastructure`, `pages`, `features`, `modules` 등) 이후 첫 번째 의미 있는 서브폴더를 도메인으로 추출
- 서브폴더가 없으면 파일명 PascalCase 첫 단어에서 추출 (`DashboardPage` → `dashboard`, `UserService` → `user`)
- `DOMAIN_COLORS` 정적 맵도 제거 → `buildDomainColorMap(domains)` 함수로 12색 팔레트 동적 할당

**결과.** DDD Java 프로젝트 (`domain/user/`, `application/graph/`), feature-based React (`features/cart/`), flat-pages React (`pages/DashboardPage.tsx`) 모두 동작. 어떤 프로젝트를 분석해도 발견된 도메인에 맞게 색상 박스가 자동 생성된다.

---

## 계층 뷰 — 비DDD 레이어 섹션 박스 미표시 (2026-06-12)

**문제.** `LAYER_META_PRE`가 DDD 레이어명(`infrastructure`, `domain`, `application`, `interfaces`, `pages`, `components`, `hooks`)만 정의하고 있어, 비DDD 프로젝트(`controllers/`, `services/`, `models/` 등)의 레이어는 섹션 박스가 렌더링되지 않았다. 파일은 보이지만 레이어 구분 박스 없이 나열되어 계층 뷰가 의미없었다.

**결정.** `LAYER_META_PRE`에 없는 레이어명은 fallback 회색 팔레트로 섹션 박스를 생성. 레이어명 첫 글자를 대문자로 표시. 기존 DDD 레이어는 기존 색상 그대로 유지.

**결과.** Express.js(`controllers/services/models/`), Go(`handler/repository/`), Django(`views/models/serializers/`) 등 어떤 폴더 구조도 계층 뷰에서 섹션 박스로 표시.

---

### 레이어 토글 — extent:'parent' 노드에 hidden 미전파 (2026-06-08)

**문제.** 레이어 불투명 토글 시 group 노드만 hidden 처리했는데, React Flow의 `extent: 'parent'` 파일 노드와 그 자식 함수 노드는 부모가 hidden이 되어도 화면에 계속 노출됐다.

**이유.** React Flow는 `extent: 'parent'` 속성 노드에 부모의 `hidden` 상태를 자동으로 cascade하지 않는다. 노드 각각에 명시적으로 설정해야 한다.

**결과.** `toggleLayerOpaque`에서 3단계(group → file → function) 모두 명시적으로 hidden 처리하도록 수정.

---

### 레이어 토글 — 섹션 박스 높이가 48px으로 줄어드는 문제 (2026-06-08)

**문제.** 토글 시 섹션 박스 높이가 내용 없는 상태로 줄어들어 색상 오버레이가 좁은 영역만 덮었다.

**이유.** 이전 구현에서 opaque 시 섹션 높이를 48px로 설정하는 코드가 남아 있었다.

**결과.** 섹션 높이를 변경하지 않고 그대로 유지. SectionNode가 이미 전체 크기에 opaqueColor 오버레이를 렌더링하도록 구현되어 있어 높이 변경 불필요.

---

## 설계 결정

### AppHeader 자체 데이터 페칭 패턴 (2026-06-08)

**문제.** 각 페이지마다 `/api/auth/me`를 호출하고 user 데이터를 AppHeader에 props로 전달하는 구조였으나, GraphPage 등 일부 페이지에서 헤더가 없거나 로그인 상태가 표시 안 되는 문제 발생.

**결정.** AppHeader가 내부에서 직접 `/api/auth/me`를 호출하도록 변경. 모든 페이지에서 `<AppHeader />`만 선언하면 자동으로 사용자 정보를 불러와 표시.

**탈락 이유.** props 전달 방식은 각 페이지가 별도로 auth 상태를 관리해야 해서 일관성 유지가 어렵고, GraphPage처럼 레이아웃이 다른 페이지에서 누락되는 문제가 반복됐다.

---

### Pages/Components 섹션 분리 결정 (2026-06-08)

**문제.** Pages와 Components를 하나의 섹션 박스에 묶었으나 사용자가 둘이 연관이 없고 혼란스럽다고 판단.

**결정.** 각자 별도 섹션 박스 사용. `LAYER_SECTION_KEY`에서 pages/components 제거, `LAYER_COLUMN`에서 pages=0, components=1로 분리.

---

### DB 노드 레이어 배치 — 오른쪽 끝 고정 (2026-06-08)

**문제.** DB 노드가 infrastructure 그룹 왼쪽에 배치되어 다른 레이어와 간격 조절이 안 되는 것처럼 보였다.

**결정.** 모든 그룹의 오른쪽 끝 + 80px 위치로 배치. 기존 레이어 컬럼 시스템 외부에서 일관된 간격 유지.

---

### AdminPage localStorage 키 오류 — `token` → `jwt` (2026-06-06)

**문제.** AdminPage에서 `localStorage.getItem('token')`으로 JWT를 읽도록 작성했으나, 실제 앱에서는 `jwt` 키로 저장하고 있었다. 런타임 검증(Chrome 직접 접속) 중 토큰이 null로 읽혀 로그인 화면으로 리다이렉트됨을 발견.

**이유.** DashboardPage 등 기존 코드가 `jwt` 키를 쓰고 있었는데 새 파일 작성 시 일관성 확인을 놓쳤다.

**결과.** `localStorage.getItem('jwt')`로 수정. 재발 방지를 위해 상수화 또는 공유 유틸 분리 검토 대상으로 남겨둠.

---

### 상위레이어 감추기 zIndex 방식 실패 → hidden 방식으로 교체 (2026-06-06)

**문제.** DDD 범례의 ○ 버튼을 클릭해도 layer-section 노드가 시각적으로 자식 노드들을 가리지 않았다. 버튼 상태(◑)는 변하지만 캔버스에는 변화 없음.

**이유.** 기존 구현은 `layer-section-*` 노드에 `zIndex: 9999`를 설정하고 `data.opaque: true`로 SectionNode가 어두운 배경을 렌더링하도록 했다. 그러나 React Flow v12에서 `parentId`를 가진 자식 노드(group/file/function)들은 DOM 상에서 부모 섹션 노드의 형제로 렌더링되며, React Flow 내부 로직이 자식 노드를 부모 위에 그리도록 z-order를 관리해 zIndex: 9999가 실질적으로 무효화됐다.

**결과.** GroupNode의 `toggleOpaque` 방식을 본보기로 삼아, section이 opaque 상태가 될 때 자손 노드(group → file → function 3단계)에 `hidden: true`를 설정하는 방식으로 교체. `applyPresetConfig`에서 프리셋 복원 시도 동일하게 처리.

---

### 그래프 노드 클릭 시 뷰포트 줌 초기화 (2026-06-05)

**문제.** 노드를 클릭하면 화면 배율이 초기화되어 전체 그래프 줌아웃 상태로 돌아갔다.

**원인.** `startPlayback` 호출 → `setPlaybackCursor(0)` → playbackCursor useEffect 발동 → `fitView({ nodes: [...] })` 실행. playbackPlaying 여부와 무관하게 항상 fitView가 발동됐다.

**해결.** playbackCursor useEffect에 `!playbackPlaying` 조건 추가. 재생 중(playbackPlaying=true)일 때만 뷰포트 추적 fitView를 실행하도록 변경.

**결과.** 노드 클릭 시 줌 유지. 재생 버튼을 누를 때만 현재 노드로 뷰포트 이동.

---

### 사이드바 null 접근 오류 — 블랙 화면

**문제.** 그래프 페이지 전체가 블랙 화면으로 렌더링됐다.

**원인.** 우측 사이드바를 항상 표시하도록 리팩터링하면서 `sidebar`가 null인 상태에서 `sidebar.kind`를 직접 접근.

**해결.** 사이드바 콘텐츠 블록 전체를 `sidebar &&`로 감쌈.

**결과.** 기본 상태(미선택)에서 안내 텍스트 표시, 엣지/노드 클릭 시 상세 정보 정상 표시.

---

### button 중첩 — 클릭 이벤트 씹힘

**문제.** GraphPage 엣지 섹션에서 "button cannot appear as a descendant of button" 경고 발생. 일부 브라우저에서 클릭 이벤트가 씹혔다.

**원인.** 엣지 항목 전체를 감싸는 외부 `<button>` 안에 아이콘용 `<button>`이 중첩된 구조. HTML 스펙상 button 안에 button은 불가.

**해결.** 외부 `<button>`을 `<div role="button" tabIndex={0} onKeyDown={...}>`으로 교체. 키보드 접근성도 함께 유지.

**결과.** 경고 제거, 클릭 이벤트 정상 동작.

---

## 설계 결정

### 고립 그룹 구분 제거

**문제.** FUNCTION_CALL/INSTANTIATION 엣지 추가 전에는 연결이 없는 고립 그룹을 별도 섹션으로 분리했다.

**제거 이유.** 엣지가 추가되면서 실질적으로 고립 그룹이 거의 없어졌고, 오히려 레이아웃을 복잡하게 만들었다.

**결과.** `graphLayout.ts`에서 `isIso` 플래그, `__iso-section__` 박스 생성 코드 전부 제거. 레이아웃 단순화.

---

### 분석 완료 알림 — 추가 후 제거

**문제.** 분석이 완료되면 다른 탭에 있어도 인지할 수 있도록 브라우저 OS 알림을 구현했다.

**제거 이유.**
- 브라우저 알림 권한 요청이 첫 방문 사용자에게 부담으로 작용
- 진행률 게이지가 이미 완료를 시각적으로 표현하므로 중복
- 알림이 필요할 만큼 분석 시간이 길지 않음 (보통 5~30초)

**결과.** 알림 코드 전체 제거. 게이지 애니메이션으로 완료 피드백 대체.

---

### GitHub 재연결 버튼 버그 2건 (2026-06-05)

**문제 1.** 재연결 버튼 클릭 시 빈 페이지.
**원인.** `<a href="/oauth2/authorization/github">`로 상대경로 작성 → 프론트(3000포트)로 요청이 가서 404.
**결과.** `window.location.href = apiUrl + '/oauth2/authorization/github'` 절대경로로 수정. LandingPage/LoginPage와 동일한 패턴 사용.

**문제 2.** 배너가 토큰 있는 사용자에게도 항상 표시.
**원인.** `!user.hasGithubToken` 조건 — 백엔드 응답에 필드가 없으면 `undefined` → `!undefined === true`로 배너 항상 표시.
**결과.** `user.hasGithubToken === false` 명시적 체크로 수정.

**교훈.** 두 버그 모두 PR 전에 버튼 한 번 클릭했으면 즉시 발견. 런타임 검증 생략의 직접적 결과.

---

### 흐름 재생 UX 버그 다수 (2026-06-05 → 06-06)

**문제 1.** `buildFlowPath`의 `FLOW_TYPES`에 `CONTAINS`가 포함돼 있어, 파일 노드가 흐름 경로의 upstream 슬롯을 차지 → 실제 caller가 표시 안 됨.
**결과.** `FLOW_TYPES`에서 `CONTAINS` 제거. FILE→FUNCTION 포함 관계는 흐름 경로 탐색 대상이 아님.

**문제 2.** 엣지/파일/그룹 노드 클릭 시 이전 흐름 재생 상태가 유지됨.
**결과.** `handleEdgeClick` 상단, `handleNodeClick`의 fileNode/groupNode/sectionNode 분기에 `resetPlayback()` 추가.

**문제 3.** 사이드바의 caller/callee 링크 클릭 시 사이드바가 해당 함수로 업데이트되지 않음. `FlowChainSection`, `FuncChainRow`, `CallChainRow` 각각 별도의 로직이 중복돼 있었고 사이드바 업데이트가 누락된 곳이 많았음.
**결과.** `openFuncNode(nodeId)` 헬퍼 함수 추출 — setSidebar + startPlayback + setCommentNodeId + 코멘트 조회를 한 곳에서 처리. 모든 onNav 콜백이 이를 호출하도록 통일.

**문제 4.** 표시 모드(이름/주석)가 우측 사이드바 FuncChainRow에 반영되지 않음.
**결과.** `FuncChainRow`에 `labelMode` prop 추가. 주석 모드일 때 `funcComment`가 있으면 그것을 라벨로 표시.

**문제 5.** DB_TABLE 노드 클릭 시 흐름 재생이 시작되지 않음.
**결과.** `handleNodeClick`의 DB_TABLE 분기에 `startPlayback(node.id)` 추가.

---

### 흐름 자동 시각화 — 노드 하이라이트 방식 결정 (2026-06-05)

**문제.** FUNCTION 노드는 React Flow 기본 노드 타입 (FileNode/GroupNode 아님) → `data` prop으로 스타일을 전달해도 기본 렌더러가 무시함.
**이유.** `nodeTypes`에 등록된 컴포넌트(FileNode, GroupNode)는 `data`를 직접 읽지만, 기본 노드는 React Flow 내부 렌더러 사용.
**결과.** `setNodes`로 `style` prop 직접 업데이트 (`outline`, `boxShadow`). FileNode에는 `data.playbackActive` 오버레이도 추가해서 두 방식 병행.

**문제 2.** 경로 엣지 `hidden: true` 상태를 재생 중 해제해야 하는데, 전체 경로를 한 번에 unhide하면 모든 FUNCTION_CALL 엣지가 동시에 표시됨 (시각적 혼잡).
**결과.** `visitedItems = playbackItems.slice(0, cursor + 1)` — 커서까지 지나온 항목만 unhide. 스텝별로 엣지가 순차 등장하는 효과.

---

### 흐름 재생 — 선형 경로에서 호출 트리로 전환 (2026-06-06)

**문제.** 기존 `buildFlowPath`는 함수 호출이 분기될 때 첫 번째 자식만 따라가는 선형 탐색 → 다른 분기가 숨겨짐. 사용자가 "분기가 생기면 하위흐름으로" 요구.

**이유.** 실제 백엔드 흐름은 A→B, A→C 같은 분기 구조가 흔함. 선형 경로로는 전체 그림을 전달할 수 없음.

**결과.**
- `buildCallTree(nodeId, rawEdges, rawNodes)` 재귀 트리 빌드 함수 도입 — upstream 추적 → 루트 함수 탐색 → 전체 downstream 트리.
- `CallTreePanel` 컴포넌트 — 트리를 인덴트 레이아웃으로 렌더링. 분기점에 ⑂ 아이콘 표시. 현재 경로는 amber로 강조.
- B 기능: 분기점 도달 시 자동 일시정지 — 플레이어가 분기를 선택하도록 유도.
- C 기능: `selectBranch(nodeId)` — 트리 노드 클릭 시 해당 노드까지의 경로로 재생 전환. `findPathInTree` + `extendToDefaultLeaf`로 경로 재계산.
- API_CALL 진입점 prepend: 루트 함수 소속 컨트롤러 파일에 API_CALL이 있으면 프론트엔드 FILE 노드를 트리 최상단에 추가.

**계층 레이아웃 방향 변경.** `LAYER_COLUMN`을 `infrastructure(0)…pages(4)`에서 `pages(0)…infrastructure(5)`로 뒤집음. 요청 흐름(프론트→백엔드→DB) 방향과 일치시키기 위함.

---

### 흐름 재생 — 노드+엣지 교차 스텝에서 노드 전용 스텝으로 전환 (2026-06-08)

**문제.** `pathToPlaybackItems`가 노드와 엣지를 교차(node→edge→node→edge)로 스텝에 넣어 실제 흐름 단계의 2배 스텝이 생성됐다. 엣지 스텝은 "함수 호출"이라는 시맨틱이 없어 사용자 입장에서 의미없는 클릭이 필요했다.

**이유.** 엣지는 두 노드 사이의 관계이지 독립적인 흐름 단계가 아님. 흐름 재생의 단위는 "어떤 컴포넌트가 실행됐는가(노드)"이어야 함.

**결과.**
- `PlaybackItem` 타입을 `{ id: string; incomingEdgeId?: string; incomingEdgeType?: string }`으로 변경. 노드만 스텝이 되고, 직전 엣지 타입은 메타데이터로 보유.
- `EDGE_TYPE_LABEL` 맵 추가: `FUNCTION_CALL→호출`, `API_CALL→HTTP 요청`, `DB_READ→DB 조회`, `DB_WRITE→DB 저장`, `IMPORT→import`.
- 재생 패널 UI에서 스텝 전환 시 직전 엣지 타입 레이블을 표시해 레이어 경계를 시각화.
- `playbackEdgeIdsRef`(useRef)로 경로 엣지 ID를 관리해 `applyEdgeVisibility`가 재생 중 경로 엣지를 다시 hide하는 충돌 방지.

---

### 계층형 뷰 vs 도메인 뷰 — 두 시각화 이중 제공 결정 (2026-06-08) ★면접 어필 포인트

**문제.** 기존 "DDD 레이어" 범례(interfaces/application/domain/infrastructure)는 이름과 달리 실제로는 **계층형 아키텍처 뷰**였다. 레이어 별로 모든 도메인의 파일을 수평으로 묶는 구조이기 때문에, 하나의 기능(도메인)이 버튼 클릭 → API 호출 → 서비스 → DB까지 수직으로 흐르는 것을 한눈에 볼 수 없었다.

**이유.** DDD(도메인 주도 설계)의 핵심은 바운디드 컨텍스트 — 즉, 하나의 도메인(project, user, graph...)이 Controller + Service + Entity + Repository를 수직으로 소유한다는 것이다. 시각화가 진짜 DDD를 표현하려면 project 도메인 박스 안에 ProjectController, ProjectService, Project 엔티티, ProjectRepository가 함께 묶여야 하고, 흐름 재생이 그 박스 안에서 위→아래로 흘러야 한다. 기존 계층형 뷰로는 이 수직 슬라이싱이 불가능했다.

**결정.**
- 계층형 뷰 유지: 레거시 프로젝트(MVC, 계층형 아키텍처)를 위한 시각화. 범례 이름을 "DDD 레이어" → "계층형 레이어"로 수정.
- 도메인 뷰 신규 추가: DDD 프로젝트를 위한 시각화. 바운디드 컨텍스트(project, user, graph, analysis, community, ai, notice, donation, collaboration) 기준으로 파일을 그룹핑. 프론트엔드 파일도 도메인 안에 포함(도메인 흐름의 시작 = 사용자 프론트 입력).
- 허브 프리셋 제거: 도메인 뷰로 대체 가능하고 독립적인 가치가 없음.

**탈락 이유 (단일 뷰만 제공).** 계층형만 제공하면 DDD 프로젝트 분석이 의미 없어지고, 도메인만 제공하면 레거시 프로젝트 사용자가 소외된다. 두 뷰를 동시 제공해 Codeprint가 다양한 아키텍처 패턴을 아우르는 도구가 된다.

**면접 어필 포인트.** "DDD라고 이름 붙였지만 실제로 구현한 건 계층형 뷰였다는 걸 개발 중 스스로 인지했고, 두 개념의 차이를 시각화 설계에 반영했다" — 바운디드 컨텍스트와 수직 슬라이싱의 의미를 이해하고 있음을 실제 코드로 증명하는 포인트.

## 배경이미지 적용 범위 — 전체 페이지 → GraphPage 전용 (2026-06-11)

**문제**: 배경이미지가 모든 페이지에 적용되자 커뮤니티/설정 등 텍스트 중심 페이지에서 가시성이 저하됨.

**결정**: 배경이미지는 GraphPage에서만 적용. App.tsx에서 전역 적용 로직 제거, GraphPage 마운트 시 bg URL 직접 fetch + body 스타일 적용, 언마운트 시 cleanup.

**라이트/다크 테마**: AppHeader에 ☀️/🌙 토글 추가. `html[data-theme]` 속성 + CSS 변수로 구현. localStorage에 저장.

## 도메인 뷰 조작성 개선 — 탭 통합·확대·DB 인접·색상·튐·드래그 (2026-06-14)

여러 UX 개선 요청을 처리하며 버그 5건을 함께 잡았다. 모두 `GraphPage.tsx`·`graphLayout.ts` 두 파일.

**#1 상단 탭바를 좌측 사이드바로 통합.**
- 문제: 도메인 필터(상단 탭)와 흐름 목록(좌측 도메인 텍스트 클릭)이 분리돼 있었다.
- 결과: 상단 탭바 제거. 좌측 도메인 클릭이 `[그래프 필터 + 우측 흐름 목록 + 확대]`를 한 번에 수행(`activateDomain`). 좌측에 "전체 보기" 리셋 추가, 중복되던 `→` 이동 버튼 제거. 계층형 범례에도 동일하게 클릭=필터 추가(탭바 제거로 인한 회귀 방지).

**#2 도메인 색상 불일치 (버그).**
- 문제: AI 도메인이 좌/우측 사이드바에선 핑크, 그래프·탭에선 파랑으로 달랐다.
- 이유: 좌측 범례가 **하드코딩 색 배열**을 썼는데, 그래프·탭은 `buildDomainColorMap`(도메인 이름 알파벳 인덱스 → 팔레트)을 썼다. 두 소스가 달라 색이 갈렸고, 하드코딩 목록은 일부 도메인(adapter·github·payment·team 등)이 누락됐다.
- 결과: 범례를 `domainColorMap` 단일 소스로 동적 생성 → 그래프·탭·좌·우 사이드바 색 일치 + 누락 도메인 자동 표시.

**#3 fitView 확대 애니메이션이 중간 배율에서 멈춤 (버그).**
- 문제: 도메인 전환 시 `fitView`가 어중간한 배율(예: scale 0.205)에 멈춰 도메인이 작게 보였다.
- 이유: 줌 애니메이션(450ms) 도중 DB 재배치·사이드바 오픈 리렌더가 애니메이션을 중간에 끊었다. 또 `fitView({nodes})`는 새로 추가된 파일 노드의 측정 전 좌표나 stale DB 위치를 포함해 잘못된 박스에 맞췄다.
- 결과: 측정·타이밍에 의존하지 않도록 **명시적 사각형 `fitBounds`** + `duration: 0`(즉시 fit)로 교체. 섹션 좌표·크기는 필터와 무관하게 고정이라 도메인 박스 + 아래 DB 영역을 정확히 계산해 맞춘다.

**#4 탭 볼 때 DB가 도메인에서 멀리 떨어짐 (버그/개선).**
- 문제: DB 테이블은 전역 DB 열(전체 그래프 중앙 Y)에 배치돼, 단일 도메인 필터 시 그 도메인의 DB가 flow 좌표로 ~6,800단위 아래에 동떨어졌다(연결선도 기본 숨김이라 떠 있는 빈 박스처럼 보임).
- 결과: 단일 도메인 필터(`activeDomainTab`) 시 그 도메인의 DB 테이블을 `displayNodes`에서 **도메인 섹션 바로 아래**(섹션 폭 안에서 줄바꿈)로 재배치하고, DB 연결선도 표시. 전체 보기는 기존 우측 DB 열 유지(scope를 필터 뷰로 한정).

**#5 노드 클릭 시 화면이 흐름 루트로 튐 (버그).**
- 문제: AI 도메인의 컨트롤러 함수를 클릭하면 화면이 왼쪽 빈 곳으로 날아갔다.
- 이유: 노드 클릭 → `startPlayback`이 흐름 재생을 시작하면서 `playbackCursor=0`(흐름 루트)으로 `fitView`. 루트는 보통 다른 도메인의 프론트 진입점(예: SettingsPage.tsx)이라, 필터 상태에선 화면 밖이라 빈 곳으로 튀었다.
- 결과: `playbackJustStarted` ref로 **클릭 직후 첫 fitView를 건너뜀**(화면 고정). 이후 스텝 이동은 `getNodes()`로 **현재 렌더된 노드만 대상**으로 제한(필터 밖 노드로 안 튐).

**#6 도메인 뷰에서 파일 드래그 시 빈 박스로 분리 (버그).**
- 문제: 파일을 드래그하면 파일만 이동하고 함수는 제자리에 남아 빈 박스가 됐고, 되돌릴 수 없었다.
- 이유: 도메인 뷰에서 함수 노드가 파일이 아니라 **도메인 섹션의 자식**으로 배치돼, 파일 드래그가 함수를 데려가지 못했다. 드래그 위치는 서버 저장되지만 레이아웃 재계산 시 무시되므로(`posX/posY` 미적용) 리로드로만 복구 가능했다.
- 결과: 도메인 뷰에서도 함수를 **파일의 자식**(`parentId: file.id`, `extent: 'parent'`)으로 배치(계층형 뷰와 동일). 렌더 위치는 동일하되 파일 드래그 시 함수가 자식으로 함께 이동 → 분리 불가능.

---

## 대형 레포 렌더링 — 뷰포트 컬링 (Phase 2 #1, perf/viewport-culling)

**문제:** 파일·함수 수백 개의 대형 그래프에서 렌더가 멈춘다(자기 그래프 319파일도 freeze — Context55/58 북극성). React Flow가 화면 밖 노드까지 전부 DOM에 그려, 노드 수에 비례해 DOM/레이아웃 비용이 폭발한다.

**이유(측정-우선, Rule 11):** 병목의 1차 원인은 "전부 렌더". React Flow v12(@xyflow/react)는 `onlyRenderVisibleElements` prop으로 **뷰포트에 들어온 노드만 렌더**하는 내장 컬링을 제공한다. 노드에 이미 명시적 width/height(style)가 있어 v12가 측정 후 정확히 컬링한다(전제 충족).

**대안 탈락:** 수동 가상화(react-window류)나 displayNodes에서 직접 뷰포트 필터링 — 부모/자식(섹션→파일→함수) sub-flow 좌표 계산과 fitView·미니맵·엣지 컬링을 직접 재구현해야 해 복잡도·회귀 위험이 크다. 내장 옵션이 한 줄로 동일 효과 + 검증된 경로.

**결과:** GraphPage·ShareGraphPage·DiffPage·CommunityPostGraphPage 4개 그래프 렌더 페이지의 `<ReactFlow>`에 `onlyRenderVisibleElements` 한 줄씩 추가(같은 대형 그래프를 그리므로 일관 적용). `tsc -b` 통과.

**측정 기반 다음 단계 보류:** Phase 2의 도메인 기본 접힘·노드 LOD는 컬링만으로 freeze가 해소되는지 **브라우저 검증 후** 필요성을 재평가한다(투기적 선구현 금지). 검증 포인트: 319파일 freeze 해소 + 부모/자식 sub-flow(그룹 박스·파일 안 함수 노드)가 스크롤·확대 시 정상 렌더되는지.

## GraphPage/ShareGraphPage 공유 컴포넌트 추출 — PR1 상수/순수함수 (2026-07-02)

**문제:** ShareGraphPage 뷰어 확장 작업(Context94) 중 색상 하드코딩·범례 빈 목록 등 회귀가 반복돼, 두 페이지가 "보기" 로직을 서로 다르게 재구현하고 있음이 드러남. 사용자 판단으로 페이지 통째 병합 대신 공유 컴포넌트/훅 추출로 방향 확정, PR을 리스크 낮은 순으로 분할(PROGRESS.md 백로그 참조).

**PR1 스코프 선택:** 코드 검색 결과 `isDbEdgeType`·`applyEdgeVisibility`(엣지 hidden 판정 로직)·`minZoom`/`maxZoom` 값(0.05/2)이 두 파일에 **완전히 동일하게** 중복돼 있었음 — 순수 함수/상수라 추출 시 동작 변경 위험이 0에 가까움. `applyOpaqueLayerSet`(레이어/도메인 dim 토글)도 중복이지만 GraphPage는 단일 레이어 토글(`toggleLayerOpaque`)로, ShareGraphPage는 전체 집합 배치 적용(`applyOpaqueLayerSet`)으로 구현 형태가 달라 완전 동일 리팩토링이 아님 — 이건 백로그 candidate #1(상태 훅 통합, "가장 신중하게")과 겹치므로 이번 PR1에서 제외하고 이후 PR로 미룸.

**결과:** `graphLayout.ts`(두 페이지가 이미 import하던 공유 유틸)에 `GRAPH_MIN_ZOOM`/`GRAPH_MAX_ZOOM`·`isDbEdgeType`·`applyEdgeVisibility` export 추가, 양쪽 페이지의 로컬 정의 제거 후 import로 교체. `tsc -b` 통과, ShareGraphPage를 claude-in-chrome으로 공개레포(gin)에 라이브 로드해 콘솔 에러 없음·그래프 정상 렌더 확인(GraphPage는 OAuth 로그인 필요해 코드 대조로만 확인).

## GraphPage/ShareGraphPage 공유 컴포넌트 추출 — PR2 노드검색+상단툴바 (2026-07-03)

**문제:** PR1 다음 순서인 "노드검색+상단툴바"를 시작하려 코드를 보니, 계획서(PROGRESS.md)가 예상한 것과 달리 노드검색은 완전한 코드 중복이 아니었음 — GraphPage는 원본 `RawNode[]`를 검색(주석 매치·최대 10개), ShareGraphPage는 레이아웃 계산이 끝난 React Flow `Node[]`를 검색(제한 없음·주석 미지원)해 데이터 모양 자체가 달랐다. 사용자에게 "왜 다른지, 통일할 가치 있으면 통일해라" 확인 받음 — 실제로는 의도된 아키텍처 차이가 아니라 ShareGraphPage가 나중에 더 단순하게 구현되며 자연 발생한 드리프트(이번 작업 전체가 다루는 패턴과 동일).

**결정 — 검색어 있을 때만 통일, 빈 상태는 유지:** `searchNodes(rawNodes, query, limit=10)` 순수 함수를 `graphLayout.ts`에 추가(GraphPage 로직 그대로 이관). GraphPage는 이 함수를 호출하도록 교체(순수 리팩토링). ShareGraphPage는 **검색어가 있을 때만** `rawNodesCache`(이미 보유 중)에 대해 같은 함수를 호출 — 결과 10개 제한·주석 매치·정확한 타입 기반 아이콘(FILE/FUNCTION/DB_TABLE)까지 GraphPage와 동일해짐. **검색어가 빈 상태(전체 노드 훑어보기)는 그대로 `nodes`(현재 화면에 보이는·hidden 아닌 노드) 기반 유지** — rawNodesCache로 바꾸면 도메인/레이어 필터·opaque 토글로 숨겨진 노드까지 노출되는 회귀가 생기므로 제외. 두 브랜치를 `{id, icon, label}`로 정규화해 렌더 코드는 하나로 통일.

**부수 발견(미수정, task로 분리):** 위 조사 중 ShareGraphPage의 빈 상태 브라우징에서 DB_TABLE 아이콘이 항상 'ƒ'로 잘못 표시되는 기존 버그 발견 — `buildLayout`이 DB_TABLE 노드에 React Flow `type`을 명시적으로 안 정해(undefined) 아이콘 판정(`n.type === 'DB_TABLE'`)이 항상 false. 이번 PR 스코프가 아니라 별도 task로 분리(`spawn_task`).

**상단 툴바:** 레이아웃 전환 버튼(byte-identical)에 더해, 이번에 ShareGraphPage에 라벨모드(이름/주석) 토글을 신규 이식(기존엔 GraphPage 전용 기능 — "Google Sheets" 원칙에 따른 기능 포팅, 단순 리팩토링 아님). 두 버튼이 이제 양쪽에서 완전히 동일해져 `components/GraphViewToggles.tsx`(`LayoutPresetToggle`/`LabelModeToggle`)로 추출, 두 페이지 모두 교체.

**검증:** `tsc -b` 통과. ShareGraphPage를 claude-in-chrome으로 공개레포(gin)에 라이브 로드해 ①빈 상태 훑어보기 ②검색어("router") 입력 시 10개 이하 결과·ƒ 아이콘 정상 ③레이아웃 전환·라벨모드 토글 클릭 후 콘솔 에러 없음 확인. GraphPage는 OAuth라 코드 대조 + `#tour-layout` 앵커 보존 확인만.

## GraphPage/ShareGraphPage 공유 컴포넌트 추출 — PR3 범례 (2026-07-03)

**문제:** PR3 착수 전 조사 결과 GraphPage 범례는 ShareGraphPage보다 훨씬 많은 기능이 얽혀있었음 — 라벨 클릭이 필터링뿐 아니라 `activateDomain`을 통해 흐름 재생(`openDomainFlows`) 사이드바까지 열고, 도메인 모드엔 DB 테이블 전용 범례 줄(클릭 시 `domain-summary` 사이드바 오픈)과 레이어 모드 전용 "전체 보기" 리셋 버튼까지 포함. ShareGraphPage 범례는 라벨이 클릭 불가능한 단순 텍스트였음. 사용자에게 확인 → "공유그래프 본연의 권한(읽기 전용)을 초과하지 않는 선에서 GraphPage 사양에 최대한 맞추라"는 답변.

**적용 범위 판단:** 필터링(라벨 클릭 → 해당 도메인/레이어만 보기)과 "전체 보기" 리셋은 **읽기 동작**이고 ShareGraphPage가 이미 상단 탭바로 동일 필터링을 하고 있어(`setActiveDomainTab` + `fitView`) 범례에 재배선만 하면 됨 — 그대로 포팅. 반면 DB 테이블 범례 줄은 GraphPage의 `domain-summary` 사이드바(흐름 체인 목록을 보여주는 전용 패널)에 의존하는데 **이 사이드바 시스템 자체가 ShareGraphPage에 없음**(ShareGraphPage 사이드바는 단일 `selectedNode` 표시만 지원) — 이건 "범례" 한 항목이 아니라 계획서가 이미 최우선순위 뒤로 미뤄둔 "노드상세패널/흐름재생"(candidate #5, "가장 복잡한 조건부 렌더라 마지막 순위") 전체를 새로 만드는 일이라 이번 PR3 범위에서 제외 — DB 범례 줄은 이번엔 포팅하지 않음.

**데이터 identity 불일치 발견·해결:** GraphPage는 `activeDomainTab`에 소문자 키(예: `application`)를 저장하고 opaque 토글도 같은 키를 씀. ShareGraphPage는 `activeDomainTab`에 대문자 라벨(예: `Application`, 섹션 노드의 `data.label`과 매칭)을 저장하지만 opaque 토글(`toggleDomainOpaque`)은 여전히 소문자 키가 필요 — 같은 논리적 엔티티가 두 페이지에서 다른 identity 공간을 씀. `GraphLegend` 컴포넌트의 `onLabelClick`/`isActive`를 `key` 문자열이 아닌 **엔트리 객체 전체**(`{key,label,color}`)를 받도록 설계해 각 페이지가 자기 규약에 맞는 필드(GraphPage=`entry.key`, ShareGraphPage=`entry.label`)를 골라 쓰도록 해결.

**결과:** `components/GraphLegend.tsx` 신규 — opaque 토글 버튼(2페이지 4곳에서 byte-identical하던 인라인 스타일 제거) + 옵션 라벨클릭 + 옵션 전체보기 리셋. ShareGraphPage에 `activateTab`(필터+fitView, 기존 상단 탭바 로직과 동일해 탭바 자체도 이 함수로 통합) 신규 추가, 범례 라벨 클릭·전체보기 리셋 버튼 신규 이식(기존엔 불가능했던 기능).

**검증:** `tsc -b` 통과. ShareGraphPage(ripgrep, 레이어 모드 — 14개 크레이트라 범례 노출)로 클릭 검증: 라벨 클릭 시 범례·상단 탭바 양쪽 active 하이라이트 동기화, 전체보기 리셋 정상, opaque 토글(○→◑) 별도 정상 동작, 콘솔 에러 없음. **도메인 모드 범례는 미검증** — 벤치마크용 공개레포 5개(gin·sinatra·Newtonsoft.Json·mini-redis·ripgrep) 전부 도메인 그룹이 2개 이하라 도메인 모드에서 범례 자체가 렌더되지 않음(코드 조건 `availableTabs.length > 2`). 레이어 모드와 완전히 동일한 컴포넌트·배선 패턴이라 코드 대조로 갈음. GraphPage는 OAuth라 코드 대조만.

## GraphPage/ShareGraphPage 공유 컴포넌트 추출 — PR4 경고 코너패널 셸 (2026-07-03)

**문제:** 코너 플로팅 패널(칩+펼침+헤더) 패턴이 GraphPage에 2곳(우측 하단 "분석·경고", 좌측 하단 "아키텍처 의도"), ShareGraphPage에 1곳("경고") — 총 3곳에서 byte-identical한 셸 구조(칩 버튼·헤더·접기 버튼·스크롤 컨테이너)가 중복돼 있었음. `WarningPanel` 자체(내용물)는 이미 이전 세션에 공유 컴포넌트로 분리돼 있었으나 이를 감싸는 "셸"은 각 페이지가 따로 구현.

**결과:** `components/CornerPanel.tsx` 신규 — `open/onOpen/onClose`, `icon/title/count`, `headerExtra`(옵션 슬롯, GraphPage의 MD 내보내기 버튼 주입), `panelClassName`(너비·최대높이, Tailwind 임의값은 호출부에 리터럴로 전달해 JIT 스캔 보장), `style`(좌/우 위치, 사이드바 폭에 따라 페이지별로 다르게 계산). GraphPage 2곳·ShareGraphPage 1곳 전부 이 컴포넌트로 교체.

**MD 내보내기 함수 이관 + 신규 이식:** `downloadWarningsMd`(경고를 마크다운으로 다운로드, 순수 클라이언트 함수·서버 호출 없음)를 GraphPage 로컬 정의에서 `graphLayout.ts`(`downloadTreeText`와 같은 위치)로 이동. 이미 화면에 보이는 데이터를 파일로 저장하는 **읽기 동작**이라 ShareGraphPage에도 그대로 이식(기존엔 GraphPage 전용, 신규 기능).

**부수 수정:** `headerExtra={warnings.length > 0 && (...)}` 패턴이 `warnings.length`가 0일 때 `0 && X`가 `0`(falsy 숫자)으로 평가돼 React가 문자 그대로 "0"을 렌더링하는 사전 존재 버그를 발견(다른 위치의 count 배지들은 원래도 같은 패턴을 쓰고 있었으나 `CornerPanel` 내부에서 `!!count`로 새로 설계해 문제 없음) — 이번에 손댄 `headerExtra` 줄만 `? ... : undefined` 삼항으로 교정, 관련 없는 다른 줄은 손대지 않음(Surgical Changes).

**검증:** `tsc -b` 통과. ShareGraphPage(Newtonsoft.Json, 경고 93개)에서 라이브 검증: 칩 펼침→헤더 "🔎 경고(93)" 정상, MD 내보내기 버튼(신규 포팅) 클릭 시 에러 없음, 접기 버튼으로 칩 복귀 정상, 콘솔 에러 없음. GraphPage 2개 패널(분석·경고 / 아키텍처 의도)은 OAuth라 코드 대조만.

## GraphPage/ShareGraphPage 공유 컴포넌트 추출 — PR6 사이드바 리사이즈 (2026-07-03)

**문제:** 백로그 "PR6 사이드바 접기/리사이즈 셸" 착수 전 조사 결과, "접기"와 "리사이즈"가 실제로는 완전히 다른 상태였음. 접기(collapse)는 PR #429(2026-07-02)에서 이미 ShareGraphPage에 이식 완료(단, GraphPage의 절대위치 오버레이 구조를 그대로 포트하지 않고 ShareGraphPage 기존 flex 레이아웃에 맞춰 재구현 — 커밋 메시지에 명시). 반면 **리사이즈(사이드바 경계선 드래그로 폭 조절)는 GraphPage에만 있고 ShareGraphPage엔 애초에 존재한 적이 없었음** — `git show`로 PR #429 diff를 직접 확인해 검증. 세션 중 사용자가 "마우스 휠로 화면 확대축소되는 그거 아니냐"고 리사이즈를 캔버스 줌(React Flow 내장, 양쪽 다 이미 있음)과 혼동해 재확인 후 범위 확정.

**결정 — 신규 기능으로 포팅:** 사용자 확인 결과 리사이즈를 ShareGraphPage에도 추가하기로 결정(이전에 없던 기능이라 v0.109.0 MINOR 대상). GraphPage의 좌/우 리사이즈 로직(`leftResizing`/`rightResizing` ref + 전역 mousemove/mouseup 리스너 + delta 계산)이 두 곳에서 거의 동일하게 중복돼 있어, 이번에 `hooks/useSidebarResize.ts`(신규)로 추출 — `direction: 'left'|'right'`로 드래그 방향 반전 처리, `{width, startResize}` 반환. GraphPage 좌/우 인라인 로직을 이 훅으로 교체(dedup) + ShareGraphPage 좌/우에 신규 적용(포팅).

**레이아웃 차이 흡수:** GraphPage 사이드바는 `absolute` 오버레이라 리사이즈 핸들이 캔버스 위에 뜨는 반면, ShareGraphPage는 flexbox 문서 흐름 안에 있어 핸들을 `aside`에 `relative` + 핸들을 `absolute` 자식으로 앵커링하는 것만으로 충분(다른 오버레이 UI의 위치 보정 불필요 — flex가 자동으로 리플로우). 초기 폭은 기존 고정폭(w-56=224px, w-64=256px)을 유지해 첫 렌더 시 시각적 점프 없음, 클램프는 GraphPage와 동일값(좌 160~420, 우 240~520)으로 통일.

**검증:** `tsc -b` 통과. ShareGraphPage(gin)에서 `preview_eval`로 mousedown→mousemove→mouseup 시퀀스를 단계별(각각 별도 호출로 이벤트 루프를 넘겨) 검증 — 동일 eval 호출 내 동기 읽기는 React 배치 업데이트 반영 전이라 스테일 값을 반환하는 함정 발견(이전 세션 교훈과 동일 패턴), 호출을 분리해 재검증 후 드래그 중 실시간 반영(224→284px, +60 일치)·해제 후 추가 이동 무시(284px 고정)·최댓값 클램프(420px) 전부 정상 확인. 접기→펼치기 후에도 리사이즈한 폭(420px) 유지 확인(state 보존, 정상). GraphPage는 OAuth라 코드 대조만(동일 클램프 값·동일 훅 사용).

## 경고 패턴 예외 UI — "무시" 액션 + 규칙 패널 (2026-06-25)

**문제:** 백엔드 패턴 예외(글로브 IGNORE, #375)를 사용자가 쓰려면 UI 필요. opt-out 모델 실용화의 마지막 조각.

**UX 결정(사용자 확정):** ①글로브 기본값=넓게(레이어 단위, `**/application/**`) — 출발/도착 파일에서 알려진 레이어 세그먼트를 찾아 추론, 의도된 패턴은 보통 레이어 단위라 부합. ②미리보기 카운트=클라이언트 계산(서버 왕복 0) — 프론트가 이미 가진 경고 목록에 글로브 매칭(`globToRegExp`는 백엔드 globToPattern과 동일 규칙). ③규칙 관리 위치=경고 패널 안(별도 설정 페이지 아님) — 경고 보는 곳에서 바로.

**★ 데이터 손실 방지:** ArchitectureIntent는 modules·rules·ignore를 한 JSON에 저장하는데, 기존 `ArchitectureIntentPanel.save()`가 modules+rules만 PUT → 백엔드가 ignore를 빈목록으로 덮어씀. 경고 패널에서 추가한 예외 규칙이 의도 패널 저장 시 사라지는 버그. **해결:** ArchitectureIntentPanel이 ignore를 로드해 저장 시 그대로 라운드트립(편집 안 해도 보존). clear()도 ignore 있으면 DELETE 대신 PUT(modules/rules만 비움). saveIgnoreRules도 역으로 modules/rules를 라운드트립.

**구현:** `utils/ignoreRules.ts`(글로브 추론·매칭·API 라운드트립), WarningPanel에 IgnoreRuleForm(인라인, 실시간 카운트)·IgnoreRulesSection(규칙 목록·제거), GraphPage 연동(fileOfNodeId·add/remove→저장→fetchGraph 재조회). nodeIds≥2 관계형 경고만 "무시" 표시. ShareGraphPage(비소유자)는 ignoreOps 미전달로 미표시. `tsc -b` 통과. **브라우저 검증은 사용자 서버 기동 후.**

## 게시글 기반 공유그래프 재설계 — PR-B: 공유 모달·글쓰기 폼 개편 (2026-07-03)

> ⚠️ **일부 대체됨 (2026-07-07)** — 가시성 토글 라벨 "비공개 — 링크로만 접근"은 "네이밍 체계 확정" 항목에 따라 "링크 공유"로 개명. 나머지 결정(체크박스 제거·프리셋 슬롯 방식)은 유효. 아래 원문은 이력 보존용.

**문제.** GraphPage의 기존 "커뮤니티에 공유" 모달은 레이어/그룹/개별 노드를 체크박스로 골라 "숨김"으로 표시하는 방식이었음. 코드 추적 결과 이 체크박스 state(`shareHiddenLayers/Groups/Nodes`)는 그래프 렌더링에 **전혀 영향을 주지 않고** `applyPresetConfig`가 프리셋 로드 시 값을 복원하기만 하는, 사실상 죽은 기능이었음(PR-A가 만든 스냅샷 방식으로 대체 가능).

**결정.**
1. **체크박스 3종 완전 삭제** — state·`availableLayers`(하드코딩 8개 레이어명)·`availableGroups`(useMemo) 전부 제거, 사용처가 이 모달뿐임을 grep으로 확인 후 삭제. `buildCurrentConfig`/`applyPresetConfig`에서도 `hiddenLayers/hiddenGroups/hiddenNodes` 필드 제거(프리셋 config 스키마에서 완전히 빠짐 — 기존 저장된 프리셋에 남아있어도 이제 아무도 안 읽어 무해).
2. **프리셋 슬롯 드롭다운 + 공개/비공개 토글로 교체** — 이미 로드돼 있던 `presets`(4슬롯) state를 그대로 재사용(추가 API 호출 불필요). `handleShareSubmit`이 `graphId`+hidden* 대신 `graphSnapshots:[{projectId,presetSlot}]`+`visibility` 전송.
3. **커뮤니티 글쓰기 폼(`CommunityPage.tsx`)도 동일 개편** — "그래프 연결"이 프로젝트 선택 시 최신 그래프를 바로 붙이던 것을, 프로젝트 선택 → 프리셋 목록 조회(`/api/graphs/{graphId}/presets`) → 슬롯 선택으로 변경. `linkedGraphId`→`linkedProjectId`로 개명(더 이상 그래프ID를 직접 안 다룸, 서버가 projectId로 최신 그래프를 알아서 해석).

**런타임 검증 중 발견한 버그(백엔드):** 비공개 게시글이 커뮤니티 전체 피드에 그대로 노출되는 실제 버그를 발견 — 상세 원인·수정은 `decisions/DECISIONS_BACKEND.md` "PR-B" 참조.

**결과.** `tsc -b` 통과. claude-in-chrome으로 실 로그인 세션(GitHub OAuth) E2E 검증 — GraphPage 공유 모달에서 슬롯3(도메인-이름) 선택+비공개 등록, 커뮤니티 글쓰기 폼에서 프로젝트 연결 후 슬롯1(계층-이름) 기본값+공개 등록, 둘 다 DB에서 `post_graph_snapshots.config`가 선택한 슬롯과 정확히 일치함을 확인. 테스트 게시글은 삭제로 정리(겸 CASCADE 재확인). ChangelogPage v0.110.0(feature).

## 게시글 기반 공유그래프 재설계 — PR-C 2단계: 다중 스냅샷 카드 목록 + 단일 스냅샷 뷰어 (2026-07-04)

**문제.** PR-C 1단계 백엔드 엔드포인트(`GET /snapshots`)는 있었지만 이를 호출하는 프론트가 없어 신규 스냅샷 게시글은 여전히 그래프를 볼 방법이 없었음. 레이아웃 방식을 사용자에게 직접 확인(AskUserQuestion) — 후보 ①세로 스택(모든 스냅샷을 한 페이지에 순서대로) ②탭 전환 ③그리드(2열) 중 사용자가 전부 거부하고 **"오늘의 공개레포"처럼 카드 목록 + 클릭 시 개별 공유그래프 화면 진입** 방식을 지정. 이는 세 후보 어디에도 없던 네 번째 방향이라 별도로 설계.

**결정.**
1. **카드 목록은 새 페이지가 아니라 기존 게시글 상세 패널 안에 배치** — `CommunityPage.tsx`의 `selectedPost.graphId && <button>그래프 보기→</button>` 자리를 `postSnapshots.length > 0` 분기로 교체(레거시 단일 첨부는 그대로 폴백 유지). 별도 "스냅샷 목록" 페이지를 만들지 않은 이유: 게시글 상세는 이미 존재하는 화면이고, 카드 자체는 그래프 미리보기 없이 텍스트 라벨(스냅샷 번호+`계층/도메인-이름/주석`)만으로 충분히 가볍기 때문(오늘의 공개레포는 OG 이미지가 있어 카드가 무거워도 됐지만, 지금은 프로젝트당 카드 1개가 압도적으로 흔한 케이스라 과설계 방지, §2).
2. **새 라우트 `/community/posts/:postId/graph/:position` 신설, 기존 `/graph`(파라미터 없음)는 레거시 전용으로 그대로 둠** — `CommunityPostGraphPage.tsx` 내부에서 `position` URL 파라미터 유무로 완전히 분리된 두 내부 컴포넌트(`CommunityPostGraphInner`=레거시, `CommunityPostSnapshotInner`=신규)를 분기 렌더링. 레거시 컴포넌트는 한 줄도 안 건드림(§3 외과적 변경) — 기존 단일 그래프 첨부 게시글의 동작·URL이 그대로 보존됨.
3. **신규 뷰어는 ShareGraphPage.tsx를 뼈대로 재구성** — 채팅(`useGraphChat`)·경고패널·배경이미지·대형레포 절단안내는 이번 스코프에서 제외(PROGRESS.md 계획상 3·4단계로 분리), 나머지(노드검색·레이아웃/라벨 토글·도메인/레이어 범례+opaque 토글+탭필터·사이드바 리사이즈/접기·노드 클릭 상세정보)는 ShareGraphPage와 최대한 동일 코드 재사용(`buildLayout`/`applyEdgeVisibility`/`searchNodes`/`GRAPH_MIN_ZOOM`/`GRAPH_MAX_ZOOM`/`GraphLegend`/`LayoutPresetToggle`/`LabelModeToggle`/`useSidebarResize` 전부 기존 공유 유틸·컴포넌트를 그대로 import, 새로 안 만듦 — §1 재사용성 먼저 확인).
4. **엣지 타입 토글 UI 신규 추가** — 조사 결과 기존 ShareGraphPage는 `edgeVisibility` state는 있지만 이를 사용자가 바꿀 수 있는 버튼이 아예 없었음(프리셋에서 초기값만 읽고 끝). PROGRESS.md 계획상 "엣지타입 토글"이 뷰어가 조작 가능해야 할 항목으로 명시돼 있어 새로 추가. GraphPage의 엣지 섹션(`의존성/콜체인/생성/끊긴연결/DB연결/API호출` 6종, 동일 라벨)과 시각적 컨벤션은 맞추되, GraphPage 자체의 JSX/훅은 건드리지 않고(가장 복잡하고 테스트가 두꺼운 페이지라 blast radius 최소화) 새 컴포넌트 안에 독립적으로 작게 재구현. 레이아웃/라벨 토글과 달리 엣지 가시성 변경은 그래프 좌표 재계산이 필요 없어(`buildLayout` 재호출 없이) `builtEdgesCache`(레이아웃 직후·가시성 필터 적용 전 엣지)를 별도 캐시해 `applyEdgeVisibility`만 재적용 — 토글마다 전체 레이아웃을 다시 그리지 않도록 최적화.
5. **카드 라벨은 config에서 파생, 백엔드 DTO 확장 없음** — `config.layoutPreset`/`labelMode`로 "도메인-이름" 같은 짧은 라벨만 생성(`snapshotLabel` 순수함수). 프로젝트명·리포 URL 등 풍부한 카드 정보는 스코프 아웃(PR-D가 "오늘의 공개레포"를 게시글 스냅샷으로 흡수할 때 재검토, 지금은 프로젝트당 스냅샷 1개가 실제 케이스라 과설계 방지).
6. **피드 목록의 "📊 그래프" 배지는 이번에 안 건드림** — `post.graphId`(레거시)만 체크하는 기존 로직 그대로 유지. 신규 스냅샷 게시글은 이 배지가 안 뜨는 상태로 남지만, 피드 배지를 스냅샷 유무까지 반영하려면 `PostResponse` DTO에 카운트 필드 추가+피드 N+1 우려가 있어 별도 판단 필요(문서만 남기고 미착수).

**★런타임 검증 중 발견한 실제 버그(백엔드, 이번 세션과 무관한 기존 결함) — 게시글 단건 조회 permitAll 누락.** 프론트 rewrite를 claude-in-chrome(비로그인 상태)으로 검증하다 `handleSelectPost`의 상세 fetch(`GET /api/community/posts/{id}`, 댓글·첨부·이번 스냅샷 fetch가 전부 이 뒤에 연쇄)가 실행되지 않는 것을 발견 — 원인은 `SecurityConfig` permitAll 목록에 게시글 **목록**(`/api/community/posts`)만 있고 **단건 상세**는 없어 비로그인 요청이 GitHub OAuth로 302 리다이렉트되던 것(axios 401 인터셉터가 리프레시 시도 후 조용히 리다이렉트해 콘솔에 에러가 안 남아 발견이 늦어짐 — `performance.getEntriesByType('resource')`로 실제 네트워크 요청 목록을 직접 대조해서야 확인). `.requestMatchers(HttpMethod.GET, "/api/community/posts/*").permitAll()` 추가로 수정(PUT/DELETE는 그대로 인증 필요). 상세 `ERROR_TRACKER.md` [SEC-2], 백엔드 변경 근거는 `decisions/DECISIONS_BACKEND.md` 참조.

**결과.** `tsc -b` 통과. claude-in-chrome 비로그인 세션으로 E2E 검증 — 실 DB에 스냅샷 행 삽입(gin-gonic/gin, 도메인 레이아웃) 후 ①게시글 상세에서 "📊 스냅샷 1 도메인-이름" 카드 렌더 확인 ②클릭 시 `/community/posts/{id}/graph/0`으로 정상 이동 ③새 뷰어에서 노드 1385개 렌더·계층↔도메인 레이아웃 전환 시 범례 등장·엣지타입 토글(의존성) 클릭 시 버튼 활성 스타일 전환·파일 노드 클릭 시 우측 상세 패널 표시 전부 확인 ④레거시 게시글("테스트 게시글", 그래프 미첨부)은 카드·레거시 링크 둘 다 안 뜸(회귀 없음) 확인. 검증 후 테스트 스냅샷 행 삭제. 백엔드 전체 테스트 재실행 green.

## 게시글 기반 공유그래프 재설계 — PR-C 3단계: 경고 코너패널·MD내보내기·노드 코멘트 읽기전용 포팅 (2026-07-04)

**문제.** PROGRESS.md 계획의 마지막 조각 — `CommunityPostSnapshotInner`에 경고 확인·MD 내보내기·노드 코멘트(읽기 전용)를 ShareGraphPage와 동등하게 포팅.

**결정.**
1. **경고 코너패널 — ShareGraphPage 패턴 그대로 이식** — `CornerPanel`+`WarningPanel`(둘 다 기존 공유 컴포넌트)을 그대로 import. `WarningPanel`은 `ignoreOps`/`onSuppress` prop을 안 넘기면 자동으로 읽기 전용(무시·숨기기 버튼 미표시)이라 컴포넌트 자체 수정 없이 그대로 재사용 가능했음(props 설계가 이미 읽기전용을 지원 — 별도 "읽기전용 모드" 분기를 새로 만들 필요 없었음).
2. **MD 내보내기 — 기존 유틸 함수 그대로 호출** — `downloadWarningsMd`(graphLayout.ts, 이미 ShareGraphPage가 쓰던 함수) 재사용, 신규 로직 없음.
3. **노드 코멘트 — 목록만, 작성/삭제 UI 없음** — `selectedNode` 변경 시 `GET /api/graphs/{graphId}/nodes/{nodeId}/comments`를 호출해 텍스트 목록만 렌더(입력창·삭제 버튼 없음 — 사용자 확정: "생성·수정·삭제는 권한 확인, 읽기는 공개여부만 확인"이 이미 백엔드 `verifyGraphReadAccess`로 구현됐고, 프론트는 그에 맞춰 읽기 동작만 노출). 노드 선택 해제 시 목록 초기화.
4. **경고 데이터는 스냅샷 응답에 이미 포함** — 백엔드 `/snapshots` 응답에 `warnings` 필드가 추가돼(decisions/DECISIONS_BACKEND.md "PR-C 3단계" 참조) 프론트는 별도 API 호출 없이 스냅샷 로드 시점에 함께 세팅.

**결과.** `tsc -b` 통과. claude-in-chrome 비로그인 세션 E2E 검증 — ①gin-gonic/gin(경고 0건) 스냅샷으로 대조 후 codeprint 자기분석 그래프(HIGH_FAN_OUT 베이스라인 10건)로 교체해 경고 코너패널에 "과도한 의존" 그룹+MD 내보내기 버튼 노출 확인, 버튼 클릭 시 예외 없이 다운로드 트리거 확인 ②실 노드 코멘트 1건 DB 직접 삽입 후 해당 노드 클릭 시 비로그인 세션에서 코멘트 내용 정상 표시, 코멘트 없는 노드는 "코멘트가 없습니다" 확인 ③`curl`(쿠키 없음)로 `GET /api/graphs/{graphId}/nodes/{nodeId}/comments` 200 확인. 검증 후 테스트 데이터(스냅샷 행·코멘트) 정리. 백엔드 신규 단위 테스트(`GraphFacadeTest` 6종, `GraphReadAdapterTest` 2종) 포함 전체 테스트 재실행 green.

## 공유 그래프 실시간 채팅 완전 제거 (2026-07-04)

**문제.** PROGRESS.md 계획의 남은 마지막 조각 — `ShareGraphPage`의 실시간 채팅(STOMP `useGraphChat`)을 제거. 게시글에 이미 댓글 기능이 있어 중복이었고, `CommunityPostGraphPage`(신규 스냅샷 뷰어)는 애초에 채팅을 이식하지 않았으므로 이번 제거는 `ShareGraphPage` 단독 정리.

**결정.**
1. **`useGraphChat.ts` 훅 파일 삭제** — grep으로 사용처가 `ShareGraphPage.tsx` 하나뿐임을 재확인 후 파일 자체를 삭제(다른 곳에서 재사용 가능성 없음).
2. **채팅 관련 state·이펙트·JSX 블록 전부 제거** — `chatInput`/`showChat`/`messagesEndRef`, 스크롤 이펙트, `handleSendChat`, 우측 사이드바 "채팅" 섹션(펼치기 버튼·메시지 목록·입력폼). 노드 상세 패널의 `flex: selectedNode ? '0 0 auto' : '1'`(채팅 섹션과 공간을 나눠 쓰던 조건부 크기)을 `flex-1` 고정으로 변경 — 채팅이 없어졌으니 노드 상세가 항상 남은 공간 전체를 차지(신규 스냅샷 뷰어의 동일 패널과 동일 패턴으로 통일).
3. **`graphId` state 함께 제거(orphan)** — `useGraphChat(graphId, null)` 호출이 유일한 소비처였는데 제거되면서 `graphId` state 자체가 완전히 안 읽히는 죽은 코드가 됨 — §3 "내 변경이 만든 미사용 코드는 제거" 원칙에 따라 `setGraphId` 호출까지 함께 삭제.
4. **백엔드 `/graph/{graphId}/chat` STOMP 핸들러 제거** — `CollaborationWebSocketController.handleChat()` 삭제. 같은 클래스의 `/team/{roomId}/chat`(팀 채팅, 별개 기능)·`/collab/{sessionId}/*`(커서·선택·presence)는 무관해 그대로 유지.

**결과.** `tsc -b` 통과, 백엔드 컴파일+전체 테스트 통과(회귀 없음). ★런타임 검증 중 발견: claude-in-chrome으로 신규 스냅샷 뷰어(`CommunityPostSnapshotInner`)를 테스트하다 콘솔에 "Rules of Hooks" 경고 4건이 누적돼 있는 걸 발견 — 원인 추적 결과 이번 세션 중 해당 컴포넌트를 여러 차례 Edit하는 동안 Vite HMR이 라이브 인스턴스를 핫스왑하면서 발생한 개발 중 일시적 아티팩트(훅 개수가 달라진 신구 버전이 같은 렌더 트리에서 충돌)로 확인 — 완전히 새로 고침한 페이지에서는 재현되지 않음(코드 자체를 정적으로 재검토해도 모든 훅이 조건부 return 이전에 무조건 호출되는 순서로 배치돼 있어 실제 위반 없음). 실제 버그 아님으로 판단, 수정 불필요. claude-in-chrome으로 `/share/{projectId}` 재확인 — "채팅" 텍스트 완전히 사라짐, 노드 검색·범례·경고패널 등 기존 기능은 정상 렌더(노드 1398개), 콘솔 신규 에러 없음.

## 피드 갤러리 필터·그래프 배지가 신규 스냅샷 게시글을 누락하던 결함 수정 (2026-07-05)

**문제.** 배지 확장 여부 판단 결과, 백엔드 조사에서 배지보다 범위가 큰 실제 버그로 확인됨 — 상세 원인은 `decisions/DECISIONS_BACKEND.md` 참조. 프론트는 백엔드가 새로 내려주는 `hasGraph` 필드로 교체만 하면 되는 단순 반영.

**결정.** `CommunityPage.tsx`·`UserProfilePage.tsx`의 `Post`/`PostSummary` 인터페이스에 `hasGraph: boolean` 추가, 피드 카드·프로필 카드의 "📊 그래프" 배지 조건을 `post.graphId &&` → `post.hasGraph &&`로 교체. `CommunityPage.tsx`의 게시글 상세 패널 안 레거시 "그래프 보기 →" 버튼(`selectedPost.graphId &&`)은 건드리지 않음 — 이건 스냅샷이 하나도 없을 때만 보이는 레거시 단일 첨부 전용 폴백이라 `graphId` 자체를 확인하는 게 맞음(§3, 의도 다른 조건을 억지로 통일 안 함).

**결과.** `tsc -b` 통과. claude-in-chrome 비로그인 세션 E2E — 스냅샷만 있고 `graphId=null`인 게시글에 대해 ①커뮤니티 피드 카드에 "📊 그래프" 배지 노출 ②"갤러리" 탭 클릭 시 목록에 포함 ③유저 프로필 페이지 글목록에도 배지 노출, 전부 확인. 검증 후 테스트 데이터 정리.

## 흐름 재생 정확성 — 도메인 뱃지 하드코딩·PascalCase=생성자 오판 수정 (2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §2 G2·G3 — `FlowPlaybackPanel.tsx`의 도메인 뱃지가 `/\/(project|user|graph|analysis|community|auth|payment|admin)[/.]?/i` 정규식으로 **Codeprint 자기 자신의 도메인명**을 하드코딩하고 있어 남의 레포(gin 등)에서는 항상 뱃지가 안 뜨거나 우연히 오매칭될 수 있었음(G2). 또 마지막 스텝에서 `/^[A-Z]/.test(name)`(PascalCase)이면 무조건 "객체가 반환됩니다"로 표시하는데, Go·C#은 공개(exported) 함수 전체가 관례상 PascalCase라 일반 함수도 전부 생성자로 오판(G3).

**1차 시도 — 잘못 판단, 런타임 검증에서 반증됨.** 처음엔 `graphLayout.ts`의 `extractDomain`(도메인형 레이아웃에서 노드 색상 분류에 쓰는 함수)이 "그래프가 이미 갖고 있는 도메인 분류(범례와 동일 소스)"에 해당한다고 판단해 이를 재사용하도록 고쳤음. gin 실그래프로 검증한 결과 **항상 뱃지가 안 뜨는 회귀**를 발견 — 원인 조사 결과 `extractDomain`은 DDD 레이어 키워드(`domain`/`application`/`infrastructure`/`interfaces`/`pages`/`components`/`hooks` 등) 하위 서브폴더를 찾는 함수라 gin처럼 그런 레이어 폴더가 없는 레포에서는 전부 `'common'`을 반환함. 실제로 화면에 보이는 범례 탭("Root/Binding/Codec/Internal/Render/Testdata")은 `extractDomain`이 아니라 **섹션/그룹 박스를 만드는 `getGroupKey`**(레이어 키워드가 없으면 실제 최상위 폴더명을 그대로 쓰고, 그마저 없으면 `'root'`)가 만든 값이었음 — 두 함수 중 진짜 "범례와 동일 소스"는 `getGroupKey` 쪽.

**최종 결정.** `getGroupKey` + `findCommonPrefix`(둘 다 `graphLayout.ts` 기존 export, 신규 함수 추가 없음)로 교체. 루트 노드의 `filePath`로 그룹 키를 구해 `'root'`(레포 최상위 파일)면 뱃지를 숨기고, 그 외에는 첫 글자만 대문자화해 표시(범례 라벨 표기와 통일). G3는 언어 인지로 완화 — `rawNode.language`가 `'Go'`/`'C#'`이면 PascalCase여도 생성자 메시지를 표시하지 않음(백엔드 `LanguageDetector`가 내려주는 언어 표시명 그대로 사용, 별도 매핑 불필요).

**결과.** `tsc -b` 통과. claude-in-chrome으로 실그래프 3종 검증 — ①gin(Go): `binding/binding.go`의 `Default` 함수 흐름 재생 시 뱃지 "Binding" 정상 표시(수정 전 1차 시도에서는 뱃지 없음을 직접 확인해 반증), 재생 마지막 스텝에서 "객체가 반환됩니다" 오표시 없음 확인 ②gin 루트 파일(`gin.go`)의 `Default` 함수는 뱃지 없음(의도된 동작 — `getGroupKey`가 `'root'` 반환) ③Newtonsoft.Json(C#): `Parse` 흐름 재생 9단계 전부 진행, 마지막 단계에서도 생성자 오표시 없음, 콘솔 에러 없음. 도메인 뱃지 하드코딩·PascalCase 오판 둘 다 실제 데이터로 반증 가능한 형태였고, 1차 시도(`extractDomain`)도 코드만 보고는 그럴듯했으나 실행 후에야 오류가 드러난 사례 — §11 "읽어라, 추측 말고" 원칙 재확인.

## 첫 사용자 튜토리얼 — OnboardingTour 범용화 + ShareGraphPage 투어 신설 (2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §3·§4 — `OnboardingTour`가 `STEPS`/`STORAGE_KEY`를 컴포넌트 안에 하드코딩하고 있어 GraphPage 전용이었음. 신규 유입 경로인 ShareGraphPage(공유 그래프)에는 투어가 전혀 없었음.

**결정.** `OnboardingTour.tsx`를 `steps`/`storageKey` props를 받는 범용 컴포넌트로 변경(`isTourDone`도 storageKey 파라미터 추가). GraphPage는 기존 5스텝·기존 storageKey(`onboarding_tour_done`)를 그대로 named export(`GRAPH_TOUR_STEPS`/`GRAPH_TOUR_STORAGE_KEY`)로 옮겨 값 변경 없이 props로 전달(순수 추출, 기존 사용자의 "이미 봄" localStorage 상태 보존). ShareGraphPage에는 신규 4스텝 투어(별도 storageKey `onboarding_tour_share_done`) 추가 — ①인트로 ②함수 클릭→흐름재생 안내 ③구조 경고 버튼(CornerPanel) ④"로그인하기" CTA(내 레포 분석 유도). `CornerPanel.tsx`에 선택적 `triggerId` prop을 추가해 접힘 트리거 버튼을 투어 타겟으로 지정할 수 있게 함(GraphPage 쪽은 미전달이라 영향 없음).

**★런타임 검증 중 발견한 버그 — 대형 그래프에서 캔버스 노드를 타겟으로 하면 스텝이 조용히 건너뛰어짐.** 처음엔 GraphPage의 4번째 스텝과 동일하게 2번째 스텝의 target을 `.react-flow__node`(범용 클래스 셀렉터)로 만들었음. gin(노드 1398개) 실그래프로 검증한 결과 이 스텝이 앞뒤 어느 방향으로 이동해도 항상 건너뛰어짐(다음 클릭 시 바로 3번째 스텝으로, 이전 클릭 시 바로 1번째 스텝으로) — DOM에 매칭 요소가 1398개나 있어도, react-joyride가 첫 매칭 요소의 위치를 계산하지 못하면(대형 그래프가 fitView로 축소된 뒤 첫 DOM 순서 노드가 뷰포트 밖/극소 크기일 가능성) 자동으로 스텝을 스킵하는 것으로 추정. **해결:** 2번째 스텝의 target을 특정 캔버스 노드에 의존하지 않는 `'body'`/`placement: 'center'`(1번째 스텝과 동일 패턴)로 변경 — 안내 문구 자체가 "함수 노드를 클릭하면 ~"이므로 특정 노드를 직접 가리킬 필요가 없었음. GraphPage의 기존 4번째 스텝(`.react-flow__node`)은 이번 세션에서 건드리지 않아 동일 리스크가 남아있을 수 있음(GraphPage는 통상 노드 수가 더 적고, 이번 세션에서 발견된 것과 정확히 같은 조건인지는 별도 확인 필요 — 후속 판단 대상으로 기록만).

**결과.** `tsc -b` 통과. claude-in-chrome으로 gin(Go, 노드 1398개) 실그래프에서 신규 4스텝 투어 전부 순서대로 진행 확인(각 스텝 타이틀 텍스트 렌더 확인) + "완료" 클릭 시 `localStorage['onboarding_tour_share_done']` = '1' 저장 확인(재방문 시 재생 안 됨). 콘솔 에러 없음. GraphPage 쪽 변경은 순수 값 추출(동일 STEPS 배열·동일 storageKey 문자열)이라 OAuth 로그인 필요한 실사용 확인 대신 코드 대조로 동등성 검증(값 변경 없음).

## 아키텍처 의도 패널 완성 — A1 인트로·A2 감지된 구조·A3 실시간 매치 미리보기 (2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §4 A1~A3 — 패널을 열면 설명 없이 "모듈/글로브/FORBID"만 있어 뭘 해주는 기능인지 알 수 없었고(A1), 그래프가 이미 감지한 도메인/레이어 구조를 두고도 사용자가 글로브를 빈손으로 타이핑해야 했으며(A2), 오타 글로브가 조용히 아무것도 안 잡는데도 미리보기가 없었음(A3).

**결정.** `ArchitectureIntentPanel`에 `filePaths: string[]` prop 추가(GraphPage가 `rawNodes`의 FILE 경로를 전달) — 3가지 모두 이 하나의 배선으로 해결되므로 별도 PR로 안 쪼갬. A1: 상단 인트로 문구 + "예시로 채워보기(domain ↛ infrastructure)" 프리셋 버튼(모듈·규칙이 비어있을 때만 노출). A2: "감지된 구조에서 가져오기" 버튼 — `getGroupKey`+`findCommonPrefix`(둘 다 `graphLayout.ts` 기존 export, 흐름재생 도메인 뱃지 수정 때 확립한 것과 동일한 "범례와 같은 소스" 원칙 재사용)로 실제 폴더 구조를 감지해 파일 수 많은 순 상위 8개를 모듈 후보로 제시, 이미 있는 이름은 건너뜀. A3: 모듈 글로브 입력창 아래 매치 파일 수를 실시간 표시 — 신규 글로브 매칭 로직을 만들지 않고 기존 `ignoreRules.ts`의 `globMatch`(경고 예외 규칙 UI가 이미 쓰던 것)를 그대로 재사용, 서버 왕복 없음. A5(예외 규칙은 경고 패널에서 관리)도 한 줄 안내 문구로 함께 보완.

**결과.** `tsc -b` 통과. **실 로그인 세션(claude-in-chrome, 사용자 Chrome 프로필의 기존 GitHub OAuth 쿠키 재사용)으로 codeprint 자기분석 프로젝트에서 라이브 검증** — ①빈 상태에서 "예시로 채워보기" 클릭 시 domain(120개 파일 매치)·infrastructure(130개 파일 매치) 모듈 + FORBID 규칙(domain→infrastructure) 자동 생성 확인 ②"감지된 구조에서 가져오기" 클릭 시 실제 codeprint 폴더 구조 기반 8개 모듈(infrastructure/persistence 64개, interfaces/api 43개, domain/graph 28개 등) 자동 채움 + 각 매치 수 정확히 표시 확인 ③글로브를 존재하지 않는 값으로 바꾸면 "매치되는 파일 없음 — 글로브를 확인하세요" 빨간 경고로 즉시 전환 확인 ④저장 시 기존 동작(상태 메시지+`onSaved`로 그래프 즉시 재조회, INTENT_DRIFT 하이라이트 반영) 회귀 없음 확인. 테스트 데이터는 검증 후 전부 제거해 원상 복구.

## 아키텍처 의도 저장 메시지에 실제 위반 수 표시 (A4, 2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §4 A4 — 저장 버튼을 눌러도 "다음 그래프 조회 시 INTENT_DRIFT 경고가 업데이트됩니다"라는 결과를 알 수 없는 막연한 메시지뿐이었음.

**결정.** 백엔드가 새로 내려주는 `violationCount`(응답 JSON, 상세는 `decisions/DECISIONS_BACKEND.md` "A4" 참조)를 파싱해 "저장됨 — 현재 위반 N건이 경고 패널에 표시됩니다"로 교체. 구버전 백엔드/네트워크 오류로 `violationCount`가 없는 응답이면 "저장됨 — 경고 패널에서 확인하세요"로 폴백(하위 호환).

**결과.** 실 로그인 세션(claude-in-chrome)으로 codeprint 자기분석 프로젝트에서 확인 — DDD 프리셋 저장 시 "현재 위반 0건이 경고 패널에 표시됩니다" 정상 렌더, 우측 경고 패널에 INTENT_DRIFT 항목이 실제로 없는 것과 일치함을 대조 확인. 테스트 데이터는 검증 후 빈 상태로 재저장해 원상 복구.

## 레포 소유 뱃지 프론트 반영 (6-b, 2026-07-05)

**문제.** 백엔드가 새로 내려주는 `ownRepo`(상세는 `decisions/DECISIONS_BACKEND.md` "레포 소유 뱃지" 참조)를 화면에 반영.

**결정.** GraphPage·ShareGraphPage 헤더에 "내 레포"/"외부 레포 분석" 뱃지 추가(`ownRepo`가 `null`이면 렌더 안 함 — 구버전 응답 하위 호환). CommunityPage 피드 카드·UserProfilePage 글 목록은 `hasGraph`(PR #442) 때 확립한 패턴 그대로 `Post`/`PostSummary` 인터페이스에 `ownRepo: boolean` 추가 후 배지 렌더. 두 곳 다 `post.repoUrl`/`post.hasGraph`가 있을 때만 배지를 보여줘 텍스트만 있는 글에는 무의미한 배지가 뜨지 않게 함.

**결과.** `tsc -b` 통과. claude-in-chrome 실 로그인 세션(codeprint 자기분석 프로젝트)으로 GraphPage "내 레포" 뱃지, 공개 gin ShareGraphPage "외부 레포 분석" 뱃지 렌더 확인(둘 다 실제 소유 관계와 일치). 커뮤니티 피드는 `/api/community/posts` 응답에 `ownRepo` 필드가 정확히 포함되는 것 확인(기존 테스트 게시글은 `repoUrl`이 없어 항상 false — 배지 자체가 안 뜨는 것도 의도대로 확인). 스냅샷 뷰어(`CommunityPostGraphPage`)는 이번 스코프 제외(백엔드 결정 참조) — 후속 작업.

## v1.0 UX 갭 리뷰 7번 — 소소한 다듬기 7건 (#4·#11·#12·#13·#14·#15·#16, 2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §1 표에 남아있던 마지막 항목 7건. 항목당 규모는 작지만 #12는 조사 중 실제 버그로 확인됨.

**결정 및 발견.**
1. **#4 빈 URL 검증** — `LandingPage.tsx` `handleTryUrl`에 `urlError` state 추가, 빈 값 제출 시 입력창 빨간 테두리 + "레포 URL을 입력해주세요." 안내로 교체(기존은 조용한 early return).
2. **#14 전문용어 순화** — 기능 카드 "노드-엣지 그래프"→"한눈에 보이는 다이어그램", "전체 흐름 추적 (DFS)"→"전체 흐름 추적"(제목에서 DFS 제거), "upstream·downstream 경로"→"어디서 시작해 어디로 이어지는지".
3. **#15 메뉴명** — `AppHeader.tsx`의 서비스 드롭다운 "도그푸딩"→"자체 사용 사례"(페이지 자체 제목·URL은 유지, 진입점 라벨만 변경).
4. **#11 React Flow 컨트롤 영어 노출** — `@xyflow/react` v12의 `ariaLabelConfig` prop(공식 API, 커스텀 버튼 재구현 불필요)으로 `controls.zoomIn/zoomOut/fitView/interactive.ariaLabel` 4개를 한국어로 오버라이드. `graphLayout.ts`에 `GRAPH_ARIA_LABELS` 상수로 공유해 그래프 5개 `<ReactFlow>` 인스턴스(GraphPage·ShareGraphPage·CommunityPostGraphPage 2곳·DiffPage) 전부에 배선.
5. **#12 노드 정보 패널 "타입 -" — 진짜 버그였음.** `ShareGraphPage`/`CommunityPostGraphPage`가 `selectedNode.type ?? ''`로 조회했는데, `graphLayout.ts`의 `buildLayout`이 파일 노드에만 React Flow `type: 'fileNode'`를 명시하고 **함수·DB_TABLE 노드는 `type` 자체를 아예 설정하지 않음**(React Flow 기본값으로 `undefined`) — 그래서 함수를 클릭하면 항상 매핑 실패로 "-"가 뜬 것(DB_TABLE도 같은 결함이었으나 목록에 자주 없어 미보고). 해결은 React Flow `type`을 억지로 채우는 대신(존재하지 않는 nodeTypes 키를 넣으면 콘솔 경고 위험) `rawNodesCache`(백엔드 원본 `NodeType`: FILE/FUNCTION/DB_TABLE/API_ENDPOINT 보유)에서 `selectedNode.id`로 실제 타입을 역조회하도록 변경.
6. **#13 AI 키 사전 안내** — `AiAnalysisSection.tsx`에 `GET /api/ai/keys`(기존 SettingsPage가 쓰던 엔드포인트 재사용) 마운트 시 조회 추가, 등록된 키가 하나도 없으면 분석 버튼 비활성화 + "/settings로 키 등록하기" 링크 안내. 401(비로그인)이면 `hasApiKey=null`로 폴백(기존 동작과 동일, 버튼 활성 상태 유지 — 회귀 없음).
7. **#16 사이드바 1,400개 평면 나열** — 검색어가 없을 때는 파일 단위로 접어서 표시(기본 전부 접힘, `expandedFiles` Set state), 파일 행에 함수 개수 배지 + 펼치기 토글(▸/▾) 추가. 함수 노드의 `parentId`가 항상 소속 파일의 id로 설정돼 있는 걸 이용해 그룹핑(신규 백엔드/레이아웃 변경 없음). 검색 중일 때는 기존 평면 검색 결과 그대로 유지(회귀 없음).

**결과.** `tsc -b` 통과. Claude Preview(비로그인)로 확인 — ①빈 URL 제출 시 에러 문구·빨간 테두리 렌더 ②"자체 사용 사례" 메뉴 정상 표시 ③gin ShareGraphPage에서 컨트롤 4개 버튼 title이 "확대/축소/화면에 맞추기/편집 잠금 전환"으로 렌더 ④사이드바가 파일 단위로 접힌 상태로 시작, `gin.go` 펼치면 함수 목록 노출, `New` 함수 클릭 시 노드 정보 패널에 "FUNCTION" 정상 표시(수정 전 "-" 재현 확인 후 대조) ⑤랜딩 기능 카드 문구 반영 확인. #13(AI 키 안내)은 `AiAnalysisSection`이 GraphPage 전용(로그인 필수)이라 이번 세션에서 실사용 확인 불가 — 로그인 후 키 미등록 상태에서 버튼 비활성화·안내 문구가 뜨는지 사용자 확인 필요(§4 OAuth 예외 규칙).
> ⚠️ **부분 대체 (2026-07-12)** — "AI 키 기반 기능 전면 제거"(`decisions/DECISIONS_BACKEND.md`)로 아래 "SettingsPage·노드 설명/코드생성 UI는 무관하므로 그대로 유지" 부분이 대체됨. 그 UI들도 결국 제거됨. 아래 원문은 이력 보존용.

## AI 분석 삭제 + 아키텍처 의도 A1 예시 교체 (2026-07-06)

**문제.** 백엔드 AI 그래프 분석 기능 삭제(사유는 `decisions/DECISIONS_BACKEND.md` "AI 그래프 분석 삭제" 참조)에 따라 프론트 UI도 제거. 겸사겸사 아키텍처 의도 패널의 A1 데모 예시("domain ↛ infrastructure")가 DDD 컨벤션 프로젝트에서 이미 자동으로 잡히는 `DOMAIN_IMPORTS_INFRA` 경고와 겹쳐, "이미 자동으로 잡히는 걸 왜 또 선언하지?"라는 혼란을 준다는 게 사용자 검토로 드러남.

**결정.**
1. `AiAnalysisSection.tsx` 삭제, `GraphPage.tsx`에서 사용처·import 제거. `/api/ai/keys`·`/explain`·`/generate-code`를 쓰는 SettingsPage·노드 설명/코드생성 UI는 무관하므로 그대로 유지.
2. `ArchitectureIntentPanel.tsx`의 `applyDddPreset` 예시를 domain↛infrastructure(자동 게이트와 겹침) → `app↛legacy`(마이그레이션 경계, 자동 게이트가 못 잡는 케이스)로 교체. 인트로 문구에 "domain/infrastructure나 Controller/Service/Repository 같은 흔한 구조는 이미 자동으로 검사되니, 여기서는 그 외 규칙을 선언하라"는 안내 한 줄 추가 — 실제 프로젝트별 자동 감지 여부를 조건부로 보여주려면 백엔드 필드 추가가 필요해 이번 스코프에서는 제외(범용 안내 문구로만 처리).

**결과.** `tsc -b` 통과. AI 분석 관련 UI 완전 제거 확인, 아키텍처 의도 패널 인트로·A1 버튼 문구 변경 확인.

## "/월" 표기 정정 — 1회 결제 명시 (V2-2, 2026-07-09)

**문제.** V1_UX_GAP_REVIEW.md V2-2 — 실결제 화면(TeamsPage)과 랜딩 요금제 카드가 "₩9,900/월"·"좌석당 4,900원/월" 표기를 쓰지만 실제 결제는 Toss 1회 결제(정기결제 미구현)라 표기와 실동작이 어긋남. #447에서 개인 결제는 중단했지만 "/월" 표기 자체는 남아있었음.

**결정.** `LandingPage.tsx`(요금제 카드)·`TeamsPage.tsx`(좌석 증가 섹션·새 팀 만들기 모달) 전부 "/월" 제거하고 "1회 결제" 명시로 교체. 실제 결제 트리거가 있는 두 지점(좌석 증가·새 팀 만들기)에는 "월 자동갱신(정기결제)은 아직 지원되지 않습니다. 도입 시 기존 구매는 그대로 유지됩니다." 1줄을 결제 버튼 근처에 추가. 랜딩 카드는 개인 결제 버튼이 `disabled`(결제 트리거 없음)라 안내 문구는 생략 — 표기 정정만 적용.

**★런타임 검증 중 발견한 기존 버그(이번 스코프와 무관, 수정 안 함) — `TeamsPage` 비로그인 접근 시 블랙 화면.** 비로그인 상태로 `/teams` 진입 시 `GET /api/teams/mine`이 401 JSON이 아닌 302(`/oauth2/authorization/github`)를 반환(curl로 확인) → axios가 리다이렉트를 투명하게 따라가 OAuth 페이지 HTML을 `res.data`로 받음 → `setTeams(문자열)` → `.map` 호출 시 `TypeError: teams.map is not a function`으로 전체 렌더 크래시. `ERROR_TRACKER.md` FE-22로 기록, 백로그 등재만 하고 이번 세션은 미수정(스코프 외).

**결과.** `tsc -b` 통과. claude-in-chrome 실 로그인 세션(사용자 GitHub OAuth)으로 확인 — 좌석 증가 섹션 "좌석당 4,900원 · 1회 결제 · 현재 5석" + 안내 문구 정상 렌더, "새 팀 만들기" 모달 "Pro · Desktop · 좌석당 4,900원 · 총 24,500원 (1회 결제)" + 동일 안내 문구 정상 렌더, 콘솔 에러 없음. 랜딩 요금제 카드는 Claude Preview DOM 조회로 "₩9,900"·"1회 결제 · 팀은 좌석당 ₩4,900" 렌더 확인.

## 런칭 갭 A2·A3 — OG 이미지 + 404 페이지 (2026-07-09)

**문제.** PRODUCT_STRATEGY.md §15.3 A2·A3 — ①`index.html`이 `og-image.png`를 참조하나 `public/`에 실파일이 없어 카톡·슬랙·트위터 링크 공유 카드가 전부 깨진 이미지로 노출(링크 공유가 핵심 기능인 서비스에 치명). ②`App.tsx`에 catch-all 라우트가 없어 잘못된/깨진 링크(스냅샷 링크 오타 포함)가 빈 화면으로 떨어짐.

**결정.**
1. **404(A3)** — `NotFoundPage.tsx` 신규(기존 `ContactPage` 스타일 그대로 재사용: `AppHeader`+`Footer`+`bg-gray-950`), `App.tsx`에 `<Route path="*" element={<NotFoundPage />} />`를 라우트 목록 맨 끝에 추가. "홈으로 이동"·"문의하기" 링크 제공. `frontend/vercel.json`이 이미 전체 경로를 `index.html`로 리라이트하므로 새로고침 시에도 클라이언트 라우팅이 정상 처리됨(별도 서버 설정 불필요, 확인만 함).
2. **OG 이미지(A2)** — 랜딩 히어로와 동일한 브랜드 톤(`bg-gray-950` 배경, favicon.svg의 보라·파랑 그라디언트, "GitHub 레포를 인터랙티브 회로도로" 카피)으로 1200×630 카드 제작. **제작 방식 — 브라우저 스크린샷 대신 Node `@napi-rs/canvas`로 직접 픽셀 렌더링**: 처음엔 정적 HTML(`_og-draft.html`)을 만들어 claude-in-chrome의 `zoom(save_to_disk)`으로 캡처하려 했으나, 저장된 파일의 실제 디스크 경로를 Bash/PowerShell 양쪽에서 찾지 못함(브라우저 확장 호스트가 로컬 파일시스템 밖에 저장하는 것으로 추정) — 세션 스크래치 디렉터리에 `@napi-rs/canvas`(Node 네이티브 캔버스, 프로젝트 `package.json`에는 추가하지 않음)를 임시 설치해 시스템 폰트(`malgun.ttf`/`malgunbd.ttf`)로 직접 그려 `frontend/public/og-image.png`에 저장. **탈락**: `pip install pillow`(사내망 SSL 인증서 검증 실패로 설치 자체 불가) — `npm`은 같은 환경에서 정상 동작해 npm 생태계 경로로 전환.

**결과.** `tsc -b` 통과. `file` 명령으로 PNG 실제 픽셀 크기 1200×630(8-bit RGBA) 확인. claude-in-chrome으로 `/some-nonexistent-page` 접속 시 404 페이지 렌더 + "홈으로 이동" 클릭 시 `/`로 정상 이동 확인. `/og-image.png` 직접 접속으로 Vite 정적 서빙 확인(브라우저 탭 제목에 "(1200×630)" 표시로 실제 렌더 크기까지 확인). 배포 후 실제 OG 카드는 카카오톡·슬랙 등 외부 크롤러 캐시 특성상 이번 세션에서 직접 확인 불가 — 배포 후 Facebook Sharing Debugger 등으로 재확인 권장(후속 확인 항목, 코드는 정상).

## 경고 MD 내보내기에 룰별 가이드(WARNING_META.desc) 미포함 (2026-07-11, codeprint_114)

**문제.** PROGRESS.md "경고 설명·수정 가이드 레이어" 백로그 점검 중 발견 — `WarningPanel.tsx`의 `WARNING_META`엔 이미 15종 전체에 "왜+어떻게 고쳐라" `desc`가 있고 패널 UI에도 렌더되는데, `downloadWarningsMd`(`graphLayout.ts`)는 `WARNING_META[type].label`만 쓰고 `desc`는 안 넣고 있었음 — MD로 내보내면 가이드 없이 타입명+메시지 목록만 남는 상태. 백로그 자체가 "MD 산출물에 위 템플릿·근거를 채우면 자동으로 풍부해짐"이라고 이미 정확히 짚어뒀던 갭.

**결정.** `downloadWarningsMd`에서 `meta.desc`가 있으면 그룹 헤더 바로 아래 인용구(`> ...`)로 추가. 기존 `WARNING_META` 단일 소스를 그대로 재사용(신규 데이터 없음), 라벨 조회 로직과 동일 패턴.

**결과.** `tsc -b` 통과. 순수 문자열 조합 함수(그래프 인증 세션 불필요)라 로직 검토로 정확성 확인 — 실 브라우저 클릭 확인은 GitHub OAuth 로그인 필요라 이번엔 생략(위험도 낮음: 이미 검증된 `WARNING_META`·Blob 다운로드 패턴 재사용뿐, 신규 분기 없음).

## 예외 규칙(ignoreRules) 저장 조용한 실패 — raw fetch가 CSRF 헤더 누락 (2026-07-12, codeprint_116)

**문제.** Audit Log 기능(백엔드 감사 로그 신설) 브라우저 E2E 검증 중 발견 — 실제로 예외 규칙을 추가했는데 감사 로그가 텅 비어있었음. 원인 추적 결과 `ignoreRules.ts`의 `loadIgnoreRules`/`saveIgnoreRules`가 전역 axios 인스턴스가 아니라 **raw `fetch()`**를 쓰고 있었음 — `main.tsx`가 `axios.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest'`로 붙이는 CSRF 헤더가 `fetch()` 호출엔 안 붙어 `SecurityConfig`의 CSRF 필터가 PUT을 403으로 차단. 더 나쁜 건 `fetch()`가 HTTP 에러 상태를 예외로 던지지 않아(`res.ok` 미검사) `GraphPage.tsx`의 `try/catch`도 못 잡아 **콘솔 에러조차 없는 완전 침묵 실패** — 화면엔 규칙이 추가된 것처럼(낙관적 업데이트) 보이지만 새로고침하면 사라짐. GET은 CSRF 대상이 아니라 정상 동작해 지금까지 발견이 늦어짐(`ERROR_TRACKER.md` FE-23).

**결정.** `ignoreRules.ts`의 `loadIgnoreRules`/`saveIgnoreRules`(+ 신규 `loadAuditLog`)를 전부 공유 `axios` 인스턴스로 통일 — 다른 모든 API 호출(`GraphPage.tsx` 등)과 동일 패턴. `saveIgnoreRules` 실패 시 axios가 예외를 던지므로 기존 `try/catch`의 `alert('예외 규칙 저장에 실패했습니다.')`가 이제 실제로 작동.

**결과.** `tsc -b` 통과. 실브라우저(claude-in-chrome) E2E — 수정 전 PUT 403(콘솔 무반응) 재현 확인 → 수정 후 같은 조작으로 PUT 200, "예외 규칙" 섹션 반영, `docker exec ... psql`로 `architecture_intent_audit_log` 테이블에 ADD 행 실제 삽입까지 확인. 규칙 제거(REMOVE) 경로도 같은 방식으로 PUT 200 + REMOVE 행 삽입 확인 후 테스트 데이터 정리(DELETE). **교훈: 같은 화면 안에서도 API 호출 방식(axios vs fetch)이 섞여 있으면 전역 인터셉터·기본 헤더가 조용히 빠질 수 있다 — 새 기능 추가 시 인접 코드의 호출 방식도 눈여겨볼 것.**

## PR7·마이페이지 보류 확정 (2026-07-12) — 여러 세션 미결정 항목 종료

**배경.** 두 항목 모두 수 세션에 걸쳐 "옵션은 정리됐지만 최종 판단 없음" 상태로 PROGRESS.md에 남아있었음. 사용자가 세션 방향 판단을 위임("너가 알아 해라")한 시점에 두 항목을 확정 종료.

**PR7(GraphPage↔공유뷰어 노드상세패널 공유) — 보류.** 옵션 3가지(①보류 ②표시전용 부분만 작게 추출 ③전체 통합 재설계) 중 ①선택. 근거: 실제 겹치는 코드가 20~30줄뿐인데 `GraphPage.tsx`(최고위험 파일, 판별 유니온 9종+의 노드 상세 패널)를 건드려야 해서 리스크가 이득보다 큼. ②(부분 추출)는 "20~30줄만 위해 새 컴포넌트 경계를 만드는" 과설계, ③(전체 재설계)은 명확한 사용자 요구 없이 리스크만 최대화 — 둘 다 지금 단계에선 탈락.

**마이페이지 — 현행 2탭(글/프로젝트) 유지, `/settings`·`/teams`는 계속 별도 페이지.** 탭 통합을 요구하는 사용자 불편 신고가 없고, 두 페이지가 독립적으로 이미 잘 작동 중 — 통합 시 얻는 UX 이득이 라우팅·상태관리 리팩토링 비용을 상회한다는 근거가 없어 원래 계획(4탭)에 억지로 맞추지 않기로 함.

**재추진 조건.** PR7은 공유 뷰어 기능이 커져 실제 코드 중복이 유의미해질 때, 마이페이지는 탭 미통합으로 인한 구체적 사용자 불편이 접수될 때 — 조건 충족 전엔 재논의 안 함(현재 PROGRESS.md 백로그에서 제거, 이 기록이 유일한 근거).

## SettingsPage 비로그인 접근 시 가드 없음 (2026-07-12, codeprint_119)

**문제.** S3 IAM 권한 축소 작업 중 사용자가 "로그인 없이 설정 페이지에 들어가지는 게 이상하다"고 직접 발견 — 실제로 `/settings`가 비로그인 상태에서도 프로필/배경 이미지 업로드 UI와 "계정 삭제" 폼을 그대로 렌더링하고 있었음. `SettingsPage.tsx`의 `useEffect`가 `/api/auth/me` 실패를 `.catch(() => {})`로 조용히 삼키기만 하고 별도 가드가 없었던 게 원인.

**심각도 확인.** 실제 데이터 침해는 아님 — 백엔드가 이미 이중으로 막고 있었음. `/api/users/me/avatar`·`/api/users/me/background`는 `SecurityConfig`에서 `.authenticated()`로 명시돼 컨트롤러 도달 전 401. `/api/auth/account`(DELETE)는 `/api/auth/**` 전체가 `permitAll()`이지만 `AuthController.deleteAccount()`에 `if (user == null) return 401` 방어 코드가 있어 실제 삭제는 불가능했음. 즉 순수 프론트 UX 결함(비로그인 사용자에게 의미 없는 "실패" 에러만 보여줌)이었고 보안 취약점은 아니었음.

**결정.** 신규 가드 컴포넌트를 만들지 않고, 같은 문제를 이미 겪은 `TeamsPage.tsx`의 기존 패턴(보호된 데이터 조회 실패 시 `navigate('/', { replace: true })`)을 그대로 재사용. `SettingsPage.tsx`의 `/api/auth/me` `.catch(() => {})`를 `.catch(() => navigate('/', { replace: true }))`로 교체.

**결과.** `tsc -b` 통과. Preview 프론트 서버(백엔드 미기동, 로그인 세션 없음)에서 `/settings` 직접 접속 시 즉시 `/`(랜딩 페이지)로 리다이렉트되는 것을 `get_page_text`로 확인.

## UML 다이어그램 관계 표기 부분 도입 검토 — 보류(2026-07-15, codeprint_129)

**배경.** codeprint_123에서 사용자가 정처기 학습 사이트 UML 클래스 다이어그램 관계 페이지를 공유하며 그래프 시각화 표준화 가능성을 질문 — 탐색적 질문에 대한 즉흥 답변으로 ①`CONTAINS`(파일→함수 소유)를 UML 합성 스타일 ②`isInterfaceImpl`(이미 계산되나 미시각화 플래그)를 UML 실체화 스타일로 부분 도입을 제안했으나 코드 변경 없이 사용자 답변 대기 상태로 여러 세션 이월.

**착수 전 재검토(코드 근거).** ①React Flow(`@xyflow/react`)의 `MarkerType`은 `Arrow`/`ArrowClosed` 두 종류뿐 — UML의 속 빈 삼각형(실체화)·채워진 마름모(합성)는 기본 미제공, 커스텀 SVG 마커 신규 정의가 필요해 원래 제안보다 공수가 큼(`frontend/src/utils/graphLayout.ts` 확인) ②`CONTAINS`는 그래프에서 가장 흔한 엣지 타입이라 전체 그래프의 지배적 시각 패턴이 바뀌는 큰 변경인데, "파일이 함수를 담는다"는 관계는 사용자가 이미 직관적으로 아는 정보라 UML 마름모로 바꿔도 정보 이득이 작음 ③UML 6종 관계 중 2종만 표기를 따르면 UML을 아는 사용자일수록 "왜 나머지는 자기 방식이지?"라는 혼란 요인이 될 수 있음(완전 독자 표기 또는 완전 UML 준수 둘 중 하나가 반쪽보다 나음) ④애초에 실제 사용자 불편·요구에서 나온 게 아니라 탐색적 질문에서 나온 아이디어.

**결정.** 보류. 폐기는 아니나 착수 우선순위 없음 — 재추진 조건은 구체적인 사용자 혼란 신고나 UML 표기 요청이 실제로 접수될 때.

**검토한 대안.** 전면 6종 UML 매핑 — codeprint_123에서 이미 기각(필드 소유권 수명 분석이 전 언어에 없어 정밀도 위험).

## i18n 도입 — react-i18next (2026-07-15, codeprint_129, PR #571)

**배경.** "영어화 해볼까"라는 탐색적 질문에서 시작 — 처음엔 "해외 채널에 유입 실험을 하려면 최소한 첫 접점(랜딩+공개 뷰어)만 영어면 된다"로 스코프를 좁혔으나, "사용자에게 보이는 모든 페이지"·"실제 유저가 유입될 경우 어떻게 사용하는지가 접근성 있어야 한다"는 재확장 요청으로 최종 스코프가 로그인 후 화면까지 전체로 확정됨(사용자 명시적 지시, "그래도 해").

**검토한 대안.** ①`react-i18next` 없이 언어별 텍스트 객체를 컴포넌트 파일에 직접 정의 — 소규모(2페이지)일 땐 충분했으나 51개 페이지 전체로 스코프가 커지며 라이브러리 없이는 언어 전환 로직(감지·저장·polyfill)을 직접 구현해야 해 기각. ②페이지당 개별 네임스페이스 파일(51개) — 관리 단위가 너무 잘게 쪼개져 소규모 페이지들은 `misc.json` 하나로 묶고, 큰 페이지(landing/legal)만 전용 네임스페이스로 분리하는 절충안 채택.

**결정.** `react-i18next` + `i18next-browser-languagedetector` 도입. `src/i18n/index.ts`가 언어 감지(`localStorage` 우선 → `navigator` 언어 fallback) 초기화, `src/i18n/locales/{ko,en}/{common,landing,misc,legal}.json`에 번역 키 저장. `AppHeader`(전 페이지 공용)에 수동 토글 버튼 추가. 날짜 로케일(`ko-KR`/`en-US`) 포맷 중복을 `src/i18n/dateLocale.ts`로 공통화.

**법적 문서 처리.** `TermsPage`/`PrivacyPage`는 번역 오류가 실제 법적 리스크로 이어질 수 있어, 원문 그대로 옮기되 영어판에만 "본 번역과 한국어 원문이 상충할 경우 한국어 원문이 우선한다"는 고지를 추가. 전문 번역 검수는 이번 스코프에 포함하지 않음(후속 과제로 보류).

**스코프 경계.** 코드 주석·커밋 메시지·decisions/CLAUDE.md 등 개발자용 한국어 문서는 대상 아님(개발 프로세스 언어는 그대로 한국어 유지, §5). 백엔드가 생성하는 동적 텍스트(알림 메시지 `n.message` 등)는 이번 스코프 밖 — 프론트 UI 문자열만 대상.

**PR 분할.** 페이지 51개·한글 포함 약 2,600줄 규모를 한 PR로 묶으면 리뷰·회귀 위험이 커져 우선순위별로 분할: 1차(PR #571) 공개 페이지+공용 UI+법적 문서+소규모 페이지 13개, 2차 이후 핵심 기능 화면(MyPage/GraphPage/SettingsPage/TeamsPage/CommunityPage 등)+공용 컴포넌트, ChangelogPage·EvolutionPage(서사형 장문)는 최우선순위 아님으로 후순위.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome) — 언어 토글 클릭 시 랜딩페이지 전체(hero·steps·features·pricing)·헤더·푸터·쿠키배너 즉시 전환 확인, 새로고침 후 선택 유지(`localStorage`) 확인, Terms/Privacy 페이지 영어 렌더링(11개 조항+9개 섹션) 직접 확인. 부수로 `npm audit fix`가 무관한 기존 취약점(axios 하위 `form-data`)도 해결.

## i18n 2차 — 네임스페이스 단일화(workspace) + SettingsPage 계정삭제 확인어 지역화 (2026-07-15, codeprint_130)

**배경.** i18n 2차(핵심 기능 화면 11개: MyPage/GraphPage/SettingsPage/TeamsPage/CommunityPage 등) 착수 전 새 네임스페이스 파일 생성이 CLAUDE.md "새 파일 생성 전 허락" 규칙 대상이라 사용자에게 먼저 확인.

**검토한 대안(네임스페이스 이름).** ①`app`(신규) — 사용자가 "뭔 기능인지 모를 것 같다"며 기각 ②도메인별 세분화(`graph.json`·`team.json`·`community.json`·`admin.json` 등) — 파일 수 증가, 매번 새 파일 생성 승인 필요 ③사용자가 "eng/kor로 나누는 게 맞지 않냐"고 재질문 → 이미 `locales/{en,ko}/`가 언어 최상위 폴더라 네임스페이스 분할과 무관하게 3번째 언어 확장이 항상 쉽다는 점을 설명, 재질문 결과 "기존 원칙에 맞게" 결정 위임받음.

**결정.** `workspace` 네임스페이스(`locales/{ko,en}/workspace.json`) 단일 파일로 11개 페이지 전체 수용 — 1차 `misc.json`이 여러 소규모 페이지를 하나로 묶었던 전례(§2 단순성 우선, 파일 수 최소화)를 따름. 페이지별 진행 순서는 파일 크기 오름차순(SettingsPage 164줄 → ... → GraphPage 3338줄)으로 확정, 위험이 큰 GraphPage를 마지막에 별도 다룸. 페이지 단위 커밋 분리(Context129 지침 반영).

**SettingsPage 부수 발견 — 계정삭제 확인어 하드코딩.** 기존 코드가 "삭제"라는 한국어 리터럴을 입력값 비교(`deleteConfirm !== '삭제'`)에 직접 사용하고 있어, 그대로 두면 영어 UI에서도 사용자가 한국어 "삭제"를 입력해야 하는 불일치가 생김(버튼 라벨만 번역하면 실제 확인 로직과 어긋남). `settings.deleteAccount.confirmWord` 키를 신설해 ko="삭제"/en="DELETE"로 지역화하고, 비교·placeholder·안내 문구(`confirmBefore`+`<strong>`+`confirmAfter`) 전부 이 값을 참조하도록 통일 — TermsPage의 `listBold`(강조 텍스트를 별도 필드로 분리) 전례와 동일한 패턴.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 백엔드+Docker DB 기동 후 실 로그인 세션) — SettingsPage/DonatePage/HowItWorksPage 3개 페이지 한국어·영어 전환 모두 확인. SettingsPage는 영어 모드에서 입력창에 "DELETE" 입력 시 삭제 버튼이 실제로 활성화(`disabled=false`)됨을 JS로 직접 확인(실제 삭제는 실행하지 않음). HowItWorksPage의 경고 타입 라벨(`WARNING_META.label`, 예: "순환 의존")은 아직 한국어로 남음 — `WarningPanel.tsx` 등 공용 컴포넌트 17개는 별도 트랙(PROGRESS.md 백로그)이라 이번 스코프 밖, 알려진 갭으로 기록.

## AppHeader 언어·테마 드롭다운 UI 전환 (2026-07-15, codeprint_130)

**배경.** i18n 2차 작업 도중 사용자가 다른 서비스 스크린샷(🌐 Korean/📍 kms/🌙 Dark 형태의 알약형 버튼 + 클릭 시 드롭다운, 체크마크로 현재 선택 표시)을 제시하며 언어·테마 전환 UI를 이 방식으로 바꿔달라고 요청 — 기존엔 텍스트 토글 버튼 하나로 한/영, 라이트/다크를 즉시 전환하는 방식이었음.

**스코프 확인.** 스크린샷에 중국어·일본어·System(OS 설정 따름) 옵션도 보여 실제 추가 여부를 먼저 확인 — 사용자가 "UI만 드롭다운으로, 한/영만 실제 작동" 및 "드롭다운만 적용"(System 등 새 기능은 제외)으로 명확히 스코프를 좁힘. 중국어·일본어 실제 지원은 `common`·`landing`·`misc`·`legal`·`workspace` 전 네임스페이스 재번역이 필요한 별도 규모의 작업이라 이번 스코프에서 제외.

**결정.** `AppHeader.tsx`의 `toggleLanguage`/`toggleTheme`(즉시 반전 토글)를 제거하고, 기존 검색·알림 드롭다운과 동일한 패턴(`useRef`+바깥 클릭 감지)으로 언어(🌐)·테마(☀️/🌙) 드롭다운 2개를 신설. 각 드롭다운은 한국어/영어, Light/Dark 2개 옵션만 나열하고 현재 선택 항목에 ✓ 표시. 테마는 `setTheme(dark: boolean)` 명시적 setter로 교체(기존 `toggleTheme` 이진 반전 로직 대체).

**부수 발견 — 라이트 테마 드롭다운 패널 가독성 결함.** 실측 중 라이트 모드에서 새 드롭다운 패널이 어두운 배경(`bg-gray-900`)에 어두운 텍스트로 렌더링돼 거의 안 보이는 문제 발견. 원인은 `index.css`의 `html[data-theme="light"] header button` 규칙이 버튼 텍스트 색만 오버라이드하고 패널 `<div>` 배경은 손대지 않았던 기존 공백 — 이 공백은 신규 드롭다운뿐 아니라 기존 검색·알림·서비스 드롭다운에도 동일하게 존재했을 잠재 결함(우연히 지금까지 발견 안 됨). `header div[class*="bg-gray-900"]` 속성 선택자로 헤더 내 모든 드롭다운 패널에 공통 적용되는 라이트 테마 배경 오버라이드를 추가해 신규·기존 드롭다운 전부 동시 수정(범위를 넓혀 찾아간 게 아니라, 신규 기능에 필요한 CSS 규칙이 기존 컴포넌트와 셀렉터를 공유해 자연스럽게 같이 고쳐짐).

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome) — 언어 드롭다운 열기/선택/닫힘, 테마 드롭다운 열기/선택/닫힘(다크→라이트→다크) 확인. 라이트 모드에서 드롭다운 패널 배경 수정 전/후 스크린샷으로 가독성 결함 재현 및 수정 확인.

## UserProfilePage i18n 이관 — misc 네임스페이스 교차 참조 (2026-07-15, codeprint_130)

**배경.** i18n 2차 두 번째 페이지. `FEEDBACK_LABELS`(ARCHITECTURE_REVIEW/GENERAL/DEBUG) 상수가 `BookmarksPage.tsx`(1차, `misc.bookmarks.feedbackLabels`)에 이미 동일한 값으로 번역돼 있음을 발견.

**결정.** `workspace` 네임스페이스에 중복 정의하지 않고 `useTranslation('misc')`를 별도로 호출해 `tMisc('bookmarks.feedbackLabels', {returnObjects:true})`로 교차 참조 — i18next는 네임스페이스별 `useTranslation` 훅을 컴포넌트당 여러 개 호출 가능. §1 "재사용성 먼저 확인" 원칙 적용(코드 작성 전 이미 검증된 번역이 있는지 확인 후 재사용).

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 실 로그인 세션) — `/users/{실제 UUID}` 한국어("일반"·"2026년 가입"·"공개 프로젝트 (1)")·영어("General"·"Joined 2026"·"Public Projects (1)") 전환 모두 확인, `misc` 네임스페이스 재사용 라벨도 정상 표시.

## MyPage i18n 이관 (2026-07-15, codeprint_130)

**범위.** `MyPage.tsx` 자체 문자열만 대상 — 내부에서 렌더링하는 `ProjectCard`·`CreateProjectModal`은 별도 공용 컴포넌트 트랙(PROGRESS.md 백로그 "공용 컴포넌트 17개")이라 이번엔 한국어로 남김. `FEEDBACK_LABELS`는 UserProfilePage와 동일하게 `misc.bookmarks.feedbackLabels` 교차 참조로 중복 제거.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 실 로그인 세션) — `/mypage` 프로젝트 탭(한국어 "프로젝트/글/팀 관리/설정/+ 새 프로젝트" ↔ 영어 "Projects/Posts/Team Management/Settings/+ New Project")과 글 탭(피드백 라벨·그래프 배지·날짜 포맷 en-US "7/4/2026") 양쪽 확인. `ProjectCard` 내부(예: "비공개"·"PR 리뷰")는 의도대로 한국어 유지 확인.

## TeamsPage i18n 이관 (2026-07-15, codeprint_130)

**범위.** 팀 대시보드 전체(석수 현황·멤버 관리·API 키·좌석 증가 결제·팀 생성/삭제 모달) — 592줄 중 가장 문자열 밀도가 높은 페이지. 금액 포맷은 DonatePage와 동일한 `currencySuffix`("원"/" KRW") + `toLocaleString('en-US')` 패턴 재사용, 날짜는 `currentDateLocale()` 재사용.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 실 로그인 세션) — 팀 상세 페이지 전체(석수 현황·멤버·API 키·좌석 증가·위험 구역) 한국어 원문과 1:1 동일 확인(리팩토링 전/후 텍스트 비교), 영어 렌더링도 전체 확인("Team Management"·"5 seats remaining"·"4,900 KRW per seat"·"Danger Zone" 등). 팀 생성/API 키 발급 모달은 코드 리뷰로 확인(동일 `t()` 패턴 재사용, 클릭 실측은 도구 상의 이유로 생략).

## AdminPage — i18n 2차 스코프에서 제외 (2026-07-15, codeprint_130)

**결정.** AdminPage.tsx(791줄, ROLE_ADMIN 전용 운영 대시보드)는 실질적으로 사이트 운영자 본인만 접근하는 화면이라, "해외 유입자가 서비스를 이해하게"라는 i18n 도입 목적과 무관 — 사용자 확인 후 이번 2차 스코프에서 제외하고 PROGRESS.md 백로그로 이동. 재추진 조건: 운영진이 여러 명이 되거나 영어권 공동 운영자가 생길 때.

## GraphViewerPage i18n 이관 (2026-07-15, codeprint_130)

**범위.** 공개 그래프 공유 뷰어(802줄) — 로그인 없이 접근 가능해 실질적으로 i18n 2차 중 가장 국제 방문자 접점이 큰 페이지(공유 링크 클릭 시 첫 화면). `LayoutPresetToggle`·`LabelModeToggle`·`GraphLegend`·`OnboardingTour` 등 공용 컴포넌트 내부 문자열은 별도 트랙(공용 컴포넌트 17개)이라 이번 스코프 밖.

**온보딩 투어 콘텐츠.** `SHARE_TOUR_STEPS`가 모듈 최상단 상수였으나 `t()` 참조를 위해 컴포넌트 내부로 이동(HowItWorksPage의 `WARNING_GUIDE` 이전과 동일 패턴).

**대형 레포 절단 안내 — `<strong>` 강조 포기.** 원문은 파일 수 두 개를 `<strong>`로 감쌌으나, 한국어("전체 A개 파일 중 B개만")와 영어("only B of A files")가 두 숫자의 어순이 반대라 고정 슬롯(before/mid/after) 분할 방식이 통하지 않음(TermsPage류 패턴은 어순이 같을 때만 안전). Named interpolation(`{{total}}`/`{{analyzed}}`) 단일 문자열로 되돌리고 굵게 강조는 포기 — 어순이 다른 언어 간에는 위치 기반 분할과 인라인 스타일을 동시에 satisfy할 수 없다는 걸 확인한 사례, 후속 페이지에서 같은 패턴 만나면 인터폴레이션 우선.

**`activeDomainTab` 내부 sentinel 값 유지.** `useState<string>('전체')`와 비교 로직(`activeDomainTab === '전체'`, `activateTab('전체')`)은 그대로 두고, 탭 버튼에 보이는 라벨 텍스트만 `t('graphViewer.allTab')`로 감쌈 — SettingsPage의 `confirmWord`(사용자 입력과 매칭돼야 해서 값 자체를 지역화 필요)와 달리, 이 값은 순수 내부 필터 키라 표시와 저장을 분리해도 안전.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome) — 로컬 DB에서 공개 프로젝트(`pallets/flask`) ID 직접 조회해 `/share/{projectId}` 접근, 한국어("읽기 전용"·"외부 레포 분석"·"노드 검색"·"범례"·"경고"·"노드 정보")·영어("Read-only"·"External Repo Analysis"·"NODE SEARCH"·"Warnings"·"NODE INFO") 전환 확인, 온보딩 투어 1단계 제목·본문 번역 확인.

## CommunityPostGraphPage i18n 이관 — graphViewer 네임스페이스 재사용 (2026-07-15, codeprint_130)

**범위.** 커뮤니티 게시글 첨부 그래프 뷰어(886줄) — 레거시 단일 첨부(`CommunityPostGraphInner`)와 다중 스냅샷(`CommunityPostSnapshotInner`) 두 컴포넌트. 로컬 DB 확인 결과 레거시 경로(`posts.graph_id`)는 실사용 데이터가 없어(스냅샷 방식으로 전환 완료, `decisions/DECISIONS_BACKEND.md` 관련 이력 참조) 코드 리뷰로만 확인, 스냅샷 경로는 실측.

**재사용 판단.** GraphViewerPage.tsx와 사이드바·범례·경고 패널·노드 정보 UI가 거의 동일해(사이드바 접기/펼치기, 노드 검색, 범례 헤더, "전체" 탭, 경고 패널, 타입/도메인/경로/설명 라벨, 노드 클릭 안내 문구까지 문자열이 1:1 동일) 새 키를 만들지 않고 `graphViewer.*` 키를 그대로 재사용 — §1 "재사용성 먼저 확인" 원칙. 이 페이지에만 있는 문자열(엣지 타입 토글 6종 라벨, 코멘트 섹션, 숨김 요약, 스냅샷 없음 에러 등)만 `communityPostGraph` 네임스페이스로 신설.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome) — `/community/posts/{id}/graph/{position}`(sinatra/sinatra 스냅샷)에서 `read_page` interactive 필터로 헤더·사이드바·엣지 토글 6종·탭바·경고 패널 텍스트 전부 한/영 전환 확인("의존성"↔"Dependency" 등). `LayoutPresetToggle`·`GraphLegend`·React Flow `Controls` 내부(레이아웃 전환/전체 보기/확대·축소 등)는 공용 컴포넌트·라이브러리라 스코프 밖, 계속 한국어.

## GraphViewerPage·CommunityPostGraphPage에 언어·테마 토글 부재 — 부수 발견 및 수정 (2026-07-15, codeprint_130)

**문제.** 사용자가 CommunityPostGraphPage 스크린샷을 보고 "언어 토글이 어디 있냐"고 질문 — 확인 결과 `GraphViewerPage.tsx`·`CommunityPostGraphPage.tsx` 둘 다 공용 `AppHeader`를 쓰지 않고 자체 경량 상단 배너만 사용 중이었고, 그 배너엔 애초에 언어·테마 토글 UI 자체가 없었음. 텍스트는 번역해뒀지만 실제 사용자가 전환할 방법이 없는 상태 — 이번 세션 브라우저 검증이 `localStorage.setItem('codeprint_lang', ...)`로 직접 상태를 주입해 확인했기 때문에 이 갭을 놓쳤었음(교훈: 언어 전환 검증은 실제 UI 클릭으로 해야 이런 갭을 잡는다).

**범위 확인.** 사용자에게 `GraphPage.tsx`(로그인 후 본인 그래프 보는 화면)도 같은 문제인지 질문받아 확인 — `GraphPage.tsx`는 `AppHeader`를 그대로 쓰고 있어 문제없음. 갭은 비로그인 접근 가능한 두 읽기 전용 뷰어에만 국한.

**결정.** 새 공유 컴포넌트를 만들지 않고(사용처 2곳, 완전한 네비게이션은 불필요) `AppHeader.tsx`의 언어·테마 드롭다운 JSX·상태·로직을 두 페이지에 각각 인라인으로 복제 — 사용자가 "인라인 복제"를 명시적으로 선택. 각 페이지는 `useTranslation('workspace')`가 기본이라, `language.*`·`header.darkMode`·`header.lightMode`는 `common` 네임스페이스 소속이라 `useTranslation('common')`을 추가로 호출(`tCommon`)해 교차 참조 — UserProfilePage의 `misc` 교차 참조와 동일 패턴.

**부수 버그 — `Node` 타입 충돌.** 두 파일 모두 `@xyflow/react`의 `Node` 타입을 이미 import하고 있어, AppHeader의 바깥 클릭 감지 코드(`e.target as Node`, DOM `Node` 타입 의도)가 리액트플로우의 `Node`로 잘못 해석돼 컴파일 에러 발생. `e.target as globalThis.Node`로 명시해 DOM 타입임을 분명히 해 해결.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome) — GraphViewerPage에서 언어 드롭다운 클릭→열림→"English" 선택→헤더/사이드바/노드정보 전체 즉시 영어 전환 확인(스크린샷). CommunityPostGraphPage는 드롭다운 존재·상태 반영(`read_page`로 "Language" 버튼 노출) 확인 — 동일 코드라 클릭 상호작용은 GraphViewerPage 검증으로 갈음.

## CommunityPage i18n 이관 (2026-07-16, codeprint_130)

**범위.** 커뮤니티 게시판(942줄) — 목록/탭/정렬/글쓰기 폼/상세 패널/댓글/신고 모달 전체. `FEEDBACK_LABELS`·그래프/레포 배지는 UserProfilePage·misc 네임스페이스 재사용(select option 텍스트도 `FEEDBACK_LABELS.GENERAL` 등으로 통일 — 하드코딩된 `<option>일반</option>`이 `tMisc` 값과 어긋날 여지 제거). `snapshotLabel()`(도메인/계층·이름/주석 조합 함수)은 `t()` 참조 위해 컴포넌트 내부로 이동(SHARE_TOUR_STEPS 등과 동일 패턴).

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 로그아웃+로그인 양쪽) — 익명 보기(목록·정렬·탭·상세 패널·스냅샷 라벨) 한/영 전환, 로그인 후 글쓰기 폼 전체(제목/내용/피드백타입 셀렉트/그래프연결/공개범위 토글+안내문구/이미지첨부/등록) 영어 렌더링, 타인 게시글 신고 모달("Report Post"→placeholder→"Cancel"/"Report") 열기까지 실제 클릭으로 확인(제출은 안 함, 취소로 종료).

## GraphPage.tsx i18n 3분할 완료 + 공용 컴포넌트 착수 — WarningPanel (2026-07-16, codeprint_131)

**GraphPage 3분할(PR #579~581).** Context130에서 계획한 대로 진행: ①왼쪽 사이드바(검색·범례·보기옵션·스케치·버전관리)+상단 툴바(프리셋·내보내기·공유·키보드단축키)+배너/에러(로딩·최신커밋·대형레포절단·분석완료 결과카드·빈그래프안내) ②오른쪽 사이드바 상세뷰 10종(가장 큰 덩어리, `sidebar.kind` 분기별)+파일 내부 헬퍼(SidebarSection·FileConnGroup·FlowChainSection) ③모달 2개(프리셋 저장·커뮤니티 공유). `graphViewer.*`·`communityPostGraph.edgeTypes.*` 재사용 극대화, 신규 `graphPage`/`graphPage.sidebar`/`graphPage.modals` 네임스페이스. 3개 PR 모두 브라우저 실측(func/domain-summary/기본상태/프리셋모달/공유모달) 완료. **놓친 부분**: 3개 PR 전부 `ChangelogPage.tsx` 항목 추가를 빠뜨려 PR #582로 뒤늦게 보강(v0.136.0) — 이전 i18n PR들(#571, #573~578)은 전부 지켰던 규칙인데 GraphPage 3분할에서만 누락, 향후 i18n PR 체크리스트에 "Changelog 항목" 명시적으로 포함할 것.

**WarningPanel.tsx — `WARNING_META` 구조 변경(탈락 이유 포함).** `WARNING_META[type].label`/`.desc`는 `graphLayout.ts`의 `downloadWarningsMd()`(플레인 유틸 함수, React 컴포넌트 아님)에서도 직접 참조하는데, 이 함수는 React 훅(`useTranslation`)을 쓸 수 없어 단순히 `WARNING_META`를 그대로 두고 컴포넌트 안에서만 `t()`로 대체하는 방법은 불가능했다.
- **탈락안 1**: WARNING_META를 그대로 두고 컴포넌트에서만 label/desc를 별도로 관리 — HowItWorksPage·DogfoodingPage·graphLayout.ts 등 4개 소비처가 전부 `WARNING_META[type].label` 형태로 직접 참조해 데이터 중복(2026-07-13 "라벨은 WARNING_META 단일 소스 재사용 — 자체 맵 중복 시절 신규 타입 누락" 사고의 재발 위험) — 기각.
- **채택안**: `WARNING_META`는 `color`/`severity`(비번역, 정적)만 남기고, `label`/`desc`는 별도 함수 `getWarningLabel(type)`/`getWarningDesc(type)`로 분리 — `useTranslation` 훅 대신 `i18n.t(key, { ns: 'workspace' })`로 i18n 인스턴스를 직접 호출(`dateLocale.ts`의 `currentDateLocale()`과 동일 패턴, 컴포넌트 밖에서도 호출 가능). `WarningPanel.tsx` 자체의 렌더링(`WarningGroup`)은 리액티브 갱신을 위해 `useTranslation` 훅의 `t()`를 직접 사용, `graphLayout.ts`(원샷 다운로드 액션)와 `DogfoodingPage.tsx`(아직 i18n 미착수 페이지)는 plain 함수 `getWarningLabel`/`getWarningDesc`를 사용.
- **부수 영향**: `HowItWorksPage.tsx`(`meta?.label ?? type` → `t()` 직접 호출로 교체)·`DogfoodingPage.tsx`(`meta?.label ?? c.type` → `getWarningLabel(c.type)`로 교체) 두 기존 소비처 수정 필요 — 둘 다 `tsc -b`로 컴파일 에러 확인 후 수정, 회귀 없음을 브라우저로 재확인(HowItWorksPage: severity 순 정렬 유지, DogfoodingPage: 나머지 페이지는 한국어인데 경고 타입명만 영어로 섞여 렌더링 — 페이지 자체가 아직 i18n 대상 아니라 예상된 결과, 깨짐 아님).
- **경고 마크다운 리포트도 함께 번역**: `downloadWarningsMd()`의 리포트 제목("# 구조 경고 리포트")·총계 줄도 `warningPanel.reportTitle`/`reportTotalLine` 키로 번역 — WARNING_META를 직접 건드리는 김에 함께 처리(범위 확장이 아니라 같은 데이터 소스의 자연스러운 부수 효과).

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 로그인 세션) — GraphPage 우측 "Analysis · Warnings" 패널에서 경고 그룹 라벨/설명("Controller → Infra Direct Dependency", "Missing @Transactional" 등)·심각도 배지·개수 영어 렌더링, "Ignore" 버튼 클릭 → 인라인 폼("This rule will turn off 12 warnings", "Add Rule"/"Cancel") 확인 후 취소로 종료. `/how-it-works`·`/dogfooding` 재방문으로 회귀 없음 확인.

## 공용 컴포넌트 나머지(GraphLegend·GraphViewToggles·OnboardingTour) i18n — GRAPH_TOUR_STEPS 모듈 스코프 상수를 함수로 전환 (2026-07-16, codeprint_131)

**범위.** "공용 컴포넌트 17개" 백로그의 나머지 — `GraphLegend.tsx`("전체 보기"·"내용 표시/가리기")·`GraphViewToggles.tsx`(레이아웃/라벨 전환 버튼)·`OnboardingTour.tsx`(GraphPage 첫 방문 온보딩 투어 5단계 + Joyride 내비게이션 라벨). 신규 `graphShared` 네임스페이스(`legend`/`layoutToggle`/`labelToggle`/`tour`).

**`GRAPH_TOUR_STEPS` 구조 변경(탈락 이유 포함).** 기존 `OnboardingTour.tsx`는 `GRAPH_TOUR_STEPS`를 모듈 스코프 `const` 배열로 export해 `GraphPage.tsx`가 그대로 import해 썼다. 모듈 스코프 상수는 import 시점에 딱 한 번만 평가되므로, 그 안에 `t()`(리액트 훅)를 넣을 수 없다(훅은 컴포넌트 렌더 중에만 호출 가능).
- **탈락안**: `useTranslation`을 `OnboardingTour.tsx` 최상단에서 호출해 모듈 스코프에서 번역 — 애초에 불가능(훅은 컴포넌트/커스텀훅 내부에서만 호출 가능, React 규칙 위반으로 런타임 에러).
- **채택안**: `GRAPH_TOUR_STEPS` export를 제거하고, 같은 파일에 함수 `getGraphTourSteps(): Step[]`을 새로 만들어 `i18n.t(key, { ns: 'workspace' })`로 직접 인스턴스 호출(`WarningPanel.tsx`의 `getWarningLabel`/`getWarningDesc`와 동일 패턴, `dateLocale.ts`의 `currentDateLocale()`이 원조). `GraphPage.tsx`는 JSX 안에서 `steps={getGraphTourSteps()}`처럼 렌더마다 새로 호출 — 메모이제이션 없이 매 렌더 재계산이라 언어 전환 시에도 최신 번역이 항상 반영됨(GraphPageInner가 이미 `t()`를 쓰고 있어 언어 전환마다 재렌더되는 것에 편승).
- **Joyride 자체의 `locale`(이전/닫기/완료/다음/열기/건너뛰기 버튼 라벨)은 컴포넌트 내부에서 `useTranslation` 훅으로 정상 처리** — steps와 달리 이건 항상 컴포넌트 렌더링 중에만 쓰이므로 훅 사용에 문제없음.

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 로그인 세션) — 상단 툴바 "Switch Layout"/"Layer"·"Domain", "Label display mode"/"Name"·"Comment", 좌측 사이드바 범례 "Show All" 영어 렌더링 확인. `localStorage.removeItem('onboarding_tour_done')` 후 재접속해 온보딩 투어 1·2단계("👋 Welcome to Codeprint!"·"Switch Layout" 설명, "Skip"/"Back"/"Next (N of 5)" 버튼) 실제 클릭으로 확인.

## "i18n 사실상 완료" 기록이 스테일 — MessagesPage 전체 누락 + ProjectCard 미포함 발견·수정 (2026-07-16, codeprint_132)

**문제.** codeprint_131 종료 시점 PROGRESS.md·Context131이 "i18n 도입이 GraphPage.tsx + 공용 컴포넌트 전체까지 완료돼 사실상 마무리"로 기록했으나, 사용자 질문("사용자 페이지 영어버전 다 끝났어?")에 `useTranslation` import 여부로 전체 페이지 전수 재확인한 결과 두 가지 누락 발견.
- **`MessagesPage.tsx`**: `useTranslation` import 자체가 없어 헤더·알림 설정·차단 확인 대화상자·입력 placeholder·전송 버튼 등 전체가 하드코딩 한글. "핵심 기능 화면 9개"(PR #573~578) 스코프에서 이 페이지만 통째로 빠진 것으로 추정 — 원인 특정은 안 됨(당시 세션 기록에 언급 자체가 없음).
- **`components/ProjectCard.tsx`**: codeprint_130의 "MyPage i18n 이관" 결정(위 항목)에서 "`ProjectCard`는 별도 공용 컴포넌트 트랙(PROGRESS.md 백로그 '공용 컴포넌트 17개')이라 이번엔 한국어로 남김"이라고 명시적으로 스코프 아웃했는데, 정작 codeprint_131의 "공용 컴포넌트 17개 종료" 커밋(PR #583·#584)이 실제로는 `WarningPanel`·`GraphLegend`·`GraphViewToggles`·`OnboardingTour` 4개만 처리하고 `ProjectCard`를 빠뜨린 채 백로그를 "종료"로 정리 — PROGRESS.md 상 "완료" 표시와 실제 코드 상태가 어긋남.

**원인 공통점.** 둘 다 "리스트에서 빠짐" 종류의 누락이라 `analyzeLocal`/`tsc` 등 정적 검증으로는 못 잡음(컴파일 관점에서 결함이 아니라 그냥 한국어 문자열이 남아있는 것) — 완료 보고를 코드 재확인 없이 그대로 믿었다면 계속 스테일 상태로 남았을 사례. `[[feedback_adversarial_verification]]`(자가보고 불신, 코드로 반증) 원칙이 정확히 이런 경우를 겨냥한 것.

**수정.** `MessagesPage.tsx`(신규 `workspace.messages` 네임스페이스, `graphViewer.loading` 키 교차 참조) + `components/ProjectCard.tsx`(신규 `workspace.projectCard` 네임스페이스, 약 35개 키 — 뱃지·분석 진행률·브랜치 피커·PR 리뷰 패널·게이트 설정 패널·하단 액션 버튼 전체) 전면 이관. 날짜/시간 포맷은 기존 `currentDateLocale()` 패턴 재사용(`MessagesPage`의 `toLocaleTimeString('ko-KR')` 하드코딩도 함께 교정).

**결과.** `tsc -b` 통과. 브라우저 실측(claude-in-chrome, 실 로그인 세션, 한국어/영어 양쪽) — MessagesPage 전체(받은 쪽지함·알림 설정 토글 3종·빈 상태 문구) + `/mypage` 프로젝트 카드 4장(신선도 뱃지·날짜 포맷 en-US·공개/비공개·PR 리뷰 패널·게이트 설정 패널·다른 브랜치 피커까지 실제 클릭으로 하위 패널 전개) 확인, 콘솔 에러 0건. `SettingsPage.tsx`의 이미지 삭제 성공/실패 메시지 2곳도 같은 세션에서 기존 미사용 키(`settings.imageDeleted`/`imageDeleteFailed`)로 마저 연결.

**교훈.** "N개 항목 백로그"를 종료 처리할 때 실제로 N개를 다 건드렸는지 커밋 diff로 재확인 없이 "종료"로 정리하면 이번처럼 누락이 완료 기록에 묻힌다 — 백로그 종료 커밋은 대상 목록과 실제 변경 파일을 대조하는 습관화가 필요.

## README.md 영어 번역 — README.en.md 신설 + 상호 링크 (2026-07-16, codeprint_132)

**배경.** i18n 2차(위 항목들)는 로그인 후 화면만 대상이었는데, 사용자가 "README도 영어판이 있어야 하지 않냐"고 지적 — GitHub 저장소 방문자(특히 해외 채용담당자)가 가장 먼저 보는 페이지라 오히려 로그인 후 화면보다 접점이 크다는 점에서 타당한 지적.

**구조 선택(탈락안 포함).**
- **탈락 A: 한 파일에 언어별 섹션 병기.** 파일 하나로 관리는 편하지만 스크롤이 두 배로 길어지고, GitHub가 렌더링하는 첫 화면(스크롤 없이 보이는 영역)에 badge/소개문이 두 언어로 겹쳐 보여 방문자 경험이 나빠짐.
- **탈락 B: README.md를 영어로 교체하고 기존 내용을 README.ko.md로 이동.** GitHub가 기본 노출하는 파일을 영어로 바꾸는 방식 — 이 저장소는 CLAUDE.md §5 규칙상 개발 문서·커밋 메시지가 전부 한국어 우선인 프로젝트라, 저장소 첫 화면만 영어 기본값으로 바뀌면 나머지 문서 전체와 언어 우선순위가 어긋남.
- **채택: README.md는 한국어 유지, README.en.md 신규 추가.** 기존 파일·기본 노출 언어는 그대로 두고 영어판을 별도 파일로 추가 — 원본 변경 최소화(§3 surgical), 프로젝트 전체의 "한국어 우선, 사용자 접점만 영어 병행" 원칙과도 일치.

**언어 전환 UX 한계 — 사용자에게 사전 고지.** README는 GitHub가 정적으로 렌더링하는 마크다운이라 JavaScript가 실행되지 않음 — 웹앱의 `react-i18next` 드롭다운처럼 같은 화면에서 즉시 토글되는 방식은 애초에 불가능. 대신 각 파일 상단에 `🇺🇸 English / 🇰🇷 한국어` 상호 링크를 넣어 클릭 시 다른 파일로 이동하는 방식(다수 오픈소스 저장소의 관례)으로 구현 — "이것도 웹페이지처럼 즉시 전환되냐"는 질문에 기술적 한계를 먼저 설명하고 진행 동의를 받음.

**결과.** `README.en.md` 신규 생성(기존 `README.md`와 섹션·배지·링크 구조 1:1 대응, 코드 블록·기술 고유명사는 원문 유지), `README.md` 상단에 언어 전환 링크 추가. 순수 마크다운 문서 변경이라 `tsc -b`·브라우저 실측 대상 아님(규칙 4 런타임 검증은 코드 변경에만 적용, 정적 문서는 프리뷰 대상 없음).

# 프론트엔드 시행착오 & 설계 결정

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

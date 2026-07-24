# Codeprint — 코드 구조·컨벤션 레퍼런스

> 이 문서는 "왜 이렇게 만들었나"(→ `ARCHITECTURE.md` "구조 채택 이유")가 아니라
> **"지금 뭐가 어디 있고 어떻게 재사용하는지"만** 짧게 기록한다. 서사 없이 사실만 — 매 세션 build.gradle·소스를
> 다시 훑지 않고 이 문서만 보고 판단할 수 있는 걸 목표로 한다.
>
> **갱신 시점**: 대화 중 "이게 문서화가 안 돼 있었네" 싶은 지점이 발견되면 그 자리에서 이 문서에 추가할지 제안한다
> (슬래시 명령 등 별도 절차로 미루지 않음 — 2026-07-24 결정).

---

## 로컬 CLI 도구군(`com.codeprint.tools`) — DB/Spring 없이 임의 디렉터리 분석

**핵심 재사용 지점**: `LocalAnalyzer.buildGraph(rootDir, projectId, loader)`(package-private) —
Spring/DB 없이 임의 `Path`를 받아 파싱 → `GraphBuilder` → 인메모리 `List<Node>`/`List<Edge>`를 반환하는 단일 코어.
`InMemoryGraphRepository`(같은 파일 내부 클래스)로 DB 없이 `GraphBuilder`를 그대로 구동 — 프로덕션과 동일한
빌더를 쓰므로 별도 재구현본보다 호출 해소 정확도가 높다(과거 자체 재구현판은 정확도가 낮았던 전례, `ARCHITECTURE.md` 참조).

**이 코어를 공유하는 3개 진입점**(전부 `backend/src/main/java/com/codeprint/tools/`):

| 클래스 | Gradle task | 용도 |
|---|---|---|
| `LocalAnalyzer` | `analyzeLocal` | 워닝 목록 출력 |
| `LocalWatcher` | `watchLocal` | 파일 변경 감지 → 증분 재분석, JSON stdout(VS Code 확장 소비) |
| `LocalGraphQuery` | `exploreLocal` | repo map / 노드검색(`find`) / 이웃조회(`neighbors`) |

**경로 파라미터화 컨벤션**: 어떤 레포를 분석할지는 코드에 없다 — `build.gradle`의 Gradle task 인자
`-PanalysisDir=<경로>`로만 결정된다(미지정 시 기본값 `src/main/java`, 즉 자기 레포 — 이건 "편의상 기본값"이지
하드코딩이 아니다). `exploreLocal`은 추가로 `-PqueryMode=repoMap|find|neighbors -PqueryTarget=검색어`를 받는다
(⚠️ `find`/`neighbors` 모드는 `-PqueryTarget=` 파라미터명을 정확히 써야 함 — `-Pquery=`로 잘못 넘기면 조용히
무시되고 repoMap과 동일한 전체 목록이 반환됨, `decisions/DECISIONS_INFRA.md` 참조).

**이 계열에 새 로컬 도구를 추가할 때 규칙**:
1. `LocalAnalyzer.buildGraph`(또는 그 결과를 감싸는 기존 메서드)를 재사용 — 그래프 생성 로직 재구현 금지.
2. 분석 대상 경로는 반드시 Gradle task 인자(`-PanalysisDir=`)로 받고, 소스에 특정 레포 경로를 박지 않는다.
3. 기본값만 자기 레포로 둔다(즉시 실행 편의). 다른 레포(예: 언어별 벤치용 OSS 레포 clone) 분석은
   `-PanalysisDir=<clone경로>`만 바꾸면 되고 Java 코드 변경이 없어야 한다.

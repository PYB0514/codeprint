// 서비스 버전별 패치노트를 공개하는 페이지
import AppHeader from '../components/AppHeader'

export interface Release {
  version: string
  date: string
  title: string
  type: 'major' | 'feature' | 'fix' | 'infra'
  items: { category: string; desc: string }[]
}

export const RELEASES: Release[] = [
  {
    version: 'v0.93.18',
    date: '2026-06-24',
    title: '유저 프로필 글목록 로딩 속도 개선 — 조회 쿼리 일괄 처리',
    type: 'fix',
    items: [
      { category: '성능', desc: '유저 프로필의 작성 글 목록을 불러올 때 글마다 북마크 수·내 북마크 여부를 따로 조회하던 것을, 커뮤니티 피드(v0.93.17)와 동일하게 페이지 단위로 한 번에 묶어 조회하도록 바꿨습니다. 프로필을 열 때의 응답 속도를 개선했습니다. 표시 내용은 동일합니다.' },
    ],
  },
  {
    version: 'v0.93.17',
    date: '2026-06-24',
    title: '커뮤니티 피드 로딩 속도 개선 — 목록 조회 쿼리 일괄 처리',
    type: 'fix',
    items: [
      { category: '성능', desc: '커뮤니티 게시글 목록(피드·내 북마크)을 불러올 때, 기존에는 글 하나하나마다 작성자명·북마크 수·좋아요 수·댓글 수·내 북마크/좋아요 여부를 따로 조회해 글이 20개면 100건 이상의 DB 조회가 발생했습니다. 이를 페이지 단위로 한 번에 묶어 조회하도록 바꿔(약 6건), 피드를 열 때의 응답 속도를 개선했습니다. 표시되는 내용은 동일합니다.' },
    ],
  },
  {
    version: 'v0.93.16',
    date: '2026-06-24',
    title: '분석 속도 개선 — 파일 파싱 병렬 처리',
    type: 'fix',
    items: [
      { category: '성능', desc: '저장소를 분석할 때 각 소스 파일을 구문 분석(tree-sitter)하는 단계를 여러 CPU 코어에서 동시에 처리하도록 바꿨습니다. 기존에는 파일을 하나씩 순서대로 분석해 파일이 많은 대형 저장소일수록 시간이 오래 걸렸습니다. 파일 분석은 서로 독립적이므로 병렬로 처리해도 결과가 동일하며(순차 처리와 결과가 같은지 자동 테스트로 검증), 코어 수에 비례해 파싱 시간이 단축됩니다. 분석 결과(그래프 내용)는 동일합니다.' },
    ],
  },
  {
    version: 'v0.93.15',
    date: '2026-06-24',
    title: '분석 그래프 저장 속도 개선 — 노드/엣지 DB 저장 방식 최적화',
    type: 'fix',
    items: [
      { category: '성능', desc: '저장소 분석 후 그래프를 DB에 저장하는 단계를 빠르게 개선했습니다. 기존에는 노드·엣지를 한 건씩 저장할 때마다 “이미 존재하는지” 확인하는 조회 쿼리가 먼저 나가고, 저장도 한 건씩 처리돼 대형 저장소(수천 개의 노드·엣지)에서는 DB 왕복이 수천 번 발생했습니다. 노드·엣지는 저장 시점에 이미 새 항목임이 확실하므로 불필요한 사전 조회를 제거하고, 여러 건을 묶어 한 번에 저장(배치)하도록 바꿨습니다. 분석 완료까지의 대기 시간이 특히 큰 저장소에서 단축됩니다. 분석 결과(그래프 내용)는 동일합니다.' },
    ],
  },
  {
    version: 'v0.93.14',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(Rust) + Rust 테스트 함수 과도호출 경고 제외',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'Rust에서도 변수를 통해 호출하는 함수를 그 변수의 선언 타입으로 정확히 연결하도록 개선했습니다. 메서드의 self(impl 대상 타입), 함수 매개변수(repo: &UserRepo), 지역변수(let x: Foo / let x = Foo {})의 타입을 추적해 self.step()·repo.save() 같은 호출을 타입을 선언한 파일로 잇습니다. Rust도 파일명과 타입명이 다르므로(snake_case 파일), 파일이 선언한 struct/enum/trait 이름으로 대상을 찾는 방식을 함께 적용했습니다. 외부 ripgrep 오픈소스 분석에서 메서드 호출 연결이 418개 늘어 누락이 줄었음을 확인했습니다. 이로써 Java·C#·TypeScript·Python·Go·Rust 여섯 언어가 변수 타입 기반 호출 추적을 지원합니다.' },
      { category: '경고', desc: 'Rust 테스트 함수(#[test], #[cfg(test)] mod tests 내부)를 과도한 호출(HIGH_FAN_OUT) 경고에서 제외했습니다. 테스트는 setup·검증으로 자연히 여러 함수를 호출하므로 단일 책임 위반이 아닌데, Rust 테스트는 별도 파일이 아니라 소스 내부에 인라인으로 작성돼 기존 파일명 기반 제외가 동작하지 않았습니다. ripgrep 분석에서 과도호출 경고가 테스트 오탐을 제거해 77→42개로 정밀해졌습니다.' },
    ],
  },
  {
    version: 'v0.93.13',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(Go)',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'Go에서도 변수를 통해 호출하는 함수를 그 변수의 선언 타입으로 정확히 연결하도록 개선했습니다. 메서드 리시버(func (c *Context) ...에서의 c), 함수 매개변수, 지역변수(e := Engine{})의 타입을 추적해 c.Next() 같은 호출을 타입을 선언한 파일로 정확히 잇습니다. Go는 파일명과 타입명이 다르므로(예: user_repository.go의 UserRepository), 파일이 선언한 타입 이름으로 대상을 찾는 방식을 함께 적용했습니다. 외부 Gin 오픈소스 분석에서 변수 메서드 호출이 엉뚱한 동명 함수로 잘못 이어지던 가짜 연결 88개가 제거되고(노드·미참조 함수 수는 그대로), 같은 파일 내 호출 표시는 보존됨을 확인했습니다. 이로써 Java·C#·TypeScript·Python·Go 다섯 언어가 변수 타입 기반 호출 추적을 지원합니다.' },
    ],
  },
  {
    version: 'v0.93.12',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(Python)',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'Python에서도 변수를 통해 호출하는 함수를 그 변수의 타입으로 정확히 연결하도록 개선했습니다. self.repo = ProfilesRepository(conn)처럼 생성자 대입으로 타입이 정해지는 경우(타입 힌트 없이도), 타입 어노테이션(self.x: Type, def m(self, x: Type)), 지역변수(v = Repo())를 모두 인식합니다. 외부 FastAPI 오픈소스 분석에서 self._tags_repo.create_tags_that_dont_exist() 같은 호출이 실제 정의 파일(tags.py)로 정확히 연결됨을 확인했습니다. 이로써 Java·C#·TypeScript·Python 네 언어가 변수 타입 기반 호출 추적을 지원합니다. 타입을 알 수 없는 경우엔 기존 방식으로 안전하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.93.11',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(TypeScript)',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'TypeScript에서도 변수를 통해 호출하는 함수를 그 변수의 선언 타입(클래스 필드·생성자 매개변수 주입·함수 매개변수·지역변수)으로 정확히 연결하도록 개선했습니다. TS·Python처럼 파일명과 클래스명이 다른 언어를 위해, 파일이 선언한 클래스/인터페이스 이름으로 호출 대상을 찾는 공통 해소 방식을 도입했습니다. 외부 NestJS 오픈소스 분석에서 this.articleService.findFeed() 같은 의존성 주입 호출이 정확한 서비스 파일로 연결되고, 라이브러리(TypeORM) 호출이 무관한 파일로 잘못 이어지던 가짜 연결이 제거됨을 확인했습니다. 타입을 알 수 없는 경우엔 기존 방식으로 안전하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.93.10',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(C#)',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'Java에 이어 C#에서도, 변수를 통해 호출하는 함수를 그 변수의 선언 타입(필드·매개변수·지역변수·C# 12 primary constructor 매개변수)으로 정확히 연결하도록 개선했습니다. 제네릭 타입(IRepository<T>)은 베이스 이름으로, nullable 타입(Foo?)은 실제 타입으로 풀어 처리합니다. 외부 C# 오픈소스(clean-architecture) 분석 결과 무관한 파일로 잘못 이어지던 가짜 연결 24건이 제거되고 도메인 클래스로의 호출이 정확히 연결됨을 확인했습니다. 타입을 알 수 없는 경우(var 등)엔 기존 방식으로 안전하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.93.9',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 변수 선언 타입 기반 추적(Java)',
    type: 'fix',
    items: [
      { category: '그래프', desc: 'repo.save()·order.confirm()처럼 변수를 통해 호출하는 함수를, 그 변수의 선언 타입(필드·매개변수·지역변수)을 읽어 정확한 클래스로 연결하도록 개선했습니다. 기존에는 메서드 이름만 보고 같은 이름을 가진 무관한 파일로 잘못 이어지곤 했습니다. Codeprint 자체 분석에서, 분석기가 10개 언어 파서의 parse()를 호출하는 부분이 엉뚱한 한 곳으로만 연결되던 것이 실제 10개 파서로 정확히 분리되는 등 호출 흐름이 바로잡혔습니다. 타입을 알 수 없는 경우엔 기존 방식으로 안전하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.93.8',
    date: '2026-06-23',
    title: '과도한 의존(HIGH_FAN_OUT) 오탐 감소 — 가짜 호출 연결 제거',
    type: 'fix',
    items: [
      { category: '경고', desc: 'get·add·put·isEmpty 같은 표준 라이브러리 내장 메서드 호출이 무관한 파일로 잘못 연결되던 가짜 엣지를 제거했습니다. 이 가짜 연결이 함수의 호출 수를 부풀려 "과도한 의존(HIGH_FAN_OUT)" 경고를 잘못 띄우던 문제를 고쳤습니다(Codeprint 자체 분석에서 오탐 3건 제거 확인). 같은 패키지 내 실제 호출(import 없이 호출하는 Go 등)은 보존해 누락이 생기지 않도록 했습니다.' },
    ],
  },
  {
    version: 'v0.93.7',
    date: '2026-06-23',
    title: '함수 호출 연결 정확도 개선 — 실제 import 기반 추적',
    type: 'fix',
    items: [
      { category: '그래프', desc: '클래스명 없이 호출되는 함수(save·findById 등)를 그래프에 연결할 때, 같은 이름의 아무 파일이 아니라 호출하는 파일이 실제로 import한 파일로 연결하도록 고쳤습니다. 기존에는 이름만 같으면 무관한 파일로 잘못 연결되는 경우가 있었고(예: 어떤 서비스의 save() 호출이 전혀 관계없는 다른 서비스로 이어짐), 이 때문에 호출 흐름 추적이 부정확했습니다. Codeprint로 Codeprint 자체를 분석한 결과 자기 코드에서만 199건의 잘못된 연결을 찾아 교정했습니다. import 정보를 못 읽는 경우엔 기존 방식으로 안전하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.93.6',
    date: '2026-06-22',
    title: 'AI 키 관리 API 500 오류 수정 — AI 기능 정상화',
    type: 'fix',
    items: [
      { category: 'AI', desc: 'AI 제공자 키 조회·등록 등 AI 관련 API(/api/ai/*)가 내부 오류로 동작하지 않던 문제를 수정했습니다. 이제 Claude/OpenAI/Gemini API 키를 정상적으로 등록하고 함수 설명·코드 생성 등 AI 기능을 사용할 수 있습니다. (서버 오류 추적 ID 알림 기능이 이 잠재 오류를 찾아내 수정으로 이어졌습니다.)' },
    ],
  },
  {
    version: 'v0.93.5',
    date: '2026-06-22',
    title: '서버 오류 발생 시 추적 ID를 화면에 표시',
    type: 'fix',
    items: [
      { category: '안정성', desc: '서버 오류(5xx)가 발생하면 우측 하단에 추적 ID(traceId)를 담은 알림을 표시합니다. 문의 시 이 ID를 알려주시면 운영자가 해당 오류를 로그·Sentry에서 정확히 찾을 수 있습니다(Cloudflare Ray ID와 같은 개념). 사용자 입력 오류(4xx)에는 표시하지 않습니다.' },
    ],
  },
  {
    version: 'v0.93.4',
    date: '2026-06-22',
    title: '과도한 의존(HIGH_FAN_OUT) 경고에서 main 진입점 제외 — 오탐 감소',
    type: 'fix',
    items: [
      { category: '경고', desc: '"한 함수가 7개 넘게 호출"을 잡는 과도한 의존(HIGH_FAN_OUT) 경고에서 main 진입점을 제외했습니다. main(Spring 부트스트랩·CLI·func main 등)은 본질적으로 여러 협력자를 호출하는 게 정상이라 단일 책임 위반이 아닌데, 기존에는 이를 경고로 잡는 오탐이 있었습니다. 테스트 함수를 제외하는 것과 같은 원리입니다. Codeprint로 Codeprint 자체를 분석하는 도그푸딩 과정에서 발견·수정했습니다.' },
    ],
  },
  {
    version: 'v0.93.3',
    date: '2026-06-22',
    title: '서버 오류에 추적 ID(traceId) 부여 — 운영 추적성 개선',
    type: 'fix',
    items: [
      { category: '안정성', desc: '서버 오류(5xx)가 발생하면 응답과 서버 로그에 동일한 추적 ID(traceId)를 부여합니다. 같은 ID가 Sentry 이벤트에도 남아, 오류 신고 시 그 ID 하나로 정확한 요청을 추적할 수 있습니다. 사용자 입력 오류(4xx)는 추적이 불필요해 부여하지 않으며, 기존 응답 형식(status·message·timestamp)은 그대로 유지됩니다.' },
    ],
  },
  {
    version: 'v0.93.2',
    date: '2026-06-22',
    title: '레이어 위반 감지에 Model(도메인 모델) 레이어 추가',
    type: 'fix',
    items: [
      { category: '경고', desc: '비DDD 레이어드 프로젝트의 레이어 역전 감지에 Model(도메인 모델·엔티티) 레이어를 추가했습니다. 클래스명 접미사(*Entity·*Model·*VO)와 디렉터리(model/·entity/·domain/)로 분류해, 도메인 모델이 Controller·Service·Repository 같은 상위 레이어를 import하는 역전 의존을 잡습니다. 모델은 순수 데이터·규칙이어야 하며 상위 레이어를 알면 안 됩니다. DTO는 레이어 전반을 오가므로 분류에서 제외해 오탐을 막았습니다.' },
    ],
  },
  {
    version: 'v0.93.1',
    date: '2026-06-22',
    title: 'PR 자동 리뷰 코멘트 중복 누적 방지 — 갱신(upsert) 방식 전환',
    type: 'fix',
    items: [
      { category: 'PR 연동', desc: 'GitHub PR에 커밋을 push할 때마다 Codeprint 구조 분석 봇 코멘트가 새로 추가돼 스레드가 길어지던 문제를 고쳤습니다. 이제 기존 분석 코멘트가 있으면 같은 코멘트를 갱신하고(없으면 새로 작성), PR마다 항상 최신 분석 결과 하나만 유지됩니다. 코멘트 조회에 실패하면 기존처럼 새로 작성해 리뷰가 누락되지 않습니다.' },
    ],
  },
  {
    version: 'v0.93.0',
    date: '2026-06-22',
    title: '비DDD 프로젝트 레이어 위반 경고 — Controller/Service/Repository 자동 감지',
    type: 'feature',
    items: [
      { category: '경고', desc: '지금까지 아키텍처 경고(컨텍스트 경계·의존 방향 등)는 domain/application/infrastructure 폴더를 쓰는 DDD 프로젝트에만 적용돼, 일반적인 레이어드 구조(Controller/Service/Repository) 프로젝트에서는 경고가 거의 나오지 않았습니다. 이제 클래스명 접미사(OwnerController·OwnerRepository)와 디렉터리 컨벤션(controllers/·services/·repositories/)으로 레이어를 자동 감지해 두 가지 위반을 잡습니다. ①레이어 역전 의존(LAYERED_REVERSE_DEPENDENCY): Repository가 Controller를 import하는 등 하위 레이어가 상위를 참조하는 경우. ②Service 레이어 우회(LAYERED_BYPASS): Service 레이어가 있는데도 Controller가 Repository를 직접 접근하는 경우. Service 레이어가 아예 없는 단순 앱은 정상으로 보고 경고하지 않습니다.' },
    ],
  },
  {
    version: 'v0.92.2',
    date: '2026-06-21',
    title: '도메인 자동 분류 정확도 개선 — 동사형 파일명 → 명사형 도메인',
    type: 'fix',
    items: [
      { category: '그래프', desc: '도메인 뷰에서 동사형 이름의 파일이 도메인으로 묶이지 않고 "공통"으로 흩어지던 문제를 개선했습니다. 영어 명사화 규칙(동사→명사 -ion/-ment: donate→donation, pay→payment, create→creation)을 적용해, 예를 들어 후원 페이지(DonatePage 등)가 이제 donation 도메인으로 올바르게 분류됩니다. 모든 분석 대상 프로젝트에 적용됩니다.' },
    ],
  },
  {
    version: 'v0.92.1',
    date: '2026-06-21',
    title: '그래프 좌측 사이드바 경량화 — 툴바 통합 + 분석 패널 코너 이동',
    type: 'fix',
    items: [
      { category: '그래프', desc: '좌측 사이드바에 14개 섹션이 세로로 길게 쌓여 스크롤이 과하던 것을 세 구역으로 분산했습니다. ①레이아웃(계층형/도메인)·라벨(이름/주석) 토글과 프리셋·내보내기를 상단 툴바로 이동(프리셋·내보내기는 팝업). ②AI 누락 패턴 감지·아키텍처 의도·런타임 경고는 그래프 위 좌·우 하단의 접이식 코너 패널로 이동(기본 접힘, 경고는 개수 뱃지 표시). 사이드바에는 탐색·필터(노드 검색·타입 필터·엣지·범례·버전·스케치)만 남겨 스크롤이 절반으로 줄었습니다.' },
    ],
  },
  {
    version: 'v0.92.0',
    date: '2026-06-21',
    title: '숨긴 경고 복원 — 새로고침·재방문 후에도 유지',
    type: 'feature',
    items: [
      { category: '경고', desc: '경고를 숨겨도 페이지를 새로고침하면 "숨긴 경고" 목록이 비어 더 이상 복원할 수 없던 문제를 해결했습니다. 이제 숨긴 경고가 서버에 영속 저장돼 다시 접속해도 목록과 복원 버튼이 그대로 유지되며, 언제든 복원할 수 있습니다. (프로젝트 소유자 전용)' },
    ],
  },
  {
    version: 'v0.91.1',
    date: '2026-06-20',
    title: '분석 엔진 표기 정정 — "정규식 기반" → tree-sitter AST(하이브리드)',
    type: 'fix',
    items: [
      { category: '프론트', desc: '11개 언어의 함수·호출 추출이 정규식에서 tree-sitter AST로 전환됐는데도 랜딩·작동방식 페이지가 여전히 엔진을 "정규식 기반"으로 표기하던 것을 정정했습니다. 함수·호출은 tree-sitter AST, DB·API·import·주석은 정규식인 하이브리드라는 실제 구조를 반영하고, 경고 한계 설명도 이제 AST가 처리하는 메서드 레퍼런스(::) 등을 "정규식 한계"로 잘못 귀속하던 문구를 바로잡았습니다(LandingPage·HowItWorksPage·EvolutionPage).' },
    ],
  },
  {
    version: 'v0.91.0',
    date: '2026-06-20',
    title: 'C++ 언어 분석 지원 추가 — 총 13개 언어',
    type: 'feature',
    items: [
      { category: '분석 엔진', desc: 'C++(.cpp/.cc/.cxx/.hpp/.hh) 소스 분석을 추가했습니다. 그동안 확장자는 인식하면서도 실제 분석에서는 제외되던 반쪽 상태를 해소하고, tree-sitter AST로 클래스 메서드·생성자·소멸자·연산자 오버로드(operator+ 등)·아웃오브라인 정의(Foo::bar)·템플릿 함수를 정확히 추출합니다. 호출은 bare·멤버(obj.method / ptr->method)·정적(Class::method) 형태를 모두 인식합니다. nlohmann/json·fmt 기준 크래시 없이 추출 확인.' },
    ],
  },
  {
    version: 'v0.90.0',
    date: '2026-06-20',
    title: 'C 언어 분석 지원 추가 — 총 12개 언어',
    type: 'feature',
    items: [
      { category: '분석 엔진', desc: 'C(.c) 소스 분석을 추가했습니다. 그동안 확장자는 인식하면서도 실제 분석에서는 제외돼 빈 그래프가 나오던 반쪽 상태를 해소하고, tree-sitter AST로 함수 정의(선언자 체인에서 함수명 추출, 포인터 반환·static 포함)와 함수 호출을 정확히 추출합니다. curl(192개 .c 파일) 기준 함수 3,664개·호출 15,880개 추출 확인.' },
    ],
  },
  {
    version: 'v0.89.11',
    date: '2026-06-20',
    title: '과도한 의존(HIGH_FAN_OUT) 경고 정밀화 — 동명 머지 정확 감지',
    type: 'fix',
    items: [
      { category: '경고 엔진', desc: '한 파일에 같은 이름의 메서드가 여러 개 있을 때(오버로드·다형성) 한 노드로 합쳐지며 호출이 합산돼 의존 수가 부풀려지던 문제를, 노드별 정의 횟수를 추적해 정확히 제외하도록 바꿨습니다. 이전에는 같은 이름이 여러 파일에 있다는 이유만으로 진짜 과도한 의존까지 함께 숨겨졌는데, 이제 각 정의를 독립적으로 판단해 가려졌던 실제 경고가 드러납니다.' },
    ],
  },
  {
    version: 'v0.89.10',
    date: '2026-06-20',
    title: 'PHP 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'PHP 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 정규식이 못 잡던 `->`(인스턴스)·`?->`(널세이프)·`::`(정적) 메서드 호출을 정확히 인식하고, 정규식이 호출로 오인하던 PHP 키워드(`foreach`·`function`·`match`·`static` 등)를 호출에서 제외해 거짓 데드코드·과도한 의존 경고를 줄였습니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.9',
    date: '2026-06-20',
    title: 'Ruby 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'Ruby 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 정규식이 `def self.build`에서 메서드명 대신 `self`를 잘못 잡던 싱글톤 메서드 버그와 세터(`config=`)의 `=` 누락을 바로잡고, 괄호 없는 명령형 호출(`save name`)·수신자 메서드 호출·블록 안의 호출까지 정확히 인식합니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.8',
    date: '2026-06-20',
    title: 'C# 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'C# 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 정규식이 접근 제어자 제약으로 놓치던 internal·override 메서드와 표현식 바디(=> 1)·로컬 함수·생성자까지 정확히 인식하고, 주석·문자열 속 식별자를 호출로 오인하지 않습니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
      { category: '분석 엔진', desc: 'UTF-8 BOM으로 저장된 소스 파일(.NET 프로젝트에서 흔함)에서 함수·호출 이름이 어긋나 추출되던 문제를 수정했습니다. BOM이 있는 모든 언어 파일에 적용됩니다.' },
    ],
  },
  {
    version: 'v0.89.7',
    date: '2026-06-20',
    title: 'Rust 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'Rust 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 정규식이 못 잡던 pub(crate) fn·const fn·unsafe fn 같은 변형 함수와 impl 메서드·trait 시그니처를 정확히 인식하고, 어트리뷰트(#[derive(...)] 등)와 매크로 호출을 함수 호출로 오인하지 않습니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.6',
    date: '2026-06-20',
    title: 'Go 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'Go 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 리시버 메서드(func (s *Server) Handle())를 정확히 인식하고, 중첩 클로저 안의 호출을 가장 가까운 함수에 귀속하며, 주석·문자열·import 경로 속 식별자를 호출로 오인하지 않습니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.5',
    date: '2026-06-19',
    title: '데드코드 경고 오탐 수정 — 프로젝트 하위 폴더 분석',
    type: 'fix',
    items: [
      { category: '경고 엔진', desc: '프로젝트의 하위 폴더(예: frontend/src)를 직접 분석할 때 components/·pages/ 등 디렉터리 제외 규칙이 경로 앞 슬래시 누락으로 빗나가, React 모듈이 대거 미사용(DEAD_CODE)으로 잘못 잡히던 문제를 수정했습니다. 디렉터리 매칭을 세그먼트 단위로 정규화해 분석 루트와 무관하게 동작합니다.' },
    ],
  },
  {
    version: 'v0.89.4',
    date: '2026-06-19',
    title: 'JavaScript 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'JavaScript 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다(TypeScript 엔진 재사용). 정규식이 못 잡던 클래스 메서드와, CommonJS의 핵심 패턴인 멤버 대입 함수(exports.x = function(){}, Proto.prototype.y = function(){})를 인식합니다. .jsx(JSX)는 전용 그래머로 파싱하고, native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.3',
    date: '2026-06-19',
    title: 'TypeScript 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'TypeScript 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 정규식이 전혀 못 잡던 클래스 메서드(`name(){}`)를 인식하고, 화살표 함수·중첩 함수 안의 호출을 정확한 함수에 귀속하며, 주석·문자열 속 식별자를 호출로 오인하지 않습니다. .tsx(JSX)는 전용 그래머로 파싱합니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.2',
    date: '2026-06-19',
    title: 'Python 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'fix',
    items: [
      { category: '분석 엔진', desc: 'Python 함수·호출 추출을 정규식에서 tree-sitter AST로 교체했습니다. 중첩 함수·메서드 안의 호출을 가장 가까운 함수에 정확히 귀속하고(정규식은 def 위치로 본문을 갈라 오귀속), 주석·docstring·문자열 리터럴 속 식별자를 호출로 오인하지 않습니다. native 로드 실패 시 정규식으로 자동 폴백합니다.' },
    ],
  },
  {
    version: 'v0.89.0',
    date: '2026-06-19',
    title: '아키텍처 의도 선언 — 규칙 위반 시 자동 경고',
    type: 'feature',
    items: [
      { category: '경고 엔진', desc: '그래프 사이드바의 "아키텍처 의도" 패널에서 모듈(경로 글로브)과 의존 금지 규칙(FORBID)을 선언하면, 실제 IMPORT 엣지가 규칙에 어긋날 때 INTENT_DRIFT 경고가 즉시 발생합니다. PR 코멘트·그래프 경고 패널 모두에 반영됩니다.' },
      { category: '아키텍처', desc: '선언된 의도는 서버에 저장되어 재분석 후에도 유지됩니다. 예: "domain 모듈이 infrastructure 모듈에 의존하면 안 된다"는 DDD 원칙을 코드로 박제하고, 위반이 생기면 PR 병합 전에 경고로 알아챌 수 있습니다.' },
    ],
  },
  {
    version: 'v0.88.0',
    date: '2026-06-19',
    title: 'Java 분석 정확도 — AST(tree-sitter) 엔진으로 교체',
    type: 'feature',
    items: [
      { category: '분석 엔진', desc: 'Java 함수·호출 추출을 정규식에서 tree-sitter AST 파서로 교체했습니다. 접근 제어자가 없는 메서드, 인터페이스 추상 메서드, 그리고 @PathVariable("id")처럼 파라미터에 괄호가 포함돼 정규식이 놓치던 컨트롤러 핸들러까지 정확히 인식합니다. record 타입명을 함수로 오탐하거나, 주석·문자열 안의 식별자를 호출로 오인하던 문제도 파서 차원에서 사라졌습니다. 실측에서 Spring PetClinic 기준 함수 노드가 149→224개로 늘며 그래프가 더 완전해졌고, 경고 오탐은 늘지 않았습니다(비-Java 언어는 기존 분석 그대로 유지).' },
    ],
  },
  {
    version: 'v0.87.1',
    date: '2026-06-18',
    title: '프로젝트 — 공개 프로젝트는 개수 제한 없이 생성',
    type: 'fix',
    items: [
      { category: '프로젝트', desc: 'Free 플랜의 프로젝트 개수 제한이 공개·비공개를 합친 총개수로 적용돼, 누구나 볼 수 있게 공유한 공개 프로젝트까지 3개 한도에 묶이던 문제를 수정 — 이제 제한은 비공개 프로젝트에만 적용되고 공개 프로젝트는 몇 개든 만들 수 있습니다. 비공개 한도가 찼더라도 기존 프로젝트를 공개로 전환하면 새 슬롯이 생깁니다.' },
    ],
  },
  {
    version: 'v0.87.0',
    date: '2026-06-18',
    title: 'MCP 서버 — AI 에이전트가 공개 그래프를 직접 조회',
    type: 'feature',
    items: [
      { category: 'MCP', desc: 'Claude Desktop·Cursor 등 MCP 클라이언트에 Codeprint를 연결하면 AI가 공개 그래프의 구조·경고·이웃 노드를 자연어로 물어볼 수 있습니다. POST /mcp/rpc 엔드포인트로 JSON-RPC 2.0을 구현했으며, search_public_projects·get_graph_overview·get_warnings·find_nodes·get_node_neighbors 5가지 툴을 제공합니다. 경고에는 v0.86.16에서 추가한 수정 가이드도 포함됩니다.' },
    ],
  },
  {
    version: 'v0.86.16',
    date: '2026-06-17',
    title: '경고 — 각 경고에 구체적인 수정 가이드 추가',
    type: 'fix',
    items: [
      { category: '경고', desc: '순환 의존·DB 레이어 우회·DDD 컨텍스트 경계 위반 등 8가지 경고 타입에 "이렇게 고치세요" 안내가 추가됐습니다. 예를 들어 CYCLIC_IMPORT 경고는 이제 "공유 로직을 shared/ 모듈로 분리하거나 포트-어댑터 패턴으로 역전하세요"처럼 구체적인 리팩터링 방향을 함께 보여줍니다.' },
    ],
  },
  {
    version: 'v0.86.15',
    date: '2026-06-17',
    title: 'PR 리뷰 — 이 PR이 변경한 파일의 경고만 게시(노이즈 감소)',
    type: 'fix',
    items: [
      { category: 'PR 리뷰', desc: '지금까지 PR 리뷰는 브랜치 전체를 분석해 레포 곳곳의 구조 경고를 모두 게시했는데, 정작 이 PR이 건드리지 않은 파일의 기존 경고까지 섞여 핵심이 묻혔습니다. 이제 PR이 실제로 변경한 파일에 속한 경고만 코멘트에 올리고, 변경 외 파일의 경고는 몇 개를 제외했는지만 안내합니다. (변경 파일 목록 조회가 실패하면 안전하게 기존처럼 전체를 게시합니다.)' },
    ],
  },
  {
    version: 'v0.86.14',
    date: '2026-06-17',
    title: 'PR 리뷰 — 각 경고가 어느 파일에서 발생했는지 코멘트에 표시',
    type: 'fix',
    items: [
      { category: 'PR 리뷰', desc: 'GitHub PR에 자동 게시되는 구조 분석 코멘트가 경고 종류와 설명만 보여주고 정작 "어느 파일"인지 알려주지 않던 점을 개선 — 이제 각 경고 옆에 발생 파일 경로가 함께 표시돼, 리뷰어가 코멘트만 보고 바로 해당 파일을 찾아갈 수 있습니다' },
    ],
  },
  {
    version: 'v0.86.13',
    date: '2026-06-17',
    title: '분석 안정성 — 특수 파일·대형 의존 폴더가 있어도 분석이 멈추지 않음',
    type: 'fix',
    items: [
      { category: '분석', desc: '저장소에 읽을 수 없는 파일(권한 제한·끊긴 심볼릭 링크 등)이 하나라도 있으면 분석 전체가 실패하던 문제를 수정 — 이제 문제 있는 파일만 건너뛰고 나머지는 정상 분석합니다. 또한 node_modules·.git 같은 대형 폴더를 아예 순회하지 않고 건너뛰도록 바꿔(이전에는 전부 훑은 뒤 제외) 대형 프로젝트 분석이 더 빠르고 안정적입니다' },
    ],
  },
  {
    version: 'v0.86.12',
    date: '2026-06-17',
    title: '경고 정확도 — 콜백으로 넘긴 함수를 데드 코드로 오인하지 않음',
    type: 'fix',
    items: [
      { category: '경고', desc: '함수를 호출(name())하지 않고 값으로 넘기는 콜백·고차함수 패턴(예: Go register(handler), JS arr.map(fn), Python sorted(x, key=fn))에서, 넘겨진 함수가 "데드 코드(아무도 호출 안 함)"로 잘못 표시되던 오탐을 제거 — 값으로 참조된 함수를 사용 중으로 인식합니다. 실측에서 Go(gin) 저장소의 마지막 남은 데드 코드 오탐이 사라져 무경고가 됐고, 그래프에 가짜 연결선은 추가하지 않습니다' },
    ],
  },
  {
    version: 'v0.86.11',
    date: '2026-06-17',
    title: '분석 결과 첫인상 — 발견된 잠재 문제를 결과 카드로 바로 안내',
    type: 'fix',
    items: [
      { category: '분석', desc: '분석 완료 직후 뜨던 요약 토스트가 파일·함수 수 같은 구조 정보만 보여줘 정작 핵심인 "발견된 잠재 문제"가 묻히던 점을 개선 — 이제 결과 카드에 잠재 문제 개수와 심각도(HIGH/MEDIUM/LOW) 배지가 함께 표시되고, "경고 보기" 버튼을 누르면 좌측 경고 패널로 바로 이동·강조됩니다. 문제가 없으면 "구조 문제가 발견되지 않았습니다"로 명확히 안내합니다' },
    ],
  },
  {
    version: 'v0.86.10',
    date: '2026-06-17',
    title: '분석 정확도 — 주석 속 코드를 실제 코드로 오인하지 않음',
    type: 'fix',
    items: [
      { category: '분석', desc: '주석으로 처리된 함수 호출·import·new 인스턴스화가 실제 코드로 잘못 잡혀 그래프에 가짜 연결선이 생기던 문제를 수정 — 분석 전에 주석 본문을 걸러낸 뒤 코드만 인식합니다("path // not a comment"처럼 문자열 안의 // 는 그대로 보존). 실측에서 Go 저장소는 가짜 연결선 134개, JS 저장소는 39개가 사라졌습니다' },
      { category: '분석', desc: '파라미터 목록 주석에 괄호가 들어간 경우(예: "// 활성 사용자(DAU)") 그 함수가 아예 검출되지 않던 부수적 버그도 함께 해소 — 가려져 있던 실제 함수들이 그래프에 정상 표시됩니다' },
    ],
  },
  {
    version: 'v0.86.9',
    date: '2026-06-17',
    title: '경고 정확도 — 데드 코드 신뢰도 게이트 재조정',
    type: 'fix',
    items: [
      { category: '경고', desc: '호출 관계를 정적으로 다 추적하기 어려운 라이브러리(특히 Python·JS의 동적 디스패치·duck typing)에서 멀쩡한 함수가 무더기로 "데드 코드"로 잘못 표시되던 문제를 개선 — 미호출 비율이 일정 수준(4%)을 넘고 그 개수가 10건 이상이면 개별 경고 대신 "호출 추출 신뢰도가 낮다"는 단일 안내로 묶어 보여줍니다. 진짜 데드 코드 몇 개뿐인 정상 프로젝트는 그대로 개별 표시됩니다' },
    ],
  },
  {
    version: 'v0.86.8',
    date: '2026-06-16',
    title: '경고 정확도 — 루트 레벨 테스트 폴더의 오탐 제거',
    type: 'fix',
    items: [
      { category: '경고', desc: '저장소 최상단에 있는 tests/·test/ 폴더의 테스트 헬퍼 함수가 "데드 코드"·"과도한 의존(High Fan-Out)"으로 잘못 표시되던 문제를 수정 — 테스트 코드 제외 규칙이 하위 폴더(src/test/)만 인식하고 최상단 tests/는 놓치던 버그였습니다. Python·Go·JS 등 최상단 테스트 폴더를 쓰는 저장소에서 거짓 경고가 줄어듭니다' },
    ],
  },
  {
    version: 'v0.86.7',
    date: '2026-06-16',
    title: 'PR 코멘트 개선 — 참고용(LOW) 경고는 자동 생략',
    type: 'fix',
    items: [
      { category: 'PR 리뷰', desc: 'PR 코멘트에 LOW 등급(참고용) 경고가 포함되어 중요한 경고가 묻히던 문제를 개선 — 이제 HIGH·MEDIUM 경고만 코멘트에 게시되고, 생략된 LOW 경고 수는 코멘트 하단에 별도 안내됩니다. PR 리뷰 완료 패널에도 "(LOW N개 생략)" 카운트가 표시됩니다' },
    ],
  },
  {
    version: 'v0.86.6',
    date: '2026-06-16',
    title: '대형 그래프 렌더링 성능 — 화면에 보이는 노드만 그리기',
    type: 'fix',
    items: [
      { category: '그래프', desc: '파일·함수가 수백 개인 대형 레포에서 그래프가 멈추던(freeze) 문제를 개선 — 이제 현재 화면(뷰포트) 안에 들어온 노드만 실제로 그리고, 화면 밖 노드는 스크롤·확대해서 들어올 때 그립니다. 그래프 페이지·공유 그래프·버전 비교·커뮤니티 그래프 모두 적용' },
    ],
  },
  {
    version: 'v0.86.5',
    date: '2026-06-16',
    title: '경고 정확도 개선 — 테스트 함수의 과도한 의존 오탐 제거',
    type: 'fix',
    items: [
      { category: '경고', desc: '테스트 함수(예: Go의 Test로 시작하는 함수, *_test.go·*Test.java 파일)에 "과도한 의존(High Fan-Out)" 경고가 잘못 뜨던 오탐을 제거 — 테스트는 준비(setup)와 검증(assert)을 위해 자연히 여러 함수를 호출하므로 단일 책임 위반이 아님. 실제 프로덕션 코드의 고팬아웃 함수만 경고로 남김' },
    ],
  },
  {
    version: 'v0.86.4',
    date: '2026-06-16',
    title: 'Go 분석 정확도 개선 — 데드 코드·순환 의존 오탐 제거',
    type: 'fix',
    items: [
      { category: '분석', desc: 'Go에서 리시버 변수명을 생략한 메서드(예: func (jsonBinding) Bind())를 함수로 인식하지 못해, 그 안에서만 호출되는 함수를 "데드 코드(아무도 호출 안 함)"로 잘못 표시하던 오탐을 제거 — 이제 타입만 적힌 리시버 메서드도 정상 분석' },
      { category: '경고', desc: 'Go의 같은 패키지(같은 폴더) 파일에 거짓 "순환 의존" 경고가 뜨던 문제를 수정 — 코드 안의 평범한 문자열("uri", "query" 등)을 import 경로로 오인하던 것을 바로잡아, 실제 import 문만 의존 관계로 인식' },
      { category: '경고', desc: '인터페이스·리시버 메서드처럼 같은 이름의 구현이 여러 개인 함수가 호출되고 있으면, 분석이 한 구현에만 연결하더라도 나머지를 데드 코드로 오인하지 않도록 보강' },
    ],
  },
  {
    version: 'v0.86.3',
    date: '2026-06-16',
    title: '분석 정확도 개선 — 주석 속 @Async 텍스트 오인 제거',
    type: 'fix',
    items: [
      { category: '분석', desc: '주석이나 문자열 안에 적힌 "@Async" 글자를 실제 어노테이션으로 오인해 엉뚱한 메서드를 비동기로 표시하던 문제를 수정 — 이제 줄 맨 앞에 붙은 진짜 @Async 어노테이션만 인식. 그 결과 발생하던 잘못된 "@Async 자기 호출" 경고도 함께 사라짐' },
    ],
  },
  {
    version: 'v0.86.2',
    date: '2026-06-16',
    title: '경고 정확도 개선 — 프론트엔드 async 함수 오탐 제거',
    type: 'fix',
    items: [
      { category: '경고', desc: '"@Async 자기 호출" 경고가 TypeScript/JavaScript·Python의 async 함수에까지 잘못 붙던 오탐을 제거 — 이 경고는 Spring의 @Async 프록시 우회(같은 클래스 안에서 직접 호출하면 비동기가 무시되는 자바 특유 현상)에만 해당하고, JS·파이썬의 async에는 그런 프록시가 없어 정상 동작이기 때문. 이제 Java/Kotlin 코드에서만 이 경고를 표시' },
    ],
  },
  {
    version: 'v0.86.1',
    date: '2026-06-16',
    title: '경고 정확도 개선 — 같은 파일 안에서만 쓰이는 함수 오탐 제거',
    type: 'fix',
    items: [
      { category: '경고', desc: '같은 파일 안의 다른 함수만 호출하는 함수를 "데드 코드(아무도 호출 안 함)"로 잘못 표시하던 오탐을 제거 — 파일 내부 호출도 연결로 인식하도록 분석 엔진을 보강. 예: 서명 검증 헬퍼처럼 한 파일 안에서만 쓰이는 함수가 더 이상 미사용으로 잡히지 않음' },
      { category: '경고', desc: '"@Async 자기 호출"(같은 클래스 안에서 비동기 메서드를 직접 호출해 비동기가 무시되는 패턴) 경고가 실제로 작동하도록 복구 — 그동안 파일 내부 호출 연결이 없어 감지되지 않던 문제 해결. "과도한 의존(High Fan-Out)" 경고는 기존 기준 그대로 유지' },
    ],
  },
  {
    version: 'v0.86.0',
    date: '2026-06-16',
    title: '도그푸딩 페이지 — Codeprint가 스스로 잡은 결함 공개',
    type: 'feature',
    items: [
      { category: '페이지', desc: '"도그푸딩" 페이지 신설 — Codeprint 분석 엔진이 자기 코드의 실제 구조 결함을 스스로 적발한 사례를 공개. 컴파일·테스트·코드 리뷰를 통과한 코드에서 Codeprint만 잡은 결함만 기록(오탐 제외). 대표 사례는 PR 리뷰 기능이 자기 자신의 DDD 위반을 잡은 케이스' },
      { category: '헤더', desc: '상단 헤더의 작동 방식·패치노트·발전사·도그푸딩 4개를 "서비스" 메뉴 하나로 묶음 — 마우스를 올리면 아래로 펼쳐지는 드롭다운. 헤더가 길어지던 것을 정리' },
    ],
  },
  {
    version: 'v0.85.1',
    date: '2026-06-16',
    title: '도메인 뷰 레이아웃 개편 — DB 중앙 + 도메인 그리드 배치',
    type: 'fix',
    items: [
      { category: '그래프', desc: '도메인 뷰가 세로 1열로 길어지던 것을 개편 — 데이터베이스를 화면 중앙에 두고 도메인들을 그 주위로 격자 배치. 프로젝트가 커져 도메인이 많아져도 16:9 화면을 가로로 넓게 활용해 한눈에 보기 쉬움' },
    ],
  },
  {
    version: 'v0.85.0',
    date: '2026-06-16',
    title: '스케치 모드 (베타) — 그래프 위에 설계 노드를 직접 그리기',
    type: 'feature',
    items: [
      { category: '그래프', desc: '좌측 "✏️ 스케치(베타)" 섹션에서 스케치 모드를 켜면 기존 구조 그래프 위에 설계용 노드를 자유롭게 추가·이동·삭제 가능 — 점선 보라색 "설계" 배지로 실제 코드 노드와 구분. 아직 없는 구조를 그래프 위에 미리 그려보는 설계 스케치 용도' },
      { category: '그래프', desc: '스케치 노드는 프로젝트별로 저장되어 재분석해도 유지 (현재 베타: 사용 중인 브라우저에만 저장 — 백엔드 영구 저장·연결선은 후속 예정)' },
    ],
  },
  {
    version: 'v0.84.0',
    date: '2026-06-15',
    title: 'PR 리뷰 자동화 — PR을 열면 구조 경고가 알아서 달림',
    type: 'feature',
    items: [
      { category: 'PR 연동', desc: 'GitHub 레포에 webhook을 한 번 연결해 두면 PR을 새로 열거나 커밋을 푸시할 때마다 해당 PR 브랜치를 자동 분석해 구조 경고를 PR 코멘트로 게시 — 그동안 버튼을 직접 눌러야 했던 PR 리뷰가 머지 전에 자동으로 작동. 서명(HMAC) 검증으로 정상 webhook만 처리' },
      { category: 'PR 연동', desc: '숨긴(suppress) 경고는 PR 코멘트에서도 제외 — 그래프에서 한 번 숨긴 경고가 PR마다 다시 달리지 않음' },
    ],
  },
  {
    version: 'v0.83.0',
    date: '2026-06-15',
    title: 'PR 리뷰 버튼 — 대시보드에서 바로 PR 분석 실행',
    type: 'feature',
    items: [
      { category: 'PR 연동', desc: '프로젝트 카드에 "PR 리뷰" 버튼 추가 — PR 번호만 입력하면 해당 PR 브랜치를 분석해 구조 경고를 GitHub PR 코멘트로 게시하고, 게시된 코멘트로 바로 이동하는 링크를 보여줌. 그동안 API로만 가능하던 PR 리뷰를 화면에서 직접 실행' },
    ],
  },
  {
    version: 'v0.82.0',
    date: '2026-06-15',
    title: '그래프 버전 보관 정책 — 최근 10개 자동 보관 + 원하는 버전 고정',
    type: 'feature',
    items: [
      { category: '그래프', desc: '프로젝트당 그래프 버전을 최근 10개만 자동 보관하고 오래된 버전은 새 분석 시 자동 정리 — 무제한 누적으로 인한 저장공간 낭비 해소. 좌측 "버전 기록"의 ? 아이콘에서 보관 정책 확인 가능' },
      { category: '그래프', desc: '계속 보고 싶은 버전을 최대 5개 슬롯에 📌 고정 — 고정한 버전은 자동 정리 대상에서 제외. 5개가 모두 차 있으면 어느 슬롯을 덮어쓸지 직접 선택. 고정 슬롯은 버전 기록 상단에서 한눈에 보고 클릭해 바로 로드' },
    ],
  },
  {
    version: 'v0.81.1',
    date: '2026-06-15',
    title: '그래프 사이드바 UX 개선 — 현재 버전 표시·우측 사이드바 기본 숨김',
    type: 'fix',
    items: [
      { category: '그래프', desc: '좌측 사이드바 "버전 기록"에 지금 보고 있는 버전(브랜치 + 생성일자)을 상시 표시 — 여러 버전을 오갈 때 현재 어느 시점을 보는지 한눈에 확인' },
      { category: '그래프', desc: '우측 상세 사이드바를 기본 숨김으로 전환 — 도메인/파일/함수를 클릭할 때만 나타나 그래프 영역을 더 넓게 사용. 화면 우측 끝 화살표 버튼으로 언제든 펼침' },
      { category: '그래프', desc: '좌측 사이드바 접기 버튼을 더 크고 또렷하게 개선, 실시간 협업(👥) 버튼을 헤더 우측에서 상단 바 팀채팅 옆으로 이동해 로그아웃 버튼과 겹치지 않도록 정리' },
    ],
  },
  {
    version: 'v0.81.0',
    date: '2026-06-15',
    title: 'GitHub PR 리뷰 — PR 브랜치 분석 결과를 코멘트로 게시',
    type: 'feature',
    items: [
      { category: 'PR 연동', desc: 'PR 번호를 지정하면 해당 PR의 소스 브랜치를 분석해 구조 경고(순환 의존·끊긴 인터페이스·DDD 위반 등)를 severity별로 정리한 코멘트를 GitHub PR에 자동 게시 — 머지 전에 구조 문제를 PR에서 바로 확인. 프로젝트 소유자만 실행 가능' },
    ],
  },
  {
    version: 'v0.80.0',
    date: '2026-06-15',
    title: 'NestJS 분석 지원 — 컨트롤러 데코레이터 API 인식',
    type: 'feature',
    items: [
      { category: '분석', desc: 'NestJS @Controller + @Get/@Post 데코레이터를 API 엔드포인트로 인식 — 클래스 경로 prefix와 메서드 경로를 합성. 그동안 TypeScript/JavaScript는 Express만 잡혀 NestJS 프로젝트는 API 노드가 비어 있던 문제 해소' },
    ],
  },
  {
    version: 'v0.79.0',
    date: '2026-06-15',
    title: 'Django 분석 지원 — ORM 모델·URL 라우팅 인식',
    type: 'feature',
    items: [
      { category: '분석', desc: 'Django ORM(class X(models.Model)) 모델을 DB 테이블로 인식 — 추상 모델 제외, Meta.db_table 지정 시 그 이름 사용. 그동안 Python은 SQLAlchemy만 잡혀 Django 프로젝트는 DB 노드가 비어 있던 문제 해소' },
      { category: '분석', desc: 'Django urls.py의 path()/re_path() URL 라우팅을 API 엔드포인트로 인식 — Django 프로젝트도 API 노드가 그래프에 표시됨' },
    ],
  },
  {
    version: 'v0.78.1',
    date: '2026-06-15',
    title: '일일 다이제스트 활성 사용자(DAU) 집계 정밀화',
    type: 'fix',
    items: [
      { category: '관리자', desc: '다이제스트의 활성 사용자 수를 로그인 토큰 발급 기준(재로그인 시에만 집계)에서 실제 요청 활동 기준으로 변경 — 토큰이 유효한 채 하루 종일 사용하는 사용자를 누락하던 구조적 과소 집계 해소' },
    ],
  },
  {
    version: 'v0.78.0',
    date: '2026-06-15',
    title: '관리자 문의 처리 추적 — 미처리 문의를 읽고 완료 표시',
    type: 'feature',
    items: [
      { category: '관리자', desc: '관리자 대시보드에 사용자 문의 목록 추가 — 그동안 신규 건수만 보이고 내용은 UI에서 볼 수 없던 문의를 직접 읽고 "처리 완료/미처리"로 표시 가능. 기본은 미처리만 표시, 토글로 처리 완료 건까지 확인' },
      { category: '관리자', desc: '일일 다이제스트에 "미처리 문의(현재)" 게이지 추가 — 미처리 문의가 10건 이상 쌓이면 이상 신호로 경고해 응대 지연을 조기에 인지' },
    ],
  },
  {
    version: 'v0.77.1',
    date: '2026-06-15',
    title: 'raw SQL DB 감지 정밀화 — 산문 문자열 오검출 차단',
    type: 'fix',
    items: [
      { category: '분석', desc: 'raw SQL로 DB 테이블을 추출할 때 "Please select your name from the list" 같은 일반 문자열을 테이블 접근으로 오검출하던 문제 수정 — SQL 동사로 시작하고(앵커) WHERE·VALUES·*·플레이스홀더(?·%s·:param) 등 실제 SQL 구조 마커가 있는 리터럴만 인정' },
      { category: '분석', desc: '쿼리 안의 문자열 값에 든 다른 SQL 동사(예: WHERE action = \'delete from cache\')를 별도 테이블로 잡지 않도록 선두 동사 전용 추출로 변경 — 결과적으로 ORM 미사용 프로젝트 그래프에 가짜 DB_TABLE 노드가 줄어듦' },
    ],
  },
  {
    version: 'v0.77.0',
    date: '2026-06-15',
    title: '관리자 일일 다이제스트 — 매일 운영 지표 자동 요약·알림',
    type: 'feature',
    items: [
      { category: '관리자', desc: '매일 09:00(KST) 전일 운영 지표(신규 가입·활성 사용자·분석 수·분석 실패율·결제·신규 문의)를 자동 집계해 관리자에게 인앱 알림 + 웹푸시로 발송 — 외부 메일 인프라 없이 기존 알림 채널 재사용' },
      { category: '관리자', desc: '이상 신호 자동 감지 — 분석 실패율 20% 초과, 활성 사용자·분석 수 전일 대비 ±50% 급변 시 경고 표시. 관리자 대시보드에 다이제스트 패널 + "지금 생성" 버튼 추가' },
      { category: '백엔드', desc: 'GET /mcp/admin/stats — AI 에이전트(Cowork/Claude Code)가 서비스 운영 지표를 MCP로 조회 가능(ADMIN 인증). 일별 스냅샷 저장으로 전일 대비 추세 비교' },
    ],
  },
  {
    version: 'v0.76.1',
    date: '2026-06-15',
    title: '데드 코드 신뢰도 게이트 — 호출 추출이 약한 레포에서 경고 폭주 억제',
    type: 'fix',
    items: [
      { category: '경고', desc: '미호출 함수 비율이 15%를 넘는 레포(호출 추출이 약한 동적 언어·패턴)에서는 개별 데드 코드 경고 수백 건 대신 신뢰도 안내 1건으로 치환 — "미호출 함수 비율 N% — 호출 추출 신뢰도가 낮아 개별 경고를 생략" 형태로 이유를 명시(침묵 억제 아님)' },
      { category: '경고', desc: '정상 레포(미호출 비율 ≤1%)는 영향 없음 — 유명 오픈소스 실측(Java 0.1~1% vs Python 22.6%)으로 임계값 보정. 함수 30개 미만 소형 그래프는 게이트 미적용' },
    ],
  },
  {
    version: 'v0.76.0',
    date: '2026-06-14',
    title: '관리자 플랜 변경 — 감사 로그가 남는 인가된 액션',
    type: 'feature',
    items: [
      { category: '관리자', desc: '관리자 대시보드에서 사용자 플랜을 FREE↔PRO로 변경 가능 — 변경 시 사유 입력 필수, 누가·언제·무엇을·왜 바꿨는지 감사 로그로 기록' },
      { category: '관리자', desc: '플랜 변경 감사 로그 목록 표시 — 모든 변경이 추적되는 인가된 관리 기능(결제 우회 백도어가 아님)' },
    ],
  },
  {
    version: 'v0.75.2',
    date: '2026-06-14',
    title: '데드 코드 경고 오탐 대폭 감소 — 프레임워크 핸들러·던더·테스트 제외',
    type: 'fix',
    items: [
      { category: '경고', desc: '프레임워크가 호출하는 메서드(@GetMapping·@Bean·@InitBinder·@Override 등 Spring 핸들러, NestJS/FastAPI 데코레이터)를 "데드 코드"로 오탐하던 문제 수정 — 코드에 직접 호출부가 없어도 런타임에 호출되므로 제외' },
      { category: '경고', desc: 'Python 던더 메서드(__init__·__iter__ 등)와 테스트 코드(테스트 디렉터리·*Tests.java·*.spec·pytest test_* 함수)를 데드 코드 후보에서 제외' },
      { category: '경고', desc: '유명 오픈소스 레포 분석으로 측정한 오탐 패턴을 반영 — 표준 Spring 앱에서 데드 코드 오탐이 사실상 사라짐' },
    ],
  },
  {
    version: 'v0.75.1',
    date: '2026-06-14',
    title: '랜딩페이지 분석 엔진 표기 정정 — "Tree-sitter" → 정규식',
    type: 'fix',
    items: [
      { category: '프론트', desc: '랜딩페이지가 분석 엔진을 "Tree-sitter 기반"으로 잘못 표기하던 것을 "정규식 기반"으로 정정 — 실제 엔진은 언어별 정규식 정적 분석. 히어로 배지·사용법 설명·그래프 목업 노드 3곳 수정' },
    ],
  },
  {
    version: 'v0.75.0',
    date: '2026-06-14',
    title: '기능 발전사 페이지 — 주요 기능이 버전을 거쳐 자란 과정',
    type: 'feature',
    items: [
      { category: '프론트', desc: '헤더에 "발전사"(/evolution) 추가 — 패치노트가 버전 축이라면 발전사는 기능 축. 경고엔진·분석엔진·그래프시각화 세 주요 기능이 어떤 마일스톤을 거쳐 자랐는지를 서사와 타임라인으로 정리' },
      { category: '프론트', desc: '전체 릴리스를 나열하지 않고 각 기능의 핵심 마일스톤 버전만 선별해 표시 — 패치노트(/changelog)의 RELEASES를 단일 소스로 재사용' },
    ],
  },
  {
    version: 'v0.74.0',
    date: '2026-06-14',
    title: '"작동 방식" 페이지 — 분석 엔진 동작 원리·경고 기준·한계 공개',
    type: 'feature',
    items: [
      { category: '프론트', desc: '헤더에 "작동 방식"(/how-it-works) 추가 — 그래프를 만드는 방법(정규식 정적 분석), 감지하는 위험 패턴 10종(의미·심각도·알려진 오탐), 경고 숨기기 안내를 한 페이지에 정리' },
      { category: '경고', desc: '각 경고가 휴리스틱이며 오탐이 날 수 있는 이유(동적 호출·리플렉션·메서드 레퍼런스 추적 한계)를 명시 — 숨기기 기능의 맥락 제공' },
    ],
  },
  {
    version: 'v0.73.0',
    date: '2026-06-14',
    title: '경고 숨기기 — 오탐·의도된 패턴 경고를 프로젝트 단위로 정리',
    type: 'feature',
    items: [
      { category: '경고', desc: '경고 패널에서 각 경고를 ✕ 버튼으로 숨길 수 있음 — 오탐이거나 의도된 패턴(예: 차후 기능용 미사용 함수, 오케스트레이터의 높은 호출 수)을 프로젝트별로 정리. 재분석해도 숨김 상태 유지' },
      { category: '경고', desc: '숨긴 경고는 패널 하단 "숨긴 경고" 목록에서 언제든 복원 가능' },
    ],
  },
  {
    version: 'v0.72.4',
    date: '2026-06-14',
    title: '데드 코드 경고 오탐 감소 — JPA 컨버터·인터페이스 메서드 제외',
    type: 'fix',
    items: [
      { category: '경고', desc: 'JPA 암호화 컨버터 메서드(convertToDatabaseColumn 등)가 "데드 코드"로 오탐되던 문제 수정 — Hibernate가 내부적으로 호출하는 메서드라 호출 흔적이 코드에 없을 뿐, 실제로는 사용 중' },
      { category: '경고', desc: 'Repository·Port 인터페이스 선언 메서드가 데드 코드로 오탐되던 문제 수정 — 구현체를 인터페이스를 통해 호출하면 선언부에 직접 연결이 안 잡히던 한계 보정. 같은 이름의 호출이 있으면 사용 중으로 인식 (한 번도 호출 안 되는 메서드는 계속 감지)' },
    ],
  },
  {
    version: 'v0.72.3',
    date: '2026-06-14',
    title: '도메인 뷰 조작성 개선 — 사이드바로 탭 통합·DB 인접 배치·색상 통일·튐 수정',
    type: 'fix',
    items: [
      { category: '그래프', desc: '좌측 사이드바에서 도메인을 클릭하면 해당 도메인만 보기 + 흐름 목록 + 화면 채우는 확대가 한 번에 동작 — 상단 도메인 탭바를 제거하고 기능을 좌측 사이드바로 통합' },
      { category: '그래프', desc: '도메인을 누르면 그 도메인이 화면을 가득 채우도록 확대 — 도메인 전환 시 확대 애니메이션이 중간 배율에서 멈추던 버그도 함께 수정' },
      { category: '그래프', desc: '도메인을 필터링해서 볼 때 관련 DB 테이블을 도메인 바로 아래에 붙여 표시 + 연결선 표시 — 기존엔 DB가 화면 밖 멀리 떨어져 있었음' },
      { category: '그래프', desc: '도메인별 색상을 그래프·좌측·우측 사이드바에서 모두 일치 — AI 도메인이 사이드바에선 핑크, 그래프에선 파랑이던 불일치 수정. 누락됐던 도메인도 범례에 모두 표시' },
      { category: '그래프', desc: '노드를 클릭하면 화면이 흐름 시작점(다른 도메인의 진입점)으로 갑자기 튀던 문제 수정 — 클릭 시 화면을 고정하고, 흐름 재생 스텝도 화면에 보이는 노드로만 이동' },
      { category: '그래프', desc: '도메인 뷰에서 파일을 드래그하면 함수가 따라오지 않아 빈 박스가 되고 되돌릴 수 없던 문제 수정 — 파일과 그 안의 함수가 항상 함께 이동' },
    ],
  },
  {
    version: 'v0.72.2',
    date: '2026-06-13',
    title: 'Cross-Domain 호출 경고 오탐 제거 — 정확도 대폭 개선',
    type: 'fix',
    items: [
      { category: '경고', desc: '정규식 분석기가 클래스명 없는 함수 호출(get·add·save·findById·of 등)을 엉뚱한 도메인 파일로 오추적해 "Cross-Domain 직접 호출" 경고가 대량 오탐되던 문제 수정 — getter/setter·JPA·JDK 컬렉션·Optional 메서드를 경고 대상에서 제외' },
      { category: '경고', desc: '동일한 함수명이 여러 도메인에 존재할 때는 호출 대상을 신뢰할 수 없으므로 Cross-Domain 경고에서 제외 — 실제 경계 위반은 import 기반 경고가 계속 잡음' },
      { category: '경고', desc: '테스트 코드(src/test, __tests__, *.test/*.spec)의 호출은 아키텍처 경고 대상에서 제외' },
    ],
  },
  {
    version: 'v0.72.1',
    date: '2026-06-13',
    title: '도메인 뷰 분류 정확도 개선 — 컨트롤러를 올바른 도메인에 귀속',
    type: 'fix',
    items: [
      { category: '그래프', desc: '도메인 뷰에서 interfaces/api 폴더의 컨트롤러들이 모두 "api"라는 가짜 도메인 하나로 묶이던 문제 수정 — GraphController는 graph, UserController는 user처럼 파일명 기준으로 실제 도메인에 배치' },
      { category: '그래프', desc: '프론트엔드 페이지·컴포넌트도 도메인에 귀속 — ShareGraphPage→graph, TeamsPage→team, MessagesPage→message, CreateProjectModal→project 등 (단·복수형, Page/Panel/Modal 등 UI 접미사 인식)' },
      { category: '그래프', desc: '도메인을 식별할 수 없는 파일이 "common"에 과도하게 쌓이던 현상 완화 — 경로/파일명이 실제 존재하는 도메인에 매칭될 때만 적용해 파편화 없이 정확도만 향상' },
      { category: '그래프', desc: '도메인 뷰에서 그룹이 한 줄로만 배치돼 박스가 가로로 끝없이 길어지던 문제 수정 — 그룹을 그리드로 줄바꿈해 화면에 맞는 직사각형으로 표시' },
    ],
  },
  {
    version: 'v0.72.0',
    date: '2026-06-13',
    title: '경고 엔진 일반화 — 비DDD 프로젝트 지원 + severity 구분',
    type: 'feature',
    items: [
      { category: '경고', desc: 'Express/Rails/Django 등 DDD 폴더 구조가 없는 프로젝트에서 DDD 전용 경고(DB 레이어 우회, 컨텍스트 경계 위반 등)가 오탐되던 문제 수정 — DDD 레이어 2종 이상 감지 시에만 활성화' },
      { category: '경고', desc: '모든 경고에 심각도(HIGH/MEDIUM/LOW) 구분 추가 — 경고 패널에서 HIGH부터 순서대로 표시, 각 항목에 색상 배지 표시' },
    ],
  },
  {
    version: 'v0.71.8',
    date: '2026-06-13',
    title: '함수 호출 엣지 정확도 개선',
    type: 'fix',
    items: [
      { category: '분석', desc: '동명 함수가 여러 파일에 있을 때 엣지 중복 제거 키에 callee 파일 정보가 빠져 있던 버그 수정 — 재분석 시 더 정확한 호출 연결선 표시' },
    ],
  },
  {
    version: 'v0.71.7',
    date: '2026-06-13',
    title: 'raw SQL DB 연결 감지',
    type: 'fix',
    items: [
      { category: '분석', desc: 'ORM 없이 raw SQL을 쓰는 프로젝트(Go database/sql, C# Dapper, Java JDBC 등)에서 DB 연결 엣지가 표시되지 않던 문제 수정' },
      { category: '분석', desc: 'SELECT/INSERT/UPDATE/DELETE 구문에서 테이블명을 추출해 DB_TABLE 노드와 DB_READ/WRITE 엣지를 자동 생성' },
    ],
  },
  {
    version: 'v0.71.6',
    date: '2026-06-13',
    title: 'C#/Go 함수 호출 흐름 감지',
    type: 'fix',
    items: [
      { category: '분석', desc: 'C# PascalCase 메서드와 Go 대문자 함수 호출이 인식되지 않아 호출 엣지가 거의 없던 문제 수정' },
      { category: '분석', desc: 'C#/Go 프로젝트에서도 파일 간 함수 호출 흐름이 그래프에 표시됨' },
    ],
  },
  {
    version: 'v0.71.5',
    date: '2026-06-13',
    title: '대형 레포 분석 범위 안내',
    type: 'fix',
    items: [
      { category: '분석', desc: '500개 초과 대형 레포는 일부 파일만 분석됨 — 그래프 상단에 "전체 N개 중 M개 분석" 안내 배너 표시' },
      { category: 'UX', desc: '공유 그래프에도 동일 안내 적용 — 받은 사람이 일부 구조임을 알 수 있음' },
    ],
  },
  {
    version: 'v0.71.4',
    date: '2026-06-13',
    title: '프론트 fetch() API 호출 감지',
    type: 'feature',
    items: [
      { category: '분석', desc: 'axios 없이 표준 fetch()를 쓰는 프론트엔드에서도 프론트→백엔드 API_CALL 연결선 생성' },
      { category: '분석', desc: 'fetch 옵션 객체의 method 인식 (없으면 GET), 템플릿 리터럴 경로 정규화' },
    ],
  },
  {
    version: 'v0.71.3',
    date: '2026-06-13',
    title: 'Prisma 스키마 DB 테이블 감지 복구',
    type: 'fix',
    items: [
      { category: '분석', desc: 'schema.prisma가 분석 대상에서 누락되어 Prisma 프로젝트의 DB 테이블이 표시되지 않던 버그 수정' },
      { category: '분석', desc: 'Prisma 멀티 파일 스키마 지원 — 모든 .prisma 파일의 model 블록을 DB_TABLE 노드로 감지' },
    ],
  },
  {
    version: 'v0.71.2',
    date: '2026-06-13',
    title: '비Spring 백엔드 API 연결선 버그 수정',
    type: 'fix',
    items: [
      { category: '분석', desc: 'Express/FastAPI/Gin/Rails/Laravel/Ktor 백엔드에서 프론트→백엔드 API_CALL 연결선이 생성되지 않던 버그 수정' },
      { category: '분석', desc: 'Express/Rails 스타일 :param 경로 세그먼트 매칭 지원 — /users/:id ↔ /users/${id} 연결' },
    ],
  },
  {
    version: 'v0.71.1',
    date: '2026-06-12',
    title: 'Go 프로젝트 DB 테이블 감지',
    type: 'feature',
    items: [
      { category: '분석', desc: 'Go GORM gorm.Model 임베딩 구조체 DB_TABLE 노드 감지 추가 — TableName() 오버라이드 시 명시적 테이블명 우선' },
      { category: '분석', desc: 'Go Beego ORM orm.RegisterModel 등록 모델 DB_TABLE 노드 감지 추가' },
    ],
  },
  {
    version: 'v0.71.0',
    date: '2026-06-12',
    title: '랜딩 페이지 요금제 섹션 + 분석 엔진 개선',
    type: 'feature',
    items: [
      { category: 'UX', desc: '랜딩 페이지에 Free / Pro / Team 요금제 카드 추가 — 서비스 가치 확인 후 가격 확인 흐름 완성' },
      { category: '분석', desc: 'Swift Core Data NSManagedObject 상속 클래스 DB_TABLE 노드 감지 추가' },
      { category: '분석', desc: 'Kotlin Ktor 라우팅 패턴 get/post/put/delete/patch(\"/path\") API_ENDPOINT 노드 감지 추가' },
    ],
  },
  {
    version: 'v0.70.0',
    date: '2026-06-12',
    title: '랜딩 페이지 URL 입력 — 로그인 후 자동 분석',
    type: 'feature',
    items: [
      { category: 'UX', desc: '랜딩 히어로 섹션에 GitHub URL 입력 폼 추가 — URL 붙여넣기 한 번으로 분석까지 직행' },
      { category: 'UX', desc: '비로그인 상태에서 URL 입력 후 OAuth 로그인 완료 시 CreateProjectModal 자동 오픈' },
    ],
  },
  {
    version: 'v0.69.0',
    date: '2026-06-12',
    title: '계정 탈퇴 기능 + 개인정보처리방침 정확도 개선',
    type: 'feature',
    items: [
      { category: '설정', desc: '설정 페이지에 계정 삭제 섹션 추가 — "삭제" 입력 확인 후 영구 삭제' },
      { category: '법무', desc: '개인정보처리방침 쿠키 항목 수정 — localStorage → HttpOnly Secure 쿠키로 정확도 개선' },
    ],
  },
  {
    version: 'v0.68.0',
    date: '2026-06-12',
    title: '대시보드 첫 방문자 온보딩 가이드',
    type: 'fix',
    items: [
      { category: 'UX', desc: '프로젝트가 없을 때 3단계 사용법 가이드 표시 (URL 입력 → 자동 분석 → 탐색)' },
      { category: 'UX', desc: '공개 레포는 GitHub 토큰 없이 분석 가능함을 명시' },
    ],
  },
  {
    version: 'v0.67.0',
    date: '2026-06-12',
    title: '분석 결과 품질 힌트',
    type: 'fix',
    items: [
      { category: 'UX', desc: '파일 5개 미만 감지 시 토스트에 "레포 루트 URL인지 확인" 안내 추가 (9초 유지)' },
      { category: 'UX', desc: '빈 그래프 오버레이에 지원 언어 목록 + 분석 실패 원인 가이드 표시' },
    ],
  },
  {
    version: 'v0.66.0',
    date: '2026-06-12',
    title: '노드 클릭 시 연결 엣지 자동 표시',
    type: 'fix',
    items: [
      { category: 'UX', desc: '노드 클릭 시 직접 연결된 엣지(1단계)가 토글 상태와 무관하게 자동 표시됨' },
      { category: 'UX', desc: '✕ 또는 흐름 재생 종료 시 엣지 원래 상태로 복원 — 전체 엣지 토글 부작용 없음' },
    ],
  },
  {
    version: 'v0.65.0',
    date: '2026-06-12',
    title: '팀 플랜 — Seat Pool 협업 관리',
    type: 'feature',
    items: [
      { category: '기능', desc: '팀 생성 및 플랜 선택 (Team Starter / Growth / Business)' },
      { category: '기능', desc: '팀 멤버 초대 및 제거 — 소유자는 석수에 포함되지 않음' },
      { category: '기능', desc: '프로젝트별 석수 배분 현황 조회' },
      { category: '과금', desc: 'FREE 팀 협업자 5명 지원, 팀 유료 플랜 가입 시 PRO 기능 자동 포함' },
      { category: '인프라', desc: 'teams / team_members / team_project_allocations 테이블 추가 (V36 마이그레이션)' },
    ],
  },
  {
    version: 'v0.64.0',
    date: '2026-06-12',
    title: 'MCP Context 엔드포인트',
    type: 'feature',
    items: [
      { category: '기능', desc: 'GET /mcp/graphs/{graphId}/context — AI 에이전트가 그래프 데이터를 읽을 수 있는 공개 엔드포인트' },
      { category: '기능', desc: '?summary=true 모드: 대형 프로젝트에서 상위 50개 노드만 반환하여 응답 크기 절감' },
    ],
  },
  {
    version: 'v0.62.0',
    date: '2026-06-12',
    title: 'URL 딥링크 — 노드 직접 공유',
    type: 'feature',
    items: [
      { category: 'UX', desc: '함수 노드 사이드바에 🔗 버튼 추가 — 클릭 시 해당 노드 선택 상태가 포함된 URL을 클립보드에 복사' },
      { category: 'UX', desc: '?nodeId= URL 파라미터로 그래프 접속 시 해당 노드 자동 선택 + 화면 이동' },
    ],
  },
  {
    version: 'v0.61.0',
    date: '2026-06-12',
    title: '커뮤니티 갤러리 탭',
    type: 'feature',
    items: [
      { category: '커뮤니티', desc: '갤러리 탭 추가 — 그래프가 첨부된 게시글만 카드 그리드 형태로 표시' },
      { category: '커뮤니티', desc: '갤러리 탭은 로그인 없이도 조회 가능' },
    ],
  },
  {
    version: 'v0.60.0',
    date: '2026-06-12',
    title: '그래프 빈 상태 + 로딩/에러 UX 개선',
    type: 'fix',
    items: [
      { category: 'UX', desc: '분석 결과가 없을 때 "분석 결과가 없습니다" 안내 오버레이 표시' },
      { category: 'UX', desc: '그래프 로딩 중 스피너 + 텍스트 표시, 에러 시 재시도 버튼 노출' },
    ],
  },
  {
    version: 'v0.58.0',
    date: '2026-06-12',
    title: '다국어 분석 엔진 확장',
    type: 'feature',
    items: [
      { category: '분석', desc: 'Express.js / FastAPI / Flask / Go Gin API 엔드포인트 자동 감지 — URL 경로가 API_ENDPOINT 노드로 표시됨' },
      { category: '분석', desc: 'Rails Routes / Laravel Route:: API 엔드포인트 감지 추가 (Ruby, PHP)' },
      { category: '분석', desc: 'TypeORM @Entity(), SQLAlchemy Base 상속 클래스 DB 테이블 감지 (TypeScript, Python)' },
      { category: '분석', desc: 'Ruby ActiveRecord / PHP Eloquent 모델 DB 테이블 감지 추가' },
      { category: '분석', desc: 'Python async def / TypeScript async function 비동기 함수 감지 — @Async 경고 대상 확장' },
    ],
  },
  {
    version: 'v0.56.0',
    date: '2026-06-11',
    title: '문의하기 피드백 폼',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'feedbacks 테이블(V35) + POST /api/feedback (로그인 필수) + GET /api/feedback/admin (ADMIN 전용)' },
      { category: '프론트', desc: 'ContactPage 이메일 링크 방식 → 실제 폼 UI로 교체 — 카테고리 토글, 글자수 카운터, 제출 완료 화면' },
    ],
  },
  {
    version: 'v0.55.0',
    date: '2026-06-11',
    title: '랜딩페이지 전면 개선 + OG 태그',
    type: 'feature',
    items: [
      { category: '프론트', desc: '랜딩페이지 히어로 섹션 개선 — 그라디언트 제목, 서비스 설명 강화' },
      { category: '프론트', desc: '그래프 UI 목업 SVG 추가 — 도메인 박스·노드·엣지·사이드바·경고 바를 실제 제품과 동일한 스타일로 표현' },
      { category: '프론트', desc: '"어떻게 사용하나요?" 3단계 섹션 추가 (URL 입력 → 자동 분석 → 다이어그램)' },
      { category: '프론트', desc: '기능 소개 카드 3개 → 6개로 확장 (흐름 추적, 경고 감지, AI 분석, 실시간 협업, 내보내기)' },
      { category: '인프라', desc: 'index.html OG 태그 추가 — og:title, og:description, og:image, twitter:card (SNS 공유 미리보기)' },
      { category: '인프라', desc: 'html lang=ko 변경, SEO meta description·keywords·robots·canonical 추가' },
    ],
  },
  {
    version: 'v0.53.0',
    date: '2026-06-11',
    title: '배경이미지 복원',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 배경이미지 기능 복원 — 느린 원인이 배경이미지가 아닌 fetchGraph 무한 루프였음을 확인 후 재도입' },
      { category: '프론트', desc: 'GraphPage "보기" 섹션에 배경이미지 켜기/끄기 토글 (localStorage 상태 유지)' },
      { category: '프론트', desc: 'ShareGraphPage 좌측 사이드바에 오너 배경이미지 토글 복원' },
      { category: '프론트', desc: '설정 페이지 배경이미지 업로드/삭제 UI 복원' },
    ],
  },
  {
    version: 'v0.52.0',
    date: '2026-06-11',
    title: '그래프 탭바 상단 이동 + 배경이미지 제거',
    type: 'feature',
    items: [
      { category: '프론트', desc: '도메인/레이어 탭을 좌측 사이드바에서 캔버스 상단 탭바로 이동 — 더 넓게 보임' },
      { category: '프론트', desc: '그래프 배경이미지 기능 제거 — 그래프 가독성 저하 원인' },
      { category: '프론트', desc: 'GraphPage + ShareGraphPage 동일하게 적용' },
      { category: '성능', desc: 'fetchGraph 무한 재요청 루프 수정 — 페이지 로드 시 /graph 14회 → 1회' },
    ],
  },
  {
    version: 'v0.51.0',
    date: '2026-06-11',
    title: '엣지 온디맨드 렌더링',
    type: 'fix',
    items: [
      { category: '프론트', desc: '전체 탭에서는 엣지 숨김 — 도메인 탭 선택 시에만 해당 범위 엣지 표시' },
      { category: '프론트', desc: '노드 클릭 시 연결된 엣지만 추가 표시 (GraphPage + ShareGraphPage 공통 적용)' },
      { category: '성능', desc: '수천 개 엣지 SVG 렌더링 제거로 대형 프로젝트 렌더 부하 대폭 감소' },
    ],
  },
  {
    version: 'v0.50.0',
    date: '2026-06-11',
    title: '공유 그래프 도메인 탭 분리',
    type: 'feature',
    items: [
      { category: '프론트', desc: '공유 그래프(ShareGraphPage) 좌측 사이드바에 도메인 탭 분리 추가 — GraphPage와 동일한 방식' },
      { category: '프론트', desc: '탭 클릭 시 해당 도메인 노드/엣지만 렌더링하여 대형 프로젝트 공유 시 부하 감소' },
    ],
  },
  {
    version: 'v0.49.0',
    date: '2026-06-11',
    title: '분석 완료 후 그래프 자동 이동',
    type: 'feature',
    items: [
      { category: '프론트', desc: '분석 완료 후 0.8초 딜레이 후 자동으로 그래프 페이지로 이동 — 수동 클릭 없이 바로 결과 확인' },
    ],
  },
  {
    version: 'v0.48.0',
    date: '2026-06-11',
    title: '관리자 JVM 메트릭 패널',
    type: 'infra',
    items: [
      { category: '프론트', desc: '관리자 대시보드에 서버 메트릭 패널 추가 — 힙 사용률·CPU·활성 스레드 30초 갱신' },
      { category: '프론트', desc: '80% 힙/70% CPU/200 스레드 초과 시 빨간 경고 카드 표시' },
    ],
  },
  {
    version: 'v0.47.0',
    date: '2026-06-11',
    title: '그래프 도메인 탭 분리 + 성능 최적화',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 좌측 사이드바에 도메인/레이어 탭 분리 기능 추가 — 탭 클릭 시 해당 영역 노드만 표시하여 렌더링 부하 감소' },
      { category: '프론트', desc: 'useMemo 최적화 — 엣지 토글 섹션, 노드 필터 섹션 메모화로 드래그 중 불필요한 리렌더 감소' },
      { category: '백엔드', desc: 'GraphWarning 감지 결과 Caffeine 10분 캐싱 — detect() 매 요청 실행 제거로 p99 응답시간 개선' },
      { category: '백엔드', desc: 'Spring Boot Actuator metrics/info 엔드포인트 추가 (ADMIN 전용) — JVM 힙·CPU·DB 커넥션 모니터링' },
      { category: '백엔드', desc: 'DDD 위반 수정 — UserSummaryDto 도입으로 메시지 컨텍스트의 User 도메인 직접 참조 제거' },
      { category: '백엔드', desc: 'JSX 분석엔진 개선 — <ComponentName /> 패턴을 FUNCTION_CALL 엣지로 추출, DEAD_CODE 오탐 감소' },
    ],
  },
  {
    version: 'v0.46.0',
    date: '2026-06-11',
    title: '배경이미지 토글',
    type: 'feature',
    items: [
      { category: '프론트', desc: 'GraphPage 좌측 사이드바 "보기" 섹션 추가 — 배경이미지 켜기/끄기 토글, localStorage에 상태 저장' },
      { category: '프론트', desc: 'ShareGraphPage 좌측 사이드바에 오너 배경이미지 토글 추가 — 오너 배경이 있을 때만 표시, 기본 꺼짐' },
      { category: '백엔드', desc: 'GET /api/share/{projectId}/graph 응답에 ownerBgUrl 필드 추가 — S3 presigned URL 7일 만료' },
    ],
  },
  {
    version: 'v0.45.0',
    date: '2026-06-11',
    title: '배경이미지 전체 페이지 적용',
    type: 'feature',
    items: [
      { category: '프론트', desc: '사용자 배경이미지를 랜딩 페이지를 제외한 모든 페이지에 적용 — app-page CSS 클래스 + body.has-bg 조합' },
    ],
  },
  {
    version: 'v0.44.0',
    date: '2026-06-11',
    title: '노드 커스텀 이름/메모 + 커뮤니티 레포 URL',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'nodes 테이블에 user_label, user_note 컬럼 추가 — 노드별 사용자 정의 표시 이름 및 메모 저장' },
      { category: '백엔드', desc: 'PUT /api/graphs/{graphId}/nodes/{nodeId}/annotation 엔드포인트 추가 — 커스텀 이름/메모 저장' },
      { category: '프론트', desc: '그래프 우측 사이드바에 "커스텀 이름 / 메모" 편집 섹션 추가 — 파일/함수 노드 클릭 시 표시' },
      { category: '백엔드', desc: 'posts 테이블에 repo_url 컬럼 추가 — 공개 프로젝트 글 작성 시 레포 URL 자동 포함, 프라이빗은 null' },
      { category: '프론트', desc: '커뮤니티 게시글 카드에 GitHub 레포 링크 표시 — repo_url이 있는 공개 프로젝트 게시글에만 노출' },
      { category: '프론트', desc: '공유 그래프 좌측 사이드바를 GraphPage 스타일로 통일 — 탭 제거, 섹션 분리(노드 검색 / 경고)' },
    ],
  },
  {
    version: 'v0.43.0',
    date: '2026-06-11',
    title: '공유 그래프 사이드바',
    type: 'feature',
    items: [
      { category: '프론트', desc: '공유 그래프 페이지에 좌측 사이드바 추가 — 노드 인덱스(검색+클릭 이동) / 경고 패널 탭 전환' },
      { category: '프론트', desc: '공유 그래프 페이지에 우측 사이드바 추가 — 노드 상세 정보(타입·도메인·경로·설명) + 채팅 패널 통합' },
    ],
  },
  {
    version: 'v0.42.0',
    date: '2026-06-11',
    title: '유저 검색',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GET /api/users?q= 엔드포인트 추가 — 사용자명 키워드 검색, 최대 10명 반환' },
      { category: '프론트', desc: '헤더 🔍 버튼 클릭 시 유저 검색 드롭다운 — 300ms 디바운스, 결과 클릭 시 프로필 이동' },
    ],
  },
  {
    version: 'v0.41.0',
    date: '2026-06-11',
    title: '게시글 정렬',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GET /api/community/posts?sort=likes|views 파라미터 추가 — 좋아요순/조회수순 정렬 지원' },
      { category: '프론트', desc: '게시글 목록 상단에 최신순/좋아요순/조회순 정렬 버튼 추가 (전체 탭, 검색 미입력 시 노출)' },
    ],
  },
  {
    version: 'v0.40.0',
    date: '2026-06-11',
    title: '게시글 댓글 수 표시',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'PostResponse에 commentCount 필드 추가 — 게시글 목록/단건 응답에 댓글 수 포함' },
      { category: '프론트', desc: '게시글 카드에 💬 댓글 수 표시 (1개 이상일 때만)' },
    ],
  },
  {
    version: 'v0.39.0',
    date: '2026-06-11',
    title: '게시글 조회수',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'posts 테이블에 view_count 컬럼(V32) 추가 — 게시글 상세 조회 시 자동 증가' },
      { category: '프론트', desc: '게시글 카드에 👁 조회수 표시 (1 이상일 때만)' },
    ],
  },
  {
    version: 'v0.38.0',
    date: '2026-06-11',
    title: '게시글 더 보기',
    type: 'feature',
    items: [
      { category: '프론트', desc: '커뮤니티 게시글 목록 하단에 "더 보기" 버튼 추가 — 20개씩 추가 로드, 검색·탭 변경 시 초기화' },
    ],
  },
  {
    version: 'v0.37.0',
    date: '2026-06-11',
    title: '게시글 수정',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'PUT /api/community/posts/:id — 제목/내용 수정, 작성자 소유권 검증 (타인 수정 시 403)' },
      { category: '프론트', desc: '게시글 상세 패널에 수정 버튼 — 인라인 편집 폼, 저장/취소' },
    ],
  },
  {
    version: 'v0.36.0',
    date: '2026-06-11',
    title: '댓글 알림 + 댓글 삭제',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '게시글에 댓글이 달리면 게시글 작성자에게 인앱 알림 자동 발송 (자신의 게시글 제외)' },
      { category: '백엔드', desc: 'DELETE /api/community/posts/:id/comments/:cid — 댓글 삭제, 작성자 소유권 검증' },
      { category: '프론트', desc: '커뮤니티 댓글 — 작성자 본인 댓글에 hover 시 ✕ 버튼 표시, 즉시 목록에서 제거' },
    ],
  },
  {
    version: 'v0.35.0',
    date: '2026-06-11',
    title: '인앱 알림 센터',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'notifications 테이블(V31) + GET/PATCH /api/notifications API 추가' },
      { category: '백엔드', desc: '팔로우·게시글 좋아요·쪽지 수신 시 알림 자동 생성 (@Async)' },
      { category: '프론트', desc: '헤더 🔔 버튼 + 드롭다운 — 미읽 배지, 항목 클릭 이동, 열 때 전체 읽음 처리' },
    ],
  },
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
    <div className="app-page min-h-screen bg-gray-950 text-white">
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

# Railway 배포 — 시행착오 & 올바른 방법

> Spring Boot + Railway + GitHub OAuth 조합으로 배포할 때 반드시 이 순서와 주의사항을 따른다.
> 2026-06-03 검증 완료.

---

## 올바른 배포 순서

```
1. Dockerfile 작성 및 로컬 빌드 확인
2. application.yml 환경변수화 (하드코딩 제거)
3. Railway 프로젝트 생성 → GitHub 레포 연결
4. Railway PostgreSQL 서비스 추가
5. 환경변수 설정 (참조 변수 방식)
6. GitHub OAuth App 운영용 별도 생성
7. 도메인 생성 후 OAuth 콜백 URL 업데이트
8. forward-headers-strategy 설정
```

---

## 문제 1 — Dockerfile 없이 Railway 연결 먼저

**원인.** Dockerfile이 없는 상태에서 Railway에 GitHub 레포를 먼저 연결하면 Railway가 jar 파일 경로를 인식하지 못해 빌드 실패.

**해결.** Dockerfile 작성 후 Railway 빌드 재트리거.

**예방.** "로컬 빌드 확인 → CI → Railway 연결" 순서를 반드시 지킨다.

---

## 문제 2 — gradlew Permission denied

**원인.** Windows에서 Git이 `gradlew` 실행 권한을 644로 저장. Railway Linux 컨테이너에서 실행 불가.

**해결.**
```bash
git update-index --chmod=+x backend/gradlew
git commit -m "fix: gradlew 실행 권한 추가"
```

**예방.** 레포 최초 생성 시 바로 실행 권한을 설정해두면 이후 문제 없다.

---

## 문제 3 — DB URL 환경변수를 읽지 않음

**원인.** `application.yml`에 DB URL이 하드코딩되어 있어 Railway 환경변수를 아예 읽지 않았다.

**해결.**
```yaml
datasource:
  url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/codeprint}
```

**예방.** 처음부터 모든 외부 연결 정보를 환경변수로 작성한다.

---

## 문제 4 — Railway DB URL 형식 불일치 (postgresql:// vs jdbc:postgresql://)

**원인.** Railway PostgreSQL이 제공하는 URL 형식은 `postgresql://user:pass@host:port/db`. Spring JDBC는 `jdbc:postgresql://` 형식만 허용.

**해결.** `DataSourceConfig` Bean을 만들어 자동 변환.
```java
// DATABASE_URL 환경변수를 읽어 jdbc:postgresql:// 형식으로 변환
// 자격증명(user:pass)을 URI에서 분리해 HikariDataSource에 별도 설정
```

**예방.** Railway에서 Spring Boot를 쓰면 이 변환은 항상 필요하다. 처음부터 DataSourceConfig를 넣어두면 된다.

---

## 문제 5 — JDBC URL에 user:pass@host 포함 시 UnknownHostException

**원인.** `jdbc:postgresql://user:pass@host:port/db` 형식을 그대로 JDBC URL로 전달하면 PostgreSQL 드라이버가 `user:pass@host` 전체를 호스트명으로 해석.

**해결.** URI 파싱으로 자격증명을 분리한 뒤 username/password를 별도 설정.
```java
URI uri = new URI(rawUrl.replace("postgresql://", "http://"));
String host = uri.getHost();
String[] userInfo = uri.getUserInfo().split(":");
// → HikariConfig에 setJdbcUrl / setUsername / setPassword 각각 설정
```

**예방.** DB URL은 항상 자격증명 분리 후 전달한다.

---

## 문제 6 — Railway 환경변수 참조 변수 구문 오류

**원인.** Railway PostgreSQL 서비스 변수를 직접 값으로 복사해서 입력하면 DB 재생성 시 깨진다.

**올바른 방법.** Railway 환경변수 설정 시 "Add Variable" 배너에서 자동 완성된 참조 변수를 사용.
```
DATABASE_URL = ${{Postgres.DATABASE_URL}}
```
수동으로 URL을 복사해 붙여넣으면 안 된다.

---

## 문제 7 — OAuth redirect_uri 불일치 (http vs https)

**원인.** Railway 로드밸런서 뒤에서 Spring이 `X-Forwarded-Proto` 헤더를 무시하고 `http://`로 baseUrl을 계산. GitHub OAuth App에 등록된 `https://` URL과 불일치해서 "redirect_uri is not associated with this application" 오류 발생.

**해결.** `application.yml`에 추가.
```yaml
server:
  forward-headers-strategy: native
```

**예방.** 리버스 프록시 뒤에 Spring Boot를 배포할 때는 항상 이 설정을 넣는다. Railway, Heroku, Nginx 모두 해당.

---

## 문제 8 — application.yml duplicate key

**원인.** `server:` 블록이 파일에 이미 있는데 파일 전체를 읽지 않고 편집해서 두 번 작성됨. SnakeYAML이 중복 키 감지 후 기동 실패.

**해결.** 두 `server:` 블록을 하나로 합침.

**예방.** yml 파일 편집 전 반드시 파일 전체를 읽는다.

---

## 문제 9 — Railway 도메인이 원하는 이름이 아님

**원인.** Railway가 `codeprint-production.up.railway.app`을 자동 생성. 기대했던 `codeprint.up.railway.app`이 아님.

**해결.** Settings → Networking → 도메인 옆 연필 아이콘 클릭 → 원하는 이름으로 수정 가능. 단, 이미 다른 사용자가 쓰고 있으면 불가.

**예방.** 도메인 생성 직후 즉시 이름을 확인하고 GitHub OAuth App 콜백 URL과 일치하는지 검증한다.

---

## 최종 환경변수 구성 (검증 완료)

| 변수명 | 값 | 비고 |
|---|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` | Railway 참조 변수 |
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID | 운영용 앱 별도 생성 |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Secret | 운영용 앱 별도 생성 |
| `JWT_SECRET` | 32자 이상 랜덤 문자열 | |
| `ENCRYPTION_KEY` | Base64 인코딩 32바이트 키 | |
| `FRONTEND_URL` | Vercel 배포 도메인 | 배포 후 업데이트 |

## GitHub OAuth App 설정 (운영용)

| 항목 | 값 |
|---|---|
| Homepage URL | `https://codeprint.up.railway.app` |
| Authorization callback URL | `https://codeprint.up.railway.app/login/oauth2/code/github` |

> 로컬용과 운영용 OAuth App을 반드시 분리 생성한다. 하나로 쓰면 콜백 URL 충돌로 로컬 테스트가 불가능해진다.

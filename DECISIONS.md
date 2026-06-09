# DECISIONS.md

기술 결정 기록 — 문제 → 이유 → 결과 형식.

---

## v0.27.0 — Stripe → 토스페이먼츠 교체

**문제.** Stripe는 한국 서비스에서 결제 UX가 불편하고 한국 간편결제(토스, 카카오페이 등)를 지원하지 않음.

**이유.** 토스페이먼츠는 국내 주요 결제 수단을 모두 지원하며, 이미 DonatePage에서 동일 SDK를 사용 중이어서 추가 의존성 없음. 테스트 키도 이미 application-local.yml에 존재.

**결과.** `StripePaymentService`, `StripeEventRepository`, 관련 인프라 파일 삭제. `stripe-java` 의존성 제거. `TossPaymentOrder` 도메인 모델 + `toss_payment_orders` 테이블(Flyway V20) 신규 추가. `PaymentController`를 `/toss/prepare` + `/toss/confirm` 구조로 교체. Pro 플랜 금액 9,900원.

---

## v1.33.001 — Railway 배포 분석 실패 (git 미설치)

**문제.** 배포 환경에서 "분석 실패. 다시 시도해주세요." 오류 발생.

**이유.** `eclipse-temurin:21-jre` 베이스 이미지에 git이 포함되어 있지 않음. `RepoCloner.clone()`이 `ProcessBuilder`로 `git clone`을 실행하는데, git 바이너리가 없어 즉시 실패. AnalysisRunner catch 블록에서 FAILED 상태로 저장됨.

**결과.** Dockerfile 런타임 스테이지에 `apt-get install -y --no-install-recommends git` 추가. Railway 재배포 후 해결.

---

## v1.33 — AI 설명 기능 (다중 제공자)

**문제.** 그래프 노드 AI 설명 기능을 어떤 방식으로 구현할지 결정 필요.

**선택지.**
1. 서버가 Anthropic API 키를 보유, 비용은 서버 부담
2. 사용자가 각자 API 키를 등록, 비용은 사용자 부담
3. Claude Plugin/Skill 방식 (MCP 서버 등록)

**이유.**
- 옵션 1: 초기 서비스에서 AI 비용 무제한 부담 불가. 무료 플랜 사용자 남용 우려.
- 옵션 3: Claude Plugin은 Anthropic 심사 필요, 타 제공자 연동 불가.
- 옵션 2 선택: 사용자 키 사용 → 비용 전가, 동시에 Claude/OpenAI/Gemini 세 제공자 동시 지원 가능.

**SDK vs RestClient.**
- Anthropic SDK, OpenAI SDK, Gemini SDK 각각 의존성 추가 시 build.gradle 복잡도 증가.
- 세 API 모두 단순 JSON POST → RestClient 직접 HTTP 호출로 충분.
- SDK 없이 구현 → 의존성 최소화, 각 서비스 클래스 구조 일관성 유지.

**결과.** `ClaudeAiService`, `OpenAiService`, `GeminiAiService` 각각 RestClient로 구현. `UserAiKey` 엔티티에 AES-GCM 암호화(`AesEncryptionConverter`)로 저장. V16 Flyway 마이그레이션으로 `user_ai_keys` 테이블 추가.

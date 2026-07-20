// 전체 Spring 빈 배선이 실제로 조립되는지만 확인하는 컨텍스트 로딩 스모크 테스트.
// 유닛 테스트(Mockito)·@DataJpaTest(JPA 슬라이스)는 순환 빈 참조 같은 전체 배선 실패를 재현·검증하지 못한다 —
// BE-18(WebSocketAuthorizationInterceptor)·BE-19(GraphWarningsCacheAdapter) 둘 다 이 종류의 버그였고
// CI가 green인 채로 프로덕션 배포까지 실패한 뒤에야 발견됐다(GATE_GAPS.md [G-8] 참조). 이 테스트는 그 재발을
// CI 단계에서 잡기 위한 최소 비용 안전망 — 로직은 전혀 검증하지 않고 컨텍스트가 뜨는지만 본다.
// local 프로파일 활성화 필요: SecretHygieneGuard가 non-local 프로파일에서 기본 시크릿 값을 거부하기 때문.
package com.codeprint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/codeprint",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.flyway.validate-on-migrate=false"
})
class CodeprintApplicationContextTest {

    @Test
    void contextLoads() {
    }
}

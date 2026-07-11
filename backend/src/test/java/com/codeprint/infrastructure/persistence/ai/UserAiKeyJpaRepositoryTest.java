// UserAiKey 저장소 회귀 테스트 — deleteByUserIdAndProvider @Transactional 누락 재발 방지(ERROR_TRACKER BE-13, 반복-A #154와 동일 원인 클래스)
package com.codeprint.infrastructure.persistence.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAiKeyJpaRepositoryTest {

    // deleteByUserIdAndProvider — @Transactional 선언 여부 검증 (누락 시 TransactionRequiredException → 500)
    @Test
    @DisplayName("deleteByUserIdAndProvider — @Transactional 어노테이션이 선언돼 있어야 한다")
    void deleteByUserIdAndProvider_hasTransactionalAnnotation() throws NoSuchMethodException {
        Method method = UserAiKeyJpaRepository.class.getMethod(
                "deleteByUserIdAndProvider", UUID.class, com.codeprint.domain.ai.AiProvider.class);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("deleteByUserIdAndProvider must have @Transactional to avoid TransactionRequiredException")
                .isTrue();
    }
}

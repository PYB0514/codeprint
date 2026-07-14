// 팀 API 키 도메인 단위 테스트 — 발급·검증·폐기 경계 회귀 방지
package com.codeprint.domain.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamApiKeyTest {

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    @Test
    @DisplayName("발급 시 평문 키는 cpk_ 접두사를 가지고, 엔티티엔 해시만 저장된다")
    void issueReturnsRawKeyWithPrefixAndStoresHashOnly() {
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(TEAM_ID, "ci-agent", CREATED_BY);

        assertThat(issued.rawKey()).startsWith("cpk_");
        assertThat(issued.entity().getKeyHash()).isNotEqualTo(issued.rawKey());
        assertThat(issued.entity().getKeyPrefix()).isEqualTo(issued.rawKey().substring(0, 12));
        assertThat(issued.entity().getTeamId()).isEqualTo(TEAM_ID);
        assertThat(issued.entity().getCreatedBy()).isEqualTo(CREATED_BY);
        assertThat(issued.entity().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("두 번 발급하면 서로 다른 평문 키가 나온다")
    void issueProducesUniqueKeys() {
        TeamApiKey.IssuedKey a = TeamApiKey.issue(TEAM_ID, "a", CREATED_BY);
        TeamApiKey.IssuedKey b = TeamApiKey.issue(TEAM_ID, "b", CREATED_BY);

        assertThat(a.rawKey()).isNotEqualTo(b.rawKey());
    }

    @Test
    @DisplayName("올바른 평문 키는 matches()가 true")
    void matchesReturnsTrueForCorrectRawKey() {
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(TEAM_ID, "ci-agent", CREATED_BY);

        assertThat(issued.entity().matches(issued.rawKey())).isTrue();
    }

    @Test
    @DisplayName("틀린 평문 키는 matches()가 false")
    void matchesReturnsFalseForWrongRawKey() {
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(TEAM_ID, "ci-agent", CREATED_BY);

        assertThat(issued.entity().matches("cpk_wrongkey")).isFalse();
    }

    @Test
    @DisplayName("폐기된 키는 평문이 맞아도 matches()가 false")
    void revokedKeyNeverMatches() {
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(TEAM_ID, "ci-agent", CREATED_BY);
        issued.entity().revoke();

        assertThat(issued.entity().isRevoked()).isTrue();
        assertThat(issued.entity().matches(issued.rawKey())).isFalse();
    }
}

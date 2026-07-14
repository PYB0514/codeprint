// 팀 API 키 발급·목록·폐기 애플리케이션 서비스 (교차 프로젝트 조회 인증용)
package com.codeprint.application.team;

import com.codeprint.domain.team.Team;
import com.codeprint.domain.team.TeamApiKey;
import com.codeprint.domain.team.TeamApiKeyRepository;
import com.codeprint.domain.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamApiKeyApplicationService {

    private final TeamRepository teamRepository;
    private final TeamApiKeyRepository apiKeyRepository;

    // 팀장만 신규 API 키 발급 — 평문은 이 반환값에서만 노출되고 재조회 불가
    @Transactional
    public TeamApiKey.IssuedKey issueKey(UUID teamId, UUID requesterId, String name) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        TeamApiKey.IssuedKey issued = TeamApiKey.issue(teamId, name, requesterId);
        apiKeyRepository.save(issued.entity());
        return issued;
    }

    // 팀 API 키 목록 조회 — 팀장만 가능(IDOR 방지)
    public List<TeamApiKey> getKeys(UUID teamId, UUID requesterId) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        return apiKeyRepository.findByTeamId(teamId);
    }

    // API 키 폐기 — 팀장만 가능
    @Transactional
    public void revokeKey(UUID teamId, UUID requesterId, UUID keyId) {
        Team team = getTeamOrThrow(teamId);
        verifyOwner(team, requesterId);
        TeamApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API 키를 찾을 수 없습니다: " + keyId));
        if (!key.getTeamId().equals(teamId)) {
            throw new IllegalArgumentException("이 팀의 API 키가 아닙니다: " + keyId);
        }
        key.revoke();
        apiKeyRepository.save(key);
    }

    private Team getTeamOrThrow(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
    }

    private void verifyOwner(Team team, UUID userId) {
        if (!team.getOwnerUserId().equals(userId)) {
            throw new SecurityException("팀장만 수행할 수 있습니다.");
        }
    }
}

// 프로젝트 의도 아키텍처 저장·조회 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.ArchitectureIntentAuditLog;
import com.codeprint.domain.graph.ArchitectureIntentAuditLogRepository;
import com.codeprint.domain.graph.ArchitectureIntentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchitectureIntentService {

    private final ArchitectureIntentRepository repository;
    private final ObjectMapper objectMapper;
    private final ArchitectureIntentAuditLogRepository auditLogRepository;

    // 프로젝트의 의도 아키텍처 조회 — 없으면 빈 Optional
    @Transactional(readOnly = true)
    public Optional<ArchitectureIntent> findByProjectId(UUID projectId) {
        return repository.findJsonByProjectId(projectId).map(this::parse);
    }

    // 프로젝트의 의도 아키텍처를 저장하고 경고 캐시를 무효화
    @Transactional
    @CacheEvict(value = "graphWarnings", allEntries = true)
    public void save(UUID projectId, ArchitectureIntent intent) {
        repository.upsert(projectId, toJson(intent));
    }

    // 행위자 정보를 남기며 저장 — 예외(IGNORE) 규칙 추가/제거분만 감사 로그로 기록(모듈·의존규칙은 대상 아님)
    @Transactional
    @CacheEvict(value = "graphWarnings", allEntries = true)
    public void save(UUID projectId, ArchitectureIntent intent, UUID actorUserId, String actorUsername) {
        List<ArchitectureIntent.IgnoreRule> before = findByProjectId(projectId)
                .map(ArchitectureIntent::ignores)
                .orElse(List.of());
        recordIgnoreRuleChanges(projectId, actorUserId, actorUsername, before, intent.ignores());
        repository.upsert(projectId, toJson(intent));
    }

    // 이전·이후 예외 규칙을 비교해 추가/제거된 것만 감사 로그로 기록
    private void recordIgnoreRuleChanges(UUID projectId, UUID userId, String username,
            List<ArchitectureIntent.IgnoreRule> before, List<ArchitectureIntent.IgnoreRule> after) {
        for (ArchitectureIntent.IgnoreRule rule : after) {
            if (!before.contains(rule)) {
                auditLogRepository.save(ArchitectureIntentAuditLog.create(
                        projectId, userId, username, "ADD", rule.type(), rule.fromGlob(), rule.toGlob()));
            }
        }
        for (ArchitectureIntent.IgnoreRule rule : before) {
            if (!after.contains(rule)) {
                auditLogRepository.save(ArchitectureIntentAuditLog.create(
                        projectId, userId, username, "REMOVE", rule.type(), rule.fromGlob(), rule.toGlob()));
            }
        }
    }

    // 프로젝트의 예외 규칙 변경 이력 최신순 조회
    @Transactional(readOnly = true)
    public List<ArchitectureIntentAuditLog> findAuditLog(UUID projectId) {
        return auditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    // 프로젝트의 의도 아키텍처를 삭제하고 경고 캐시를 무효화
    @Transactional
    @CacheEvict(value = "graphWarnings", allEntries = true)
    public void delete(UUID projectId) {
        repository.deleteByProjectId(projectId);
    }

    // JSON 문자열 → ArchitectureIntent 변환 (LocalAnalyzer와 동일 방식)
    private ArchitectureIntent parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ArchitectureIntent.Module> modules = new ArrayList<>();
            for (JsonNode m : root.path("modules")) {
                List<String> globs = new ArrayList<>();
                for (JsonNode g : m.path("globs")) globs.add(g.asText());
                modules.add(new ArchitectureIntent.Module(m.path("name").asText(), globs));
            }
            List<ArchitectureIntent.DependencyRule> rules = new ArrayList<>();
            for (JsonNode r : root.path("rules")) {
                rules.add(new ArchitectureIntent.DependencyRule(
                        r.path("from").asText(), r.path("to").asText(), r.path("edgeType").asText(null)));
            }
            List<ArchitectureIntent.IgnoreRule> ignores = new ArrayList<>();
            for (JsonNode g : root.path("ignore")) {
                ignores.add(new ArchitectureIntent.IgnoreRule(
                        g.path("type").asText(null), g.path("from").asText(null), g.path("to").asText(null)));
            }
            return new ArchitectureIntent(modules, rules, ignores);
        } catch (Exception e) {
            log.warn("의도 아키텍처 JSON 파싱 실패 (빈 의도 반환): {}", e.getMessage());
            return new ArchitectureIntent(List.of(), List.of());
        }
    }

    // ArchitectureIntent → JSON 문자열 변환
    private String toJson(ArchitectureIntent intent) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode modules = root.putArray("modules");
            for (ArchitectureIntent.Module m : intent.modules()) {
                ObjectNode mn = modules.addObject();
                mn.put("name", m.name());
                ArrayNode globs = mn.putArray("globs");
                m.globs().forEach(globs::add);
            }
            ArrayNode rules = root.putArray("rules");
            for (ArchitectureIntent.DependencyRule r : intent.rules()) {
                ObjectNode rn = rules.addObject();
                rn.put("from", r.from());
                rn.put("to", r.to());
                if (r.edgeType() != null) rn.put("edgeType", r.edgeType());
            }
            ArrayNode ignores = root.putArray("ignore");
            for (ArchitectureIntent.IgnoreRule g : intent.ignores()) {
                ObjectNode gn = ignores.addObject();
                gn.put("type", g.type());
                gn.put("from", g.fromGlob());
                gn.put("to", g.toGlob());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("의도 아키텍처 직렬화 실패", e);
        }
    }
}

// 그래프 뷰 프리셋 저장/조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.graph.GraphViewPreset;
import com.codeprint.domain.graph.GraphViewPresetDefaults;
import com.codeprint.domain.graph.GraphViewPresetRepository;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GraphViewPresetController {

    private final GraphViewPresetRepository presetRepository;
    private final GraphQueryService graphQueryService;
    private final GraphFacade graphFacade;

    // 내 프리셋 전체 조회 (슬롯 1~4, 없는 슬롯은 기본값으로 채움)
    @GetMapping("/api/graphs/{graphId}/presets")
    public ResponseEntity<List<Map<String, Object>>> getPresets(
            @PathVariable UUID graphId,
            @AuthenticationPrincipal User user) {

        List<GraphViewPreset> saved = presetRepository
                .findByGraphIdAndUserIdOrderBySlotAsc(graphId, user.getId());

        // 저장된 슬롯 맵핑
        Map<Integer, GraphViewPreset> bySlot = new java.util.LinkedHashMap<>();
        for (GraphViewPreset p : saved) {
            bySlot.put(p.getSlot(), p);
        }

        // 슬롯 1~4 기본값 채움
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (int slot = 1; slot <= 4; slot++) {
            if (bySlot.containsKey(slot)) {
                GraphViewPreset p = bySlot.get(slot);
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("slot", p.getSlot());
                item.put("name", p.getName());
                item.put("config", p.getConfig());
                item.put("isDefault", false);
                result.add(item);
            } else {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("slot", slot);
                item.put("name", GraphViewPresetDefaults.defaultName(slot));
                item.put("config", GraphViewPresetDefaults.defaultConfig(slot));
                item.put("isDefault", true);
                result.add(item);
            }
        }
        return ResponseEntity.ok(result);
    }

    // 슬롯에 현재 뷰 상태 저장 (신규/덮어쓰기)
    @PutMapping("/api/graphs/{graphId}/presets/{slot}")
    public ResponseEntity<Void> savePreset(
            @PathVariable UUID graphId,
            @PathVariable @Min(1) @Max(4) int slot,
            @RequestBody @Valid SavePresetRequest req,
            @AuthenticationPrincipal User user) {

        // 그래프 소유자 확인
        graphFacade.verifyGraphOwnership(graphId, user.getId());

        // 기존 슬롯이 있으면 삭제 후 새로 저장
        presetRepository.findByGraphIdAndUserIdAndSlot(graphId, user.getId(), slot)
                .ifPresent(presetRepository::delete);

        GraphViewPreset preset = GraphViewPreset.of(graphId, user.getId(), slot, req.name(), req.config());
        presetRepository.save(preset);
        return ResponseEntity.ok().build();
    }

    // 공유 뷰용 — 특정 사용자의 슬롯 조회 (비인증 접근 허용, 공개 프로젝트만)
    @GetMapping("/api/share/{projectId}/presets/{slot}")
    public ResponseEntity<Map<String, Object>> getPublicPreset(
            @PathVariable UUID projectId,
            @PathVariable @Min(1) @Max(4) int slot,
            @RequestParam UUID userId) {

        // 프로젝트가 공개가 아니면 차단 (비공개 프로젝트 프리셋 누출 방지)
        graphFacade.getPublicProject(projectId);

        // 해당 프로젝트의 최신 그래프 찾기
        return graphQueryService.findLatestByProject(projectId)
                .flatMap(g -> presetRepository.findByGraphIdAndUserIdAndSlot(g.getId(), userId, slot))
                .map(p -> {
                    Map<String, Object> body = new java.util.LinkedHashMap<>();
                    body.put("slot", p.getSlot());
                    body.put("name", p.getName());
                    body.put("config", p.getConfig());
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 프리셋 저장 요청 DTO
    public record SavePresetRequest(
            @NotBlank @Size(max = 30) String name,
            Map<String, Object> config
    ) {}
}

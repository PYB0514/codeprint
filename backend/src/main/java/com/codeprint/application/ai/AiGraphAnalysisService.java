// 그래프 전체를 AI에 전송하여 누락 패턴을 감지하는 서비스
package com.codeprint.application.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import com.codeprint.domain.ai.UserAiKeyRepository;
import com.codeprint.infrastructure.ai.ClaudeAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiGraphAnalysisService {

    private final UserAiKeyRepository aiKeyRepository;
    private final ClaudeAiService claudeAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record DetectedIssue(
        String nodeId,
        String nodeName,
        String issueType,
        String message,
        String suggestion
    ) {}

    // 그래프 노드+엣지를 Claude에 전송하여 누락 패턴 감지
    public List<DetectedIssue> analyze(UUID userId, List<GraphNodeDto> nodes, List<GraphEdgeDto> edges) {
        UserAiKey key = aiKeyRepository.findByUserIdAndProvider(userId, AiProvider.CLAUDE)
                .orElseThrow(() -> new IllegalStateException("Claude API 키가 등록되지 않았습니다. AI 설정에서 키를 등록하세요."));

        String prompt = buildAnalysisPrompt(nodes, edges);
        String response = claudeAiService.explain(key.getApiKey(), prompt);
        return parseIssues(response, nodes);
    }

    // 그래프 요약 프롬프트 구성 (상위 300개 노드/엣지 제한으로 토큰 절약)
    private String buildAnalysisPrompt(List<GraphNodeDto> nodes, List<GraphEdgeDto> edges) {
        List<GraphNodeDto> sampleNodes = nodes.stream()
                .filter(n -> "FUNCTION".equals(n.type()) || "FILE".equals(n.type()))
                .limit(200)
                .toList();
        List<GraphEdgeDto> sampleEdges = edges.stream().limit(300).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("다음은 소프트웨어 프로젝트의 코드 구조 그래프입니다. ");
        sb.append("아키텍처 분석을 통해 누락되거나 개선이 필요한 패턴을 찾아주세요.\n\n");

        sb.append("## 노드 목록 (파일 및 함수)\n");
        for (GraphNodeDto n : sampleNodes) {
            String comment = "";
            if (n.metadata() != null && n.metadata().get("comment") instanceof String c) {
                comment = " // " + c;
            }
            sb.append(String.format("- [%s] %s%s\n", n.type(), n.name(), comment));
        }

        sb.append("\n## 관계 목록 (엣지)\n");
        Map<UUID, String> nodeNames = new java.util.HashMap<>();
        nodes.forEach(n -> nodeNames.put(n.id(), n.name()));
        for (GraphEdgeDto e : sampleEdges) {
            String src = nodeNames.getOrDefault(e.sourceNodeId(), e.sourceNodeId().toString());
            String tgt = nodeNames.getOrDefault(e.targetNodeId(), e.targetNodeId().toString());
            sb.append(String.format("- %s --[%s]--> %s\n", src, e.type(), tgt));
        }

        sb.append("\n## 분석 요청\n");
        sb.append("위 그래프를 분석하여 아래 항목을 찾아주세요:\n");
        sb.append("1. 에러 처리가 누락된 함수 (예외 처리 관련 호출이 없는 중요 비즈니스 로직)\n");
        sb.append("2. 입력값 검증이 누락된 API 엔드포인트 함수\n");
        sb.append("3. 테스트 코드가 없어 보이는 핵심 비즈니스 로직\n");
        sb.append("4. 레이어 아키텍처 위반 (컨트롤러가 리포지토리를 직접 호출하는 패턴)\n");
        sb.append("5. 로깅이 누락된 중요 작업 (결제, 인증, 데이터 변경)\n\n");
        sb.append("응답 형식 — 반드시 아래 JSON 배열만 반환 (설명 없이):\n");
        sb.append("[{\"nodeName\":\"함수명\",\"issueType\":\"MISSING_ERROR_HANDLING|MISSING_VALIDATION|MISSING_TEST|LAYER_VIOLATION|MISSING_LOGGING\",");
        sb.append("\"message\":\"문제 설명 (한국어, 1문장)\",\"suggestion\":\"개선 방안 (한국어, 1문장)\"}]\n");
        sb.append("발견된 문제가 없으면 빈 배열 [] 반환. 최대 10개만 반환.");

        return sb.toString();
    }

    // Claude 응답에서 이슈 목록 파싱
    @SuppressWarnings("unchecked")
    private List<DetectedIssue> parseIssues(String response, List<GraphNodeDto> nodes) {
        try {
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start < 0 || end < 0) return List.of();

            String json = response.substring(start, end + 1);
            List<Map<String, String>> raw = objectMapper.readValue(json, List.class);

            Map<String, UUID> nameToId = new java.util.HashMap<>();
            nodes.forEach(n -> nameToId.put(n.name(), n.id()));

            List<DetectedIssue> issues = new ArrayList<>();
            for (Map<String, String> item : raw) {
                String nodeName = item.getOrDefault("nodeName", "");
                UUID nodeId = nameToId.get(nodeName);
                issues.add(new DetectedIssue(
                        nodeId != null ? nodeId.toString() : "",
                        nodeName,
                        item.getOrDefault("issueType", "UNKNOWN"),
                        item.getOrDefault("message", ""),
                        item.getOrDefault("suggestion", "")
                ));
            }
            return issues;
        } catch (Exception e) {
            return List.of();
        }
    }
}

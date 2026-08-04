// 경고+소스로 FixPromptBundle을 조립하고 LLM 프롬프트로 렌더링 — 순수 함수, LLM 호출을 모름(§17.10-① 벤더 중립 원칙)
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Node;

import java.util.Map;

final class FixPromptBundleAssembler {

    private FixPromptBundleAssembler() {
    }

    // MISSING_TRANSACTIONAL_DELETE 경고+대상 노드+파일 원문으로 번들 조립 — 수정 레시피는 경고 message에 이미 있어 재사용(재생성 금지)
    static FixPromptBundle assemble(Map<String, Object> warning, Node target, String fileContent) {
        String message = String.valueOf(warning.get("message"));
        return new FixPromptBundle(
                String.valueOf(warning.get("type")),
                message,
                target.getName(),
                target.getFilePath(),
                target.getLanguage(),
                message.substring(message.indexOf("수정:")),
                fileContent
        );
    }

    // 번들 → LLM 프롬프트 텍스트. 출력 계약: RATIONALE/DIFF 두 섹션만, 자유 서술 금지(§17.10-① constraints)
    static String renderPrompt(FixPromptBundle bundle) {
        return """
                당신은 %s 코드베이스의 구조 위반을 고치는 자동 수정기입니다. 아래 사실만으로 최소한의 patch를 만드세요.

                규칙: %s
                진단: %s
                대상 함수: %s (파일: %s)

                제약:
                - 위에 주어진 파일 하나만 수정하세요. 다른 파일을 만들거나 참조하지 마세요.
                - 신규 외부 의존성(라이브러리)을 추가하지 마세요.
                - 기존 코드 스타일·들여쓰기를 그대로 유지하세요.
                - 출력은 정확히 두 섹션만 포함하세요. 그 외 설명·서론·후기는 절대 쓰지 마세요.

                파일 원문:
                ```%s
                %s
                ```

                출력 형식(정확히 이 형식):
                RATIONALE: <한 문단, 한국어, 왜 이렇게 고치는지>
                DIFF:
                <unified diff, --- a/%s / +++ b/%s 헤더 포함>
                """.formatted(
                bundle.language(), bundle.ruleType(), bundle.diagnosisMessage(),
                bundle.targetFunctionName(), bundle.targetFilePath(),
                bundle.language() != null ? bundle.language() : "", bundle.fileContent(),
                bundle.targetFilePath(), bundle.targetFilePath());
    }
}

// BYOK 다중 프로바이더 순차 시도 — 인증/quota 오류(401/403/429)면 다음 등록 프로바이더로 전환, 그 외 오류는 즉시 전파
package com.codeprint.application.graph;

import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.infrastructure.ai.AiService;
import com.codeprint.shared.ai.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
public class AiFailoverClient {

    // 전환 대상으로 보는 HTTP 상태 — 계정/quota 레벨 문제만(다른 오류는 프로바이더를 바꿔도 똑같이 실패할 가능성이 높음)
    private static final List<Integer> FAILOVER_STATUS_CODES = List.of(401, 403, 429);

    private final List<AiProvider> candidates;
    private final Function<AiProvider, Optional<String>> keyLookup;
    private final Function<AiProvider, AiService> serviceLookup;
    private int currentIndex = 0;

    public AiFailoverClient(List<AiProvider> candidates,
                             Function<AiProvider, Optional<String>> keyLookup,
                             Function<AiProvider, AiService> serviceLookup) {
        this.candidates = candidates;
        this.keyLookup = keyLookup;
        this.serviceLookup = serviceLookup;
    }

    // 요청 프로바이더+사용자의 등록 프로바이더 목록으로 순서·키 조회·서비스 조회를 한 번에 조립 — 호출부(RoleSpecService·
    // FeatureSpecService)의 팬아웃을 줄이기 위한 진입점(개별 조립 단계를 여기로 캡슐화)
    public static AiFailoverClient forUser(AiProvider provider, UUID userId, AiKeyPort aiKeyPort,
                                            Map<AiProvider, AiService> servicesByProvider) {
        List<AiProvider> candidates = orderedCandidates(provider, aiKeyPort.findRegisteredProviders(userId));
        return new AiFailoverClient(candidates, p -> aiKeyPort.findPlainKey(userId, p), servicesByProvider::get);
    }

    // 요청된 프로바이더를 맨 앞에 두고, 나머지 등록 프로바이더(등록순)를 뒤에 이어붙인 순서 목록 생성 — 중복 제거
    static List<AiProvider> orderedCandidates(AiProvider requested, List<AiProvider> registered) {
        List<AiProvider> ordered = new ArrayList<>();
        ordered.add(requested);
        for (AiProvider p : registered) {
            if (!ordered.contains(p)) ordered.add(p);
        }
        return ordered;
    }

    // 현재 활성 프로바이더로 생성 시도 — 인증/quota 오류면 다음 등록 프로바이더로 전환 후 같은 프롬프트로 재시도
    public String generate(String prompt) {
        while (currentIndex < candidates.size()) {
            AiProvider provider = candidates.get(currentIndex);
            AiService service = serviceLookup.apply(provider);
            String apiKey = keyLookup.apply(provider).orElse(null);
            if (service == null || apiKey == null) {
                currentIndex++;
                continue;
            }
            try {
                return service.generate(apiKey, prompt);
            } catch (Exception e) {
                boolean hasNext = currentIndex + 1 < candidates.size();
                if (isFailoverWorthy(e) && hasNext) {
                    log.warn("AI 프로바이더 {} 인증/quota 실패로 다음 프로바이더로 전환: {}", provider, e.getMessage());
                    currentIndex++;
                    continue;
                }
                throw e;
            }
        }
        throw new IllegalStateException("등록된 AI 프로바이더가 없음");
    }

    // 요청 처리 중 실제로 프로바이더가 전환됐는지
    public boolean switched() {
        return currentIndex > 0;
    }

    public AiProvider originalProvider() {
        return candidates.get(0);
    }

    // 마지막으로 성공(또는 시도)한 프로바이더 — candidates 소진 시에도 범위 안전
    public AiProvider currentProvider() {
        return candidates.get(Math.min(currentIndex, candidates.size() - 1));
    }

    // 예외 체인에서 HttpStatusCodeException을 찾아 401/403/429인지 확인
    private static boolean isFailoverWorthy(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof HttpStatusCodeException httpEx) {
                return FAILOVER_STATUS_CODES.contains(httpEx.getStatusCode().value());
            }
            cur = cur.getCause();
        }
        return false;
    }
}

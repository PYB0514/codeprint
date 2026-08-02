// AI 제공자 3종이 공유하는 타임아웃 설정 — GitHubApiClient의 connectTimeout(10초) 컨벤션과 동일한 이유(외부 API 무한 대기 방지),
// 레이어A/B가 한 요청에서 최대 25회(레이어A 15+레이어B 10)까지 순차 호출할 수 있어 응답 지연 시 타임아웃이 특히 중요
package com.codeprint.infrastructure.ai;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

final class AiRestClients {

    private AiRestClients() {}

    static RestClient create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}

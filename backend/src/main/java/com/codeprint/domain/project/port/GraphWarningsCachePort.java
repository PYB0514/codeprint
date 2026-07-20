// project 컨텍스트가 graph 컨텍스트의 경고 캐시를 직접 참조하지 않기 위한 포트
package com.codeprint.domain.project.port;

public interface GraphWarningsCachePort {

    // 게이트 정책이 바뀌면 캐시된 detect() 결과가 stale해지므로 전체 무효화
    void evictAll();
}

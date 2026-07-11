// PR 게이트 체크 결과 기록 도메인 Repository 인터페이스
package com.codeprint.domain.analysis;

public interface GateCheckLogRepository {

    // 게이트 체크 결과 저장
    GateCheckLog save(GateCheckLog gateCheckLog);
}

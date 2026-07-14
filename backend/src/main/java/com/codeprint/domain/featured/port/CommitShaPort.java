// Featured 도메인에서 GitHub 레포의 기본 브랜치 최신 커밋 SHA를 조회하는 포트
package com.codeprint.domain.featured.port;

import java.util.Optional;

public interface CommitShaPort {

    // owner/repo 형식으로 조회, 실패 시 빈 값 반환(안전하게 재분석 쪽으로 판단)
    Optional<String> fetchLatestCommitSha(String repoFullName);
}

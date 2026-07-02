// Featured 도메인에서 GitHub 레포의 star 수·description을 조회하는 포트
package com.codeprint.domain.featured.port;

public interface RepoMetadataPort {

    // owner/repo 형식으로 조회, 실패 시 빈 메타데이터(null, null) 반환
    RepoMetadata fetch(String repoFullName);

    record RepoMetadata(Integer stars, String description) {}
}

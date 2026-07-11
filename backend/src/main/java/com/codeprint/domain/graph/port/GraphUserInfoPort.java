// Graph 도메인에서 필요한 유저 정보 포트 (user 도메인 모델 비노출)
package com.codeprint.domain.graph.port;

import java.util.Optional;
import java.util.UUID;

public interface GraphUserInfoPort {

    // userId로 username·배경이미지 원본 URL 조회 — 유저 없으면 empty
    Optional<UserInfo> findUserInfo(UUID userId);

    // graph 도메인이 필요로 하는 user 필드만 추린 view
    record UserInfo(String username, String graphBgUrl) {}
}

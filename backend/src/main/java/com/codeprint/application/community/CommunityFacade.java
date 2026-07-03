// community 컨트롤러가 graph·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.community;

import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostRepository;
import com.codeprint.domain.community.port.FollowQueryPort;
import com.codeprint.domain.community.port.GraphReadPort;
import com.codeprint.domain.community.port.ProjectReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityFacade {

    private final GraphReadPort graphReadPort;
    private final ProjectReadPort projectReadPort;
    private final FollowQueryPort followQueryPort;
    private final PostRepository postRepository;

    // 공개 프로젝트의 레포 URL 반환 — 비공개·미소유이면 empty
    public Optional<String> findPublicRepoUrl(UUID graphId, UUID userId) {
        return graphReadPort.findProjectId(graphId)
                .flatMap(projectId -> projectReadPort.findPublicRepoUrl(projectId, userId));
    }

    // 게시글에 첨부된 그래프의 노드·엣지 스냅샷 반환
    public Optional<GraphReadPort.GraphSnapshot> getGraphSnapshot(UUID graphId) {
        return graphReadPort.findGraphSnapshot(graphId);
    }

    // 게시글 등록 시점에 프로젝트+프리셋 슬롯의 설정을 스냅샷으로 캡처(이후 원본 프리셋이 바뀌어도 무관)
    public Optional<GraphReadPort.PresetSnapshot> captureGraphSnapshot(UUID projectId, UUID userId, int presetSlot) {
        return graphReadPort.findLatestPresetConfig(projectId, userId, presetSlot);
    }

    // 팔로잉 유저의 게시글 목록 조회
    public List<Post> getFollowingFeed(UUID followerId, Pageable pageable) {
        List<UUID> followingIds = followQueryPort.findFollowingIds(followerId);
        if (followingIds.isEmpty()) {
            return List.of();
        }
        return postRepository.findByUserIdInOrderByCreatedAtDesc(followingIds, pageable);
    }
}

// Featured PostPublishingPort의 community 컨텍스트 어댑터 — 시스템 계정 소유 통합 게시글 발행
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.community.CommunityFacade;
import com.codeprint.application.community.PostCommandService;
import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostGraphSnapshot;
import com.codeprint.domain.featured.port.PostPublishingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FeaturedPostPublishingAdapter implements PostPublishingPort {

    // 시스템 계정은 프리셋을 저장한 적이 없어 항상 기본값(GraphViewPresetDefaults)으로 캡처됨
    private static final int DEFAULT_PRESET_SLOT = 1;

    private final PostCommandService postCommandService;
    private final CommunityFacade communityFacade;

    @Override
    public UUID createPost(String title, String content) {
        Post post = postCommandService.createPost(
                FeaturedProjectProvisioningAdapter.SYSTEM_USER_ID, null, title, content,
                null, List.of(), List.of(), List.of(), null);
        return post.getId();
    }

    @Override
    public Optional<SnapshotToPublish> captureSnapshot(UUID projectId) {
        return communityFacade.captureGraphSnapshot(
                        projectId, FeaturedProjectProvisioningAdapter.SYSTEM_USER_ID, DEFAULT_PRESET_SLOT)
                .map(preset -> new SnapshotToPublish(projectId, preset.graphId(), preset.config()));
    }

    @Override
    public void replaceSnapshots(UUID postId, List<SnapshotToPublish> snapshots) {
        List<PostCommandService.SnapshotToSave> toSave = snapshots.stream()
                .map(s -> new PostCommandService.SnapshotToSave(s.projectId(), s.graphId(), s.config()))
                .toList();
        postCommandService.replaceGraphSnapshots(postId, toSave);
    }

    @Override
    public Map<UUID, Integer> getSnapshotPositions(UUID postId) {
        return postCommandService.getGraphSnapshots(postId).stream()
                .collect(Collectors.toMap(PostGraphSnapshot::getProjectId, PostGraphSnapshot::getPosition));
    }
}

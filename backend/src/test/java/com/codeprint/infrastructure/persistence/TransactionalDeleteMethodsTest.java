// Spring Data 파생 deleteBy*/removeBy* 메서드 @Transactional 누락 회귀 방지 (BE-13 후속 전수 감사)
package com.codeprint.infrastructure.persistence;

import com.codeprint.domain.notification.PushSubscriptionRepository;
import com.codeprint.infrastructure.persistence.analysis.ParsedFileCachePostgresAdapter;
import com.codeprint.infrastructure.persistence.community.PostBookmarkJpaRepository;
import com.codeprint.infrastructure.persistence.community.PostLikeJpaRepository;
import com.codeprint.infrastructure.persistence.community.PostRepositoryImpl;
import com.codeprint.infrastructure.persistence.graph.NodeStyleRepositoryImpl;
import com.codeprint.infrastructure.persistence.graph.WarningSuppressionRepositoryImpl;
import com.codeprint.infrastructure.persistence.message.UserBlockRepositoryImpl;
import com.codeprint.infrastructure.persistence.user.UserFollowRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalDeleteMethodsTest {

    // BE-13(UserAiKeyJpaRepository)·RefreshTokenRepositoryImpl과 같은 원인 클래스로 발견된 9건 — 누락 시 삭제 API 호출마다
    // InvalidDataAccessApiUsageException(No EntityManager with actual transaction available) 500 발생
    @Test
    @DisplayName("PostBookmarkJpaRepository.deleteByUserIdAndPostId — @Transactional 선언돼 있어야 한다")
    void postBookmark_deleteByUserIdAndPostId() throws NoSuchMethodException {
        Method m = PostBookmarkJpaRepository.class.getMethod("deleteByUserIdAndPostId", UUID.class, UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("PostLikeJpaRepository.deleteByUserIdAndPostId — @Transactional 선언돼 있어야 한다")
    void postLike_deleteByUserIdAndPostId() throws NoSuchMethodException {
        Method m = PostLikeJpaRepository.class.getMethod("deleteByUserIdAndPostId", UUID.class, UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("PushSubscriptionRepository.deleteByUserIdAndEndpoint — @Transactional 선언돼 있어야 한다")
    void pushSubscription_deleteByUserIdAndEndpoint() throws NoSuchMethodException {
        Method m = PushSubscriptionRepository.class.getMethod("deleteByUserIdAndEndpoint", UUID.class, String.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("UserFollowRepositoryImpl.deleteByFollowerIdAndFollowingId — @Transactional 선언돼 있어야 한다")
    void userFollow_deleteByFollowerIdAndFollowingId() throws NoSuchMethodException {
        Method m = UserFollowRepositoryImpl.class.getMethod("deleteByFollowerIdAndFollowingId", UUID.class, UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("NodeStyleRepositoryImpl.deleteByGraphIdAndNodeId — @Transactional 선언돼 있어야 한다")
    void nodeStyle_deleteByGraphIdAndNodeId() throws NoSuchMethodException {
        Method m = NodeStyleRepositoryImpl.class.getMethod("deleteByGraphIdAndNodeId", UUID.class, UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("WarningSuppressionRepositoryImpl.deleteByProjectIdAndFingerprint — @Transactional 선언돼 있어야 한다")
    void warningSuppression_deleteByProjectIdAndFingerprint() throws NoSuchMethodException {
        Method m = WarningSuppressionRepositoryImpl.class.getMethod("deleteByProjectIdAndFingerprint", UUID.class, String.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("UserBlockRepositoryImpl.deleteByBlockerAndBlocked — @Transactional 선언돼 있어야 한다")
    void userBlock_deleteByBlockerAndBlocked() throws NoSuchMethodException {
        Method m = UserBlockRepositoryImpl.class.getMethod("deleteByBlockerAndBlocked", UUID.class, UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("PostRepositoryImpl.deleteSnapshotsByPostId — @Transactional 선언돼 있어야 한다")
    void post_deleteSnapshotsByPostId() throws NoSuchMethodException {
        Method m = PostRepositoryImpl.class.getMethod("deleteSnapshotsByPostId", UUID.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("ParsedFileCachePostgresAdapter.evictOlderThan — @Transactional 선언돼 있어야 한다")
    void parsedFileCache_evictOlderThan() throws NoSuchMethodException {
        Method m = ParsedFileCachePostgresAdapter.class.getMethod("evictOlderThan", UUID.class, Instant.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }
}

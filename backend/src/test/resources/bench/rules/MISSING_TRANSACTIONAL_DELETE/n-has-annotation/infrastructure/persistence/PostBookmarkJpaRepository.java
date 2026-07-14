package infrastructure.persistence;

import org.springframework.transaction.annotation.Transactional;

public class PostBookmarkJpaRepository {
    @Transactional
    public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {
    }
}

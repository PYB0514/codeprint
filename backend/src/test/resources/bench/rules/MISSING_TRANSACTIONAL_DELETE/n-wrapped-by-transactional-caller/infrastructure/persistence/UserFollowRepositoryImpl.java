package infrastructure.persistence;

import org.springframework.transaction.annotation.Transactional;

public class UserFollowRepositoryImpl {
    private UserFollowJpaRepository jpaRepository;

    @Transactional
    public void deleteByFollowerIdAndFollowingId(java.util.UUID followerId, java.util.UUID followingId) {
        jpaRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }
}

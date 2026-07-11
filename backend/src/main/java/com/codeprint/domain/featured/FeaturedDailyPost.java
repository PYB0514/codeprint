// "오늘의 공개레포" 통합 게시글의 postId를 저장하는 싱글톤 엔티티(항상 id=1 단일 행)
package com.codeprint.domain.featured;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "featured_daily_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeaturedDailyPost {

    @Id
    private short id = 1;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    public static FeaturedDailyPost of(UUID postId) {
        FeaturedDailyPost state = new FeaturedDailyPost();
        state.postId = postId;
        return state;
    }
}

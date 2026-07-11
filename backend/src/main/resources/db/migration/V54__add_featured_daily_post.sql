-- 오늘의 공개레포 5개를 스냅샷으로 묶는 고정 게시글의 postId를 저장하는 싱글톤 테이블

CREATE TABLE featured_daily_post (
    id      SMALLINT PRIMARY KEY DEFAULT 1,
    post_id UUID NOT NULL,
    CONSTRAINT featured_daily_post_singleton CHECK (id = 1)
);

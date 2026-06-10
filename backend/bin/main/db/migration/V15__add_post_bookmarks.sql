-- 게시글 북마크(스크랩) 테이블
CREATE TABLE post_bookmarks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, post_id)
);

CREATE INDEX idx_post_bookmarks_user_id ON post_bookmarks(user_id);
CREATE INDEX idx_post_bookmarks_post_id ON post_bookmarks(post_id);

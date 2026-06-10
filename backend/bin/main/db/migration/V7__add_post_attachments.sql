-- 게시글 첨부파일 저장 테이블
CREATE TABLE post_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    s3_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_attachments_post_id ON post_attachments(post_id);

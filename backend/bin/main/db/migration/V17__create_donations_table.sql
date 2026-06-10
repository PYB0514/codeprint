-- 토스페이먼츠 후원(단건 결제) 내역을 저장하는 테이블
CREATE TABLE donations (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    payment_key VARCHAR(300) NOT NULL,
    order_id VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_donations_created_at ON donations(created_at DESC);

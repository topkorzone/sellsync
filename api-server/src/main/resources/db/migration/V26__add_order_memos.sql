-- 주문 메모 테이블
CREATE TABLE order_memos (
    memo_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    user_name VARCHAR(100),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_memos_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_memos_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_memos_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_order_memos_order_id ON order_memos(order_id);
CREATE INDEX idx_order_memos_created_at ON order_memos(created_at DESC);
CREATE INDEX idx_order_memos_tenant_id ON order_memos(tenant_id);

-- 주석 추가
COMMENT ON TABLE order_memos IS '주문 메모 (내부 관리용)';
COMMENT ON COLUMN order_memos.memo_id IS '메모 ID (UUID)';
COMMENT ON COLUMN order_memos.order_id IS '주문 ID';
COMMENT ON COLUMN order_memos.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN order_memos.user_id IS '작성자 ID';
COMMENT ON COLUMN order_memos.user_name IS '작성자명';
COMMENT ON COLUMN order_memos.content IS '메모 내용';
COMMENT ON COLUMN order_memos.created_at IS '생성일시';
COMMENT ON COLUMN order_memos.updated_at IS '수정일시';

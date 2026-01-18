-- 주문 상태 변경 이력 테이블
CREATE TABLE order_status_history (
    history_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by_system BOOLEAN NOT NULL DEFAULT false,
    changed_by_user_id UUID,
    changed_by_user_name VARCHAR(100),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 인덱스 생성
CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
CREATE INDEX idx_order_status_history_created_at ON order_status_history(created_at ASC);
CREATE INDEX idx_order_status_history_tenant_id ON order_status_history(tenant_id);

-- 주석 추가
COMMENT ON TABLE order_status_history IS '주문 상태 변경 이력';
COMMENT ON COLUMN order_status_history.history_id IS '이력 ID (UUID)';
COMMENT ON COLUMN order_status_history.order_id IS '주문 ID';
COMMENT ON COLUMN order_status_history.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN order_status_history.from_status IS '변경 전 상태';
COMMENT ON COLUMN order_status_history.to_status IS '변경 후 상태';
COMMENT ON COLUMN order_status_history.changed_by_system IS '시스템 자동 변경 여부';
COMMENT ON COLUMN order_status_history.changed_by_user_id IS '변경 사용자 ID';
COMMENT ON COLUMN order_status_history.changed_by_user_name IS '변경 사용자명';
COMMENT ON COLUMN order_status_history.note IS '변경 사유/메모';
COMMENT ON COLUMN order_status_history.created_at IS '변경일시';

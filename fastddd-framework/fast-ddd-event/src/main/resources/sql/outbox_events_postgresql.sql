-- Outbox事件表创建脚本 (PostgreSQL)
-- 用于实现Outbox模式，确保事件的可靠发布
--
-- @author gallop
-- @since 2025-01-09

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_data TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP NULL,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX idx_outbox_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_outbox_aggregate_type_id ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at);
CREATE INDEX idx_outbox_next_retry_at ON outbox_events(next_retry_at);
CREATE INDEX idx_outbox_retry_status ON outbox_events(retry_count, status);

-- 添加约束
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'));
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_retry_count
    CHECK (retry_count >= 0 AND retry_count <= max_retry_count);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_max_retry_count
    CHECK (max_retry_count >= 0 AND max_retry_count <= 10);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_version
    CHECK (version >= 0);

-- 字段注释 (PostgreSQL 使用 COMMENT ON 语法)
COMMENT ON TABLE outbox_events IS 'Outbox事件表';
COMMENT ON COLUMN outbox_events.id IS '主键ID';
COMMENT ON COLUMN outbox_events.event_id IS '事件唯一标识符';
COMMENT ON COLUMN outbox_events.aggregate_id IS '聚合根ID';
COMMENT ON COLUMN outbox_events.aggregate_type IS '聚合根类型';
COMMENT ON COLUMN outbox_events.event_type IS '事件类型';
COMMENT ON COLUMN outbox_events.event_data IS '事件数据（JSON格式）';
COMMENT ON COLUMN outbox_events.status IS '事件状态';
COMMENT ON COLUMN outbox_events.created_at IS '创建时间';
COMMENT ON COLUMN outbox_events.updated_at IS '更新时间';
COMMENT ON COLUMN outbox_events.published_at IS '发布时间';
COMMENT ON COLUMN outbox_events.retry_count IS '重试次数';
COMMENT ON COLUMN outbox_events.max_retry_count IS '最大重试次数';
COMMENT ON COLUMN outbox_events.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN outbox_events.error_message IS '错误信息';
COMMENT ON COLUMN outbox_events.version IS '版本号（乐观锁）';

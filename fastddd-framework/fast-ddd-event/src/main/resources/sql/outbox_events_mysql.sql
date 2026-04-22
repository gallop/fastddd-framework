-- Outbox事件表创建脚本 (MySQL)
-- 用于实现Outbox模式，确保事件的可靠发布
--
-- @author gallop
-- @since 2025-01-09

CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    event_id VARCHAR(36) NOT NULL UNIQUE COMMENT '事件唯一标识符',
    aggregate_id VARCHAR(100) NOT NULL COMMENT '聚合根ID',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '聚合根类型',
    event_type VARCHAR(200) NOT NULL COMMENT '事件类型',
    event_data TEXT NOT NULL COMMENT '事件数据（JSON格式）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '事件状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    published_at DATETIME NULL COMMENT '发布时间',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_at DATETIME NULL COMMENT '下次重试时间',
    error_message TEXT NULL COMMENT '错误信息',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox事件表';

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
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'CANCELLED'));
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_retry_count
    CHECK (retry_count >= 0 AND retry_count <= max_retry_count);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_max_retry_count
    CHECK (max_retry_count >= 0 AND max_retry_count <= 10);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_version
    CHECK (version >= 0);

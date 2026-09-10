-- Outbox 事件表（消费者自备 DDL，H2 MySQL 兼容模式）—— ECO-006C6 event-outbox 消费者场景。
-- 改编自框架 fast-ddd-event 测试树 sql/outbox_events_h2.sql（已验证形态）：
-- 消费者从框架 src/main/resources/sql/outbox_events_mysql.sql 出发为自有数据库适配 DDL，
-- 列/约束与框架实体契约对齐，去掉 H2 不识别的 ENGINE/CHARSET/COLLATE/ON UPDATE 子句。
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP NULL,
    error_message TEXT NULL,
    claimed_at TIMESTAMP NULL,
    lease_expires_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE outbox_events ADD CONSTRAINT IF NOT EXISTS chk_outbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'));
ALTER TABLE outbox_events ADD CONSTRAINT IF NOT EXISTS chk_outbox_retry_count
    CHECK (retry_count >= 0 AND retry_count <= max_retry_count);
ALTER TABLE outbox_events ADD CONSTRAINT IF NOT EXISTS chk_outbox_max_retry_count
    CHECK (max_retry_count >= 0 AND max_retry_count <= 10);
ALTER TABLE outbox_events ADD CONSTRAINT IF NOT EXISTS chk_outbox_version
    CHECK (version >= 0);

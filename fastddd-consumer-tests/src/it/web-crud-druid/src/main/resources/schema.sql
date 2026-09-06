-- ECO-006C4 web-crud-druid 消费者表（BasePO 审计字段 + 逻辑删除列）
CREATE TABLE IF NOT EXISTS c4_order (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    amount      INT NOT NULL,
    create_time TIMESTAMP NULL,
    update_time TIMESTAMP NULL,
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    deleted     TINYINT DEFAULT 0
);

-- ECO-006C4 starter-default-druid：Mapper CRUD 冒烟用表（对齐 BasePO 审计/逻辑删除列契约）
CREATE TABLE c4_stock (
    id           BIGINT       NOT NULL PRIMARY KEY,
    sku          VARCHAR(64)  NOT NULL,
    quantity     INT          NOT NULL,
    create_time  TIMESTAMP    NULL,
    update_time  TIMESTAMP    NULL,
    create_user  VARCHAR(64)  NULL,
    update_user  VARCHAR(64)  NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

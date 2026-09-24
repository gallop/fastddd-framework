-- FastDDD identity reference schema V1（W0-2 §3.1 数据字典，CW-02 修订后口径）
-- 存储列统一 VARCHAR(64)：主体公开 ID 文本协议上限 50（UUID 文本 36 / system-job: ≤50 / system），列宽留裕量；
-- 禁止截断与适配器自行变换格式；引用列与主表同宽同源。
-- 宿主拥有表与迁移：本文件为参考迁移，可直接纳入宿主迁移工具链；
-- fast-ddd-identity-reference-pg 的启动初始化器默认关闭（fastddd.identity.pg.initialize-schema=false）。

CREATE TABLE IF NOT EXISTS principal (
    principal_id   VARCHAR(64)   PRIMARY KEY,
    tenant_id      VARCHAR(32)   NOT NULL,
    principal_type VARCHAR(16)   NOT NULL CHECK (principal_type IN ('HUMAN', 'SYSTEM_JOB', 'SYSTEM')),
    status         VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    auth_epoch     BIGINT        NOT NULL DEFAULT 0,
    display_name   VARCHAR(100),
    login_name     VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE principal IS '本地主体权威存储（唯一主体库；目录镜像 person 不自动创建可登录主体）';
COMMENT ON COLUMN principal.auth_epoch IS '认证代数：停用/启用条件递增，会话按签发时快照匹配';

CREATE TABLE IF NOT EXISTS external_binding (
    provider         VARCHAR(32)  NOT NULL,
    tenant_id        VARCHAR(32)  NOT NULL,
    app_scope        VARCHAR(64)  NOT NULL,
    external_id_type VARCHAR(32)  NOT NULL,
    external_id      VARCHAR(128) NOT NULL,
    principal_id     VARCHAR(64)  NOT NULL REFERENCES principal (principal_id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_external_binding_scope UNIQUE (provider, tenant_id, app_scope, external_id_type, external_id)
);

COMMENT ON TABLE external_binding IS '外部身份绑定：作用域五元组唯一约束裁决并发首次登录';

CREATE TABLE IF NOT EXISTS local_session (
    session_id            VARCHAR(64) PRIMARY KEY,
    principal_id          VARCHAR(64) NOT NULL REFERENCES principal (principal_id),
    tenant_id             VARCHAR(32) NOT NULL,
    auth_epoch_at_issue   BIGINT      NOT NULL,
    session_format_version INT        NOT NULL,
    issued_at             TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_local_session_principal ON local_session (principal_id);

COMMENT ON TABLE local_session IS '不透明本地会话：验证 = 会话行 JOIN 主体行（ACTIVE + epoch 相等 + 未过期未撤销）';

-- 登录 state（W2/W2-2 浏览器回调防重放；W2-1 仅交付 DDL，适配器随 W2-2 落地）
CREATE TABLE IF NOT EXISTS login_state (
    state        VARCHAR(64) PRIMARY KEY,
    redirect_uri VARCHAR(500),
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ
);

-- 外部拒绝事实（§8.2：上游停用拒绝记录；W2-1 仅交付 DDL，写入路径随 W5 增量同步落地）
CREATE TABLE IF NOT EXISTS external_rejection (
    provider         VARCHAR(32)  NOT NULL,
    tenant_id        VARCHAR(32)  NOT NULL,
    app_scope        VARCHAR(64)  NOT NULL,
    external_id_type VARCHAR(32)  NOT NULL,
    external_id      VARCHAR(128) NOT NULL,
    reason           VARCHAR(200) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cleared_at       TIMESTAMPTZ,
    CONSTRAINT uk_external_rejection_scope UNIQUE (provider, tenant_id, app_scope, external_id_type, external_id)
);

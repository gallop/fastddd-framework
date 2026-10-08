-- ============================================================
-- W5 目录增量与组织变化（W5 设计 v4 §4.1；W5-b 交付）
-- 前置：V3__directory_mirror.sql（metadata 水位与事实表；本脚本不依赖 V2）。
-- 宿主拥有迁移：框架默认 initialize-schema=false；宿主将本脚本并入
-- 自身迁移序列并分配全局版本（V1 头部约定同样适用）。
-- 宽度约定：tenant_id VARCHAR(32) / app_scope VARCHAR(64) 与 V1/V3 对齐；
-- event_id VARCHAR(128) 与 V1 external_id 对齐；变更日志 id / generation_id
-- VARCHAR(26)（ULID Crockford base32 定长，调用方预铸——P2-5）。
-- 不可变性：directory_change_log 代码零 UPDATE 面 + 守护测试承载（D-3），
-- 不引入触发器。
-- ============================================================

-- ------------------------------------------------------------
-- 入站事件收件箱：先落库后 ACK，租约式消费（W5 设计 §4.1/§5.1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_event_inbox (
    event_id         VARCHAR(128)  NOT NULL,
    tenant_id        VARCHAR(32)   NOT NULL,
    app_scope        VARCHAR(64)   NOT NULL,
    event_type       VARCHAR(64)   NOT NULL,
    payload          JSONB         NOT NULL,
    received_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    state            VARCHAR(16)   NOT NULL,
    attempts         INT           NOT NULL DEFAULT 0,
    last_error       VARCHAR(255)  NULL,
    processed_at     TIMESTAMPTZ   NULL,
    claimed_at       TIMESTAMPTZ   NULL,
    lease_expires_at TIMESTAMPTZ   NULL,
    CONSTRAINT pk_directory_event_inbox PRIMARY KEY (event_id),
    CONSTRAINT ck_directory_event_inbox_state
        CHECK (state IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

-- 消费轮询：RECEIVED 按接收序择取（claim 置 PROCESSING + 租约）
CREATE INDEX IF NOT EXISTS idx_directory_event_inbox_dispatch
    ON directory_event_inbox (state, received_at);

-- 崩溃恢复：过期 PROCESSING 行扫描（reclaimExpired 复位 RECEIVED、attempts+1）
CREATE INDEX IF NOT EXISTS idx_directory_event_inbox_lease
    ON directory_event_inbox (state, lease_expires_at);

COMMENT ON TABLE directory_event_inbox IS '入站事件收件箱：飞书事件订阅持久化接收（验签→解密→防重放通过后先落库再ACK），eventId 主键幂等，租约式消费状态机（W5 设计 §4.1/§5.1）';
COMMENT ON COLUMN directory_event_inbox.event_id IS '事件幂等键（上游事件 id；重复投递 persist 冲突→仍 ACK 不再处理）';
COMMENT ON COLUMN directory_event_inbox.event_type IS '事件类型词表值（{type}，W5-b 冻结词表；VARCHAR(64) 容纳限定名形态）';
COMMENT ON COLUMN directory_event_inbox.payload IS '原始解密后事件体（JSONB；处理只作触发证据，事实以回查为准——P-8）';
COMMENT ON COLUMN directory_event_inbox.state IS '消费状态：RECEIVED/PROCESSING/PROCESSED/FAILED（FAILED=attempts 耗尽终态，仅管理端显式重放）';
COMMENT ON COLUMN directory_event_inbox.attempts IS '消费尝试次数（claim/复位累计；上限由实现配置）';
COMMENT ON COLUMN directory_event_inbox.last_error IS '最近失败原因摘要（≤255；诊断用，不含凭据）';

-- ------------------------------------------------------------
-- 不可变变更日志：目录域水位推进事务的 append-only 载体（W5 设计 §3.3/§4.1，D-3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_change_log (
    id            VARCHAR(26)   NOT NULL,
    tenant_id     VARCHAR(32)   NOT NULL,
    app_scope     VARCHAR(64)   NOT NULL,
    kind          VARCHAR(24)   NOT NULL,
    generation_id VARCHAR(26)   NULL,
    committed_seq BIGINT        NOT NULL,
    summary       JSONB         NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_directory_change_log PRIMARY KEY (id),
    CONSTRAINT ck_directory_change_log_kind
        CHECK (kind IN ('GENERATION_ACTIVATED', 'INCREMENT_APPLIED', 'RECHECK_COMMITTED'))
);

-- 同 scope 同代的代激活行至多一条（T8 no-op 判据 hasGenerationActivation 的索引承载）
CREATE UNIQUE INDEX IF NOT EXISTS uk_directory_change_log_generation
    ON directory_change_log (tenant_id, app_scope, generation_id)
    WHERE kind = 'GENERATION_ACTIVATED';

-- 增量/回查行按类别+提交水位至多一条（消费侧对账锚；代激活行不占用水位唯一域）
CREATE UNIQUE INDEX IF NOT EXISTS uk_directory_change_log_committed
    ON directory_change_log (tenant_id, app_scope, kind, committed_seq)
    WHERE kind IN ('INCREMENT_APPLIED', 'RECHECK_COMMITTED');

-- 水位序扫描（对账/巡检读取面）
CREATE INDEX IF NOT EXISTS idx_directory_change_log_committed
    ON directory_change_log (tenant_id, app_scope, committed_seq);

COMMENT ON TABLE directory_change_log IS '不可变变更日志：目录域每次水位推进的提交事务（代激活/增量落库/回查提交）各写一行，append-only——代码零UPDATE面+守护测试（D-3），事件与事实行经 changeLogRef 引用本表条目（§5.3.3 引用列≠日志本身）';
COMMENT ON COLUMN directory_change_log.id IS '条目 id（调用方预铸 ULID 26 字符——P2-5 先铸后写；append 返回值为 changeLogRef）';
COMMENT ON COLUMN directory_change_log.kind IS '条目类别：GENERATION_ACTIVATED/INCREMENT_APPLIED/RECHECK_COMMITTED';
COMMENT ON COLUMN directory_change_log.generation_id IS '代 id（仅 GENERATION_ACTIVATED 行非 NULL；部分唯一约束锚）';
COMMENT ON COLUMN directory_change_log.committed_seq IS '本事务提交后水位 H_after（BIGINT，与 metadata.committed_seq 同轴）';
COMMENT ON COLUMN directory_change_log.summary IS '紧凑 JSON 摘要（无 PII；数值性水位/计数等诊断字段）';

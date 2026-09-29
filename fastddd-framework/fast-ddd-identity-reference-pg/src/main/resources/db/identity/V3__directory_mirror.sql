-- ============================================================
-- W3 目录镜像存储（W3 设计 v3 §5.1；W3-c 交付）
-- 前置：V1__identity_core.sql（本脚本不依赖 V2__login_state_browser_binding.sql）。
-- 宿主拥有迁移：框架默认 initialize-schema=false；宿主将本脚本并入
-- 自身迁移序列并分配全局版本（V1 头部约定同样适用）。
-- 宽度约定：tenant_id VARCHAR(32) / app_scope VARCHAR(64) 与 V1
-- external_binding 对齐；外部对象键（open_id / open_department_id）
-- VARCHAR(128) 与 V1 external_id 对齐；代号 ULID VARCHAR(26)
-- （Crockford base32，26 字符定长）；token/fingerprint 十六进制 64。
-- ============================================================

-- ------------------------------------------------------------
-- 目录同步代：一次全量扫描的不可变快照构建 + 原子发布单元（W3 设计 §4 状态机）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_generation (
    id                   VARCHAR(26)  NOT NULL,
    tenant_id            VARCHAR(32)  NOT NULL,
    app_scope            VARCHAR(64)  NOT NULL,
    state                VARCHAR(16)  NOT NULL,
    start_seq            BIGINT       NOT NULL,
    scope_fingerprint    VARCHAR(64)  NOT NULL,
    observed_scope       JSONB        NULL,
    premerged_seq        BIGINT       NULL,
    lease_token          VARCHAR(64)  NULL,
    lease_expires_at     TIMESTAMPTZ  NULL,
    checkpoint           JSONB        NULL,
    pages_done           BIGINT       NOT NULL DEFAULT 0,
    pages_failed         BIGINT       NOT NULL DEFAULT 0,
    validation_report    JSONB        NULL,
    abandon_reason       VARCHAR(32)  NULL,
    pub_committed_before BIGINT       NULL,
    pub_committed_after  BIGINT       NULL,
    pub_applied_seq      BIGINT       NULL,
    pub_facts_inserted   BIGINT       NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_directory_generation PRIMARY KEY (id),
    CONSTRAINT ck_directory_generation_state
        CHECK (state IN ('BUILDING', 'VALIDATING', 'VALIDATED', 'PUBLISHED', 'ABANDONED'))
);

-- 每 scope 至多一个非终态代（T6a 接管判据的索引承载，W3 设计 §5.1）
CREATE UNIQUE INDEX IF NOT EXISTS uk_directory_generation_open
    ON directory_generation (tenant_id, app_scope)
    WHERE state IN ('BUILDING', 'VALIDATING', 'VALIDATED');

COMMENT ON TABLE directory_generation IS '目录同步代：一次全量扫描的不可变快照构建+原子发布单元（构建代全程不可见，读取一律经发布元数据活动代指针）';
COMMENT ON COLUMN directory_generation.id IS '代 id（ULID 26 字符：48bit 时间戳+80bit 随机；观察快照版本，非计数器——与事实序号职责分离 §6.2）';
COMMENT ON COLUMN directory_generation.state IS '代状态（§4 状态机）：BUILDING/VALIDATING/VALIDATED/PUBLISHED/ABANDONED';
COMMENT ON COLUMN directory_generation.start_seq IS 'begin 事务内读取的 metadata.committed_seq（§6.2）';
COMMENT ON COLUMN directory_generation.scope_fingerprint IS '配置范围指纹 sha256(tenantKey\nappScope)——只反映配置，不证明上游权限范围（§6.4）';
COMMENT ON COLUMN directory_generation.observed_scope IS 'completePaging 冻结的实得范围证据：根部门 id 集+实际遍历部门 id 集+三流完成标记（JSONB）';
COMMENT ON COLUMN directory_generation.premerged_seq IS '预合并水位 h（M1 恒 NULL，列冻结；M2 增量合并启用 §5.1）';
COMMENT ON COLUMN directory_generation.lease_token IS '获取时快照（权威值在 directory_publish_metadata；接管判据以权威值为准）';
COMMENT ON COLUMN directory_generation.lease_expires_at IS '获取时快照（权威值在 directory_publish_metadata）';
COMMENT ON COLUMN directory_generation.checkpoint IS '分页游标/检查点（JSONB 无硬容量上限；M1 编排持有游标、失败整代废弃不落库，列预留 W3-b 观察 3）';
COMMENT ON COLUMN directory_generation.pages_done IS '已写入页批数（累计）';
COMMENT ON COLUMN directory_generation.pages_failed IS '失败页数（M1 失败即整代废弃，本列恒 0，预留）';
COMMENT ON COLUMN directory_generation.validation_report IS 'validate 通过报告（checks+三流计数；VALIDATED 前置证据）';
COMMENT ON COLUMN directory_generation.abandon_reason IS '废弃原因稳定码（≤32 字符，编排 reasonOf 分类）';
COMMENT ON COLUMN directory_generation.pub_committed_before IS 'T7 幂等发布全等返回的持久化依据：H_before（本列四项仅在 state=PUBLISHED 时非 NULL，W3-c 新增列，§5.1 未列——见 W3-c 执行记录登记）';
COMMENT ON COLUMN directory_generation.pub_committed_after IS 'T7 幂等发布全等返回的持久化依据：H_after';
COMMENT ON COLUMN directory_generation.pub_applied_seq IS 'T7 幂等发布全等返回的持久化依据：applied_seq（= H_after）';
COMMENT ON COLUMN directory_generation.pub_facts_inserted IS 'T7 幂等发布全等返回的持久化依据：本事务新增对象键数（规则 A）';

-- ------------------------------------------------------------
-- 构建代三镜像（仅新代可见；发布后成为"活动代 raw"）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_person_mirror (
    generation_id  VARCHAR(26)  NOT NULL,
    open_id        VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NULL,
    en_name        VARCHAR(255) NULL,
    department_ids JSONB        NOT NULL,
    CONSTRAINT pk_directory_person_mirror PRIMARY KEY (generation_id, open_id)
);

COMMENT ON TABLE directory_person_mirror IS '构建代人员镜像（观察快照层：本代观察到什么；写入 ON CONFLICT DO NOTHING=首见胜出）';
COMMENT ON COLUMN directory_person_mirror.department_ids IS '任职部门 id 集（JSON 数组；与 directory_relation_mirror 同步派生）';

CREATE TABLE IF NOT EXISTS directory_department_mirror (
    generation_id        VARCHAR(26)  NOT NULL,
    department_id        VARCHAR(128) NOT NULL,
    parent_department_id VARCHAR(128) NULL,
    name                 VARCHAR(255) NULL,
    CONSTRAINT pk_directory_department_mirror PRIMARY KEY (generation_id, department_id)
);

COMMENT ON TABLE directory_department_mirror IS '构建代部门镜像（域类型仅 3 字段：id/父/name——只持久化域携带字段；member_count 不虚构）';
COMMENT ON COLUMN directory_department_mirror.parent_department_id IS '根部门为 NULL（源侧自引用已归一化）';

CREATE TABLE IF NOT EXISTS directory_relation_mirror (
    generation_id  VARCHAR(26)  NOT NULL,
    open_id        VARCHAR(128) NOT NULL,
    department_id  VARCHAR(128) NOT NULL,
    CONSTRAINT pk_directory_relation_mirror PRIMARY KEY (generation_id, open_id, department_id)
);

COMMENT ON TABLE directory_relation_mirror IS '任职关系镜像（person×department 派生流；源侧多部门透传不去重，本表 ON CONFLICT DO NOTHING 去重=W3-b 决策 12 归 W3-c 承接）';

-- ------------------------------------------------------------
-- 发布元数据：每 scope 单行——一切目录操作的第一把锁（SELECT FOR UPDATE，
-- 锁序固定 = metadata 行 → generation 行 → 镜像/fact 行，W3 设计 §5.2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_publish_metadata (
    tenant_id         VARCHAR(32) NOT NULL,
    app_scope         VARCHAR(64) NOT NULL,
    active_generation VARCHAR(26) NULL,
    committed_seq     BIGINT      NOT NULL DEFAULT 0,
    applied_seq       BIGINT      NOT NULL DEFAULT 0,
    lease_token       VARCHAR(64) NULL,
    lease_expires_at  TIMESTAMPTZ NULL,
    lease_holder      VARCHAR(64) NULL,
    scope_suspicion   JSONB       NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_directory_publish_metadata PRIMARY KEY (tenant_id, app_scope)
);

COMMENT ON TABLE directory_publish_metadata IS '目录发布元数据（每 scope 单行）：活动代指针+事实线水位+同步租约权威值+范围级待核对承载。T6a/T6b/T7 一切操作的第一把锁（SELECT FOR UPDATE）';
COMMENT ON COLUMN directory_publish_metadata.active_generation IS '活动代指针（T7 切换；NULL=尚未发布过）';
COMMENT ON COLUMN directory_publish_metadata.committed_seq IS '事实线水位 H：仅事实写入事务推进（一次 T7=一次事实提交，空差异发布不推进，§6.2）';
COMMENT ON COLUMN directory_publish_metadata.applied_seq IS '最终已应用水位（= 最近一次发布的 H_after；无变更发布=重写同值）';
COMMENT ON COLUMN directory_publish_metadata.lease_token IS '同步租约 token 权威值（≥128bit SecureRandom hex；NULL=无持有者）';
COMMENT ON COLUMN directory_publish_metadata.lease_expires_at IS '租约有效期权威值（墙钟仅存活判定，绝不进入顺序语义）';
COMMENT ON COLUMN directory_publish_metadata.lease_holder IS '持有者标识（M1 恒 directory-sync：调度与手动触发共用租约互斥）';
COMMENT ON COLUMN directory_publish_metadata.scope_suspicion IS '范围级待核对登记（scope-suspect 可查询承载，每次 publish 重算整体替换；ObservedSnapshot.scopeSuspicions 的存储承载——W3-c 解释性新增列，§5.1 未列，见 W3-c 执行记录登记）';

-- ------------------------------------------------------------
-- 受保护事实层：追加式对象键账本（M1 写入方仅规则 A INSERT 与规则 B
-- pending_confirm 置位/复位；墓碑/origin 变更/change_ref 冻结至 M2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS directory_fact (
    tenant_id         VARCHAR(32)  NOT NULL,
    app_scope         VARCHAR(64)  NOT NULL,
    object_type       VARCHAR(16)  NOT NULL,
    external_id       VARCHAR(128) NOT NULL,
    local_revision    BIGINT       NOT NULL DEFAULT 1,
    fact              JSONB        NOT NULL,
    tombstone         BOOLEAN      NOT NULL DEFAULT false,
    origin            VARCHAR(12)  NOT NULL DEFAULT 'FULL_SCAN',
    change_ref        VARCHAR(64)  NULL,
    pending_confirm   BOOLEAN      NOT NULL DEFAULT false,
    source_generation VARCHAR(26)  NOT NULL,
    updated_seq       BIGINT       NOT NULL,
    CONSTRAINT pk_directory_fact PRIMARY KEY (tenant_id, app_scope, object_type, external_id),
    CONSTRAINT ck_directory_fact_object_type CHECK (object_type IN ('PERSON', 'DEPARTMENT')),
    CONSTRAINT ck_directory_fact_origin CHECK (origin IN ('FULL_SCAN', 'RECHECK', 'INCREMENT'))
);

-- 待核对清单查询（差异报表 readProtectedFacts pending 过滤面）
CREATE INDEX IF NOT EXISTS idx_directory_fact_pending
    ON directory_fact (tenant_id, app_scope, updated_seq)
    WHERE pending_confirm;

COMMENT ON TABLE directory_fact IS '受保护事实层：对象键账本（只进管理视图不进授权路径；已有行只能由明确证据变更（M2），扫描只能新增+登记待核对 §6.3 总则）';
COMMENT ON COLUMN directory_fact.object_type IS '目录对象类型：PERSON（external_id=open_id）/DEPARTMENT（external_id=department_id）';
COMMENT ON COLUMN directory_fact.local_revision IS '每次被接受的证据门控变更 +1；M1 新增时=1，此后不再修改';
COMMENT ON COLUMN directory_fact.fact IS '首次观察锚点快照（对象键与首次观察到的固定形态；Java 侧只发射固定形态 JSON，读取解析推给 PG jsonb）';
COMMENT ON COLUMN directory_fact.tombstone IS '受保护"确定删除"事实；M1 恒 false（无写入方，§6.4——种子墓碑仅测试可置位，矩阵 15 墓碑对峙零修改不复活）';
COMMENT ON COLUMN directory_fact.origin IS '事实来源；M1 恒 FULL_SCAN（RECHECK/INCREMENT 为 M2 增量/回查路径，列冻结）';
COMMENT ON COLUMN directory_fact.change_ref IS '不可变变更日志条目引用（M2 写入；M1 恒 NULL，列冻结）';
COMMENT ON COLUMN directory_fact.pending_confirm IS '观察层待核对标记（scan-missing 置位/再观察到或 M2 回查复位；置位/复位不动 revision/updated_seq/tombstone、不推事实水位）';
COMMENT ON COLUMN directory_fact.source_generation IS '首次观察到该对象键的代 id';
COMMENT ON COLUMN directory_fact.updated_seq IS '首次落库时的事实线序号（= 当次 T7 的 H_after；同事务新写事实共享同一序值）';

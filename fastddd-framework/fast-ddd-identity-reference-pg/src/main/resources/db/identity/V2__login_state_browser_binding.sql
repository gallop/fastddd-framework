-- W2-2 登录授权上下文增量（追加式迁移：只增列不改写 V1，已执行过 V1 的库可安全追加）。
-- login_state 在 W2-1 仅交付 DDL；W2-2 启用读写并补充：
--   code_verifier       PKCE S256 verifier（RFC 7636：43–128 字符），与 state 一一对应，不入日志
--   browser_binding     浏览器绑定随机值（授权发起经 HttpOnly Cookie 下发，回调必须回带匹配）
--   post_login_redirect 登录成功后的站内回跳地址（服务端白名单裁决结果）
--   created_at          签发时刻（审计定位）
-- V1 的 redirect_uri 列保持不动：授权回调登记地址为应用级配置
-- （fastddd.identity.feishu.redirect-uri），非 per-state 数据；本迁移不回填。
-- 过期回收索引（W22-RV-01）：PgLoginStateStore.save 惰性清理
-- DELETE FROM login_state WHERE expires_at < ?（now - retention）走此索引；
-- 授权发起是 permitAll 匿名写入口，无回收机制 = 无界表膨胀（复评 W22-RV-01）。
-- 宿主注意（W22-R2 观察-1）：CREATE INDEX 非 CONCURRENTLY——存量大表上手动执行会
-- 短暂阻塞写；大表场景可先行以 CONCURRENTLY 建同名索引，本语句 IF NOT EXISTS 将跳过。
ALTER TABLE login_state ADD COLUMN IF NOT EXISTS code_verifier       VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE login_state ADD COLUMN IF NOT EXISTS browser_binding     VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE login_state ADD COLUMN IF NOT EXISTS post_login_redirect VARCHAR(500);
ALTER TABLE login_state ADD COLUMN IF NOT EXISTS created_at          TIMESTAMPTZ  NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS idx_login_state_expires_at ON login_state (expires_at);

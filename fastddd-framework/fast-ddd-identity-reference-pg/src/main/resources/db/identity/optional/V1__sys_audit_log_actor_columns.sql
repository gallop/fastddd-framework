-- 可选迁移：身份模式审计 actor 列（fastddd.identity.audit.actor-columns=true 前置）
-- 基表 sys_audit_log 由宿主/框架拥有（形态见 security-starter AuditLogServiceImpl DDL）；
-- actor 列与主体 ID 协议同源（VARCHAR(64)，见 W0-2 §3.0/§3.3）。

ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS actor_principal_id VARCHAR(64);
ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS actor_type VARCHAR(16);

# ADR-004：安全/JWT 模型（令牌类型隔离 + 吊销 SPI + 多实例吊销 + 关闭开关）

- **状态**：已冻结（FRM-001 架构 review）
- **日期**：2026-08-11
- **关联**：审计 P0-7（JWT 关不掉/弱密钥）；commit `7aaab46` 已落地加固
- **决策依据**：逐行核验 `7aaab46` 加固成果 + P0-7 残留缺口

---

## 1. 背景（现状，代码级证据）

commit `7aaab46` 已落地以下加固，**逐项确认仍存在于当前 HEAD**：

- **令牌类型隔离**：`JwtTokenProvider`（`JwtTokenProvider.java:126,138,215-227`）access/refresh 区分 `typ` 声明 + `jti` 唯一标识 + 校验时强校验 typ。
- **吊销 SPI**：`JwtTokenRevocationStore`（`JwtTokenRevocationStore.java:11-20`）接口；`InMemoryJwtTokenRevocationStore`（`:17-19`）fail-closed（`null → true`，即查不到按已吊销处理，安全侧失败闭合）。
- **密钥强度**：`JwtProperties`（`JwtProperties.java:21,26,78-88`）`MINIMUM_HMAC_SECRET_BYTES=64`，`secret` 默认 `null`（不内置弱默认）。
- **登出/刷新轮转**：`AuthController`（`:120-185`）logout 写吊销、refresh 走 `consumeRefreshToken` 首调用者赢（rotation）。

**P0-7 残留缺口（承重，GA 前必修）**：

- `FastDddSecurityAutoConfiguration.java:68` **无 class 级门禁**，`jwtSigner` bean（`:78-83`）在 `fastddd.security.jwt.enabled=false` 时仍被构造。若此时 JWT 依赖缺失或密钥未配，**启动直接失败**——即"安全模块关不掉"。与 ADR-002 §2.2 联动必须修复。
- **多实例吊销盲区**：`InMemoryJwtTokenRevocationStore` 进程内存储，多实例部署下实例 A 吊销的令牌在实例 B 仍有效。fail-closed 只保证"查到已吊销"，不解决"查不到但实际已吊销"。需 Redis/共享存储后端。

**范围澄清**：审计中"SEC-002 资源服务器/auth-server"**不是审计的字面编号**（审计用 P0/P1/P2），而是整改方案对 OAuth2 资源服务器/独立 auth-server 的延后项，归入阶段 C/D，**不在 1.0 GA 范围**。本 ADR 仅覆盖框架内嵌 JWT（access/refresh + 吊销）。

## 2. 决策

**2.1 彻底关闭 P0-7（与 ADR-002 联动）**

- security 自动配置类加 class 级 `@ConditionalOnProperty(fastddd.security.mode != DISABLED)` + `@ConditionalOnClass(JWT 核心类)`。
- `jwtSigner`/`JwtTokenProvider`/`JwtTokenValidator` bean 全部受上述门禁；`mode=DISABLED` 时完全不装配，`enabled=false` 作为兼容别名映射到 DISABLED。
- `mode=ENABLED` 且密钥未达 64 字节 → fail-fast（FailureAnalyzer 给出"密钥长度不足/未配置"可读诊断）。

**2.2 吊销后端 SPI 化，1.0 提供共享存储实现**

- `JwtTokenRevocationStore` 保持为公共 SPI。
- 1.0 GA 提供：①`InMemoryJwtTokenRevocationStore`（单实例/开发，fail-closed）②`RedisJwtTokenRevocationStore`（多实例生产）。
- 默认 `AUTO`：检测到 Redis bean 用 Redis 实现，否则 InMemory 并**启动告警**（WARN：多实例部署下吊销不全局生效）。

**2.3 令牌模型冻结**

- access token：短 TTL、`typ=access`、含 `jti`、可吊销。
- refresh token：长 TTL、`typ=refresh`、`jti`、一次性轮转（`consumeRefreshToken` 首调用者赢）、可吊销。
- HMAC 密钥 ≥64 字节硬门禁；不内置默认密钥。

## 3. 替代方案

- **备选 B（仅补 class 级门禁，不引入 Redis 吊销）**：关 P0-7 的"关不掉"，但多实例吊销盲区留到 1.1。鉴于安全缺陷不宜拖延，且 Redis 已是框架既有依赖（cache 模块），1.0 直接提供 Redis 实现成本可控。不采用。
- **备选 C（引入 OAuth2 资源服务器作为 1.0 默认）**：审计与方案均把 auth-server 归入阶段 C/D，1.0 内嵌 JWT 已满足框架自带鉴权需求。不采用，保持延后。

## 4. 后果与迁移

- **公共契约**：`JwtTokenRevocationStore` SPI、`RedisJwtTokenRevocationStore`、`fastddd.security.mode` 属性、`JwtProperties` 密钥门禁为公共契约。
- **行为变化（bugfix 性质）**：`enabled=false` 真正不装配 security bean（修复 P0-7）；密钥不足 64 字节从"可能运行"变为"启动失败"（安全收紧）。
- **运维变化**：多实例生产部署须配 Redis 吊销后端；单实例可用 InMemory。

## 5. 1.0 GA 范围 vs 延后

- **1.0 GA**：security class 级门禁（关闭 P0-7）、`JwtTokenRevocationStore` SPI + InMemory + Redis 实现、令牌类型隔离/轮转/密钥门禁（`7aaab46` 已落地）、FailureAnalyzer。
- **延后（阶段 C/D，1.1+）**：OAuth2 资源服务器、独立 auth-server、非对称签名（RS/ES）密钥轮转生态。

## 6. 对内部 RC / 公开 GA 的约束

- **RC**：`mode` 三态回归；密钥门禁 fail-fast 验证；多实例吊销一致性（Redis 后端，Testcontainers 本地跑）；refresh 轮转并发首调用者赢。
- **GA**：`JwtTokenRevocationStore` SPI、`fastddd.security.mode`、密钥门禁规则为公共契约；access/refresh 令牌结构与 `typ`/`jti` 语义冻结。

## 7. 参考

- 研究证据：审计 P0-7（行 286-296）；commit `7aaab46` 加固逐项核验
- 关联 ADR：ADR-002（security class 级门禁与本 ADR 共同关闭 P0-7）、ADR-001（ID 不影响 JWT 模型，`jti` 为字符串独立于实体 ID）

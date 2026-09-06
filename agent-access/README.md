# FastDDD Agent Access (Experimental)

Agent Access 是 Framework Community 仓内的试验契约线，用于让外部 Agent 通过受控 permit、资源状态校验和可验证摘要访问 FastDDD 能力。

它具有独立版本轴 `0.1.0-jdk21-alpha.1`，不属于 Framework 1.0 稳定 API，也不阻塞 Framework GA。

## 模块

- `fastddd-agent-access`：核心契约与校验；
- `fastddd-agent-access-contract-kit`：消费者契约测试工具；
- `fastddd-agent-access-bom`：试验制品版本对齐；
- `fastddd-agent-access-carrier-conformance`：仅在显式 profile 下验证 FastAgent carrier。

## 构建

```bash
../mvnw -f pom.xml clean verify
```

默认构建不依赖 FastAgent。真实 carrier 验证需显式启用 `carrier-conformance` profile，并提前提供相应候选制品。

## 兼容与许可

契约、wire schema、digest schema 和 Maven 制品版本互不推导，变更规则见 Framework 仓内 Agent Access ADR 与 contract 文档。本试验线使用 Apache License 2.0，见本目录 `LICENSE` 与 `NOTICE`。

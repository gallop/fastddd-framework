# Framework 独立消费者契约测试

本模块使用 `maven-invoker-plugin` 在独立 Maven 进程中构建最小消费者，验证 Framework 制品从 Maven 仓库解析后的真实外部使用方式。测试工程不读取相邻 Tooling/Studio 源码，也不继承主 reactor 的 `target/classes`。

## 运行

```bash
./mvnw -B clean install -DskipTests
./mvnw -B verify -pl fastddd-consumer-tests -Pconsumer-tests
```

默认 reactor 构建会跳过 invoker 场景；只有显式启用 `consumer-tests` profile 才执行全部 Framework 契约。每个场景的日志保存在 `target/its/<scenario>/build.log`。

## 场景

| 领域 | 场景 |
|---|---|
| BOM | `bom-revision-isolation` |
| Web | `web-minimal`、`web-without-application`、`web-response-wrapper`、`web-response-default-off` |
| Security | `security-minimal`、`security-with-custom-config` |
| Cache | `cache-minimal`、`cache-with-custom-manager` |
| Search | `search-minimal` |
| Event | `event-minimal`、`event-nodb`、`event-outbox`、`event-rocketmq-e2e`（显式门控 `-Dfastddd.test.rocketmq=true`，默认跳过，需真实 Broker） |
| Monitor | `monitor-minimal` |

ECO-005 已将 `fastdoc-minimal` 移交给 `fastddd-tooling-consumer-tests`，并删除失效的 Studio 相对路径 profile。Framework 的消费者门禁只验证 Framework Community 制品。

2026-08-31 本地验收：12 个场景全部通过，`Passed: 12, Failed: 0, Errors: 0, Skipped: 0`。

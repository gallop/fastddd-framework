# FastDDD Community 产品边界

FastDDD Framework 与 FastDDD Tooling 是相互独立、版本独立的 Community 产品；FastDDD Studio Pro 是独立的闭源商业产品。

## 允许的依赖方向

```text
业务项目 ───────────────> Framework Community
Tooling Community ──────> Framework Community
Studio Pro ─────────────> Tooling Community ─────> Framework Community
FastAgent Adapter ──────> Agent Access Contract (Experimental)
```

禁止 Framework 依赖 Tooling、Studio Pro 或 FastAgent，禁止 Tooling 依赖 Studio Pro。业务项目可以使用 Studio Pro 进行设计和治理，但生成后的业务代码不得以 Studio Pro 作为运行时前提。

## 许可边界

- Framework Community：Apache License 2.0；
- Tooling Community：Apache License 2.0；
- Studio Pro：Proprietary，Community 许可证不覆盖其源码、UI、部署资产和商业能力；
- Agent Access：Framework 仓内 Experimental Community 契约，版本与 Framework 主线独立。

当前不建立 Enterprise Framework Core。若未来出现必须由企业运行时扩展承载的真实需求，也应作为依赖 Community 的可选扩展，不形成 Framework 私有分叉。

跨产品商业计划、消费者全量清单和 Studio 内部路线不进入 Community 仓库。

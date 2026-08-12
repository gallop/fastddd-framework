# SPLIT-006 迁移映射

**源仓库**：`D:\usr\workspace_2026\fastddd`，分支 `refactor/fastddd-1.0-framework`  
**目标仓库**：`fastddd-framework`  
**迁移方式**：按路径筛选 Git 历史，保留 Framework、BOM、消费者测试与文档相关提交；目标提交哈希因历史重写而变化。

| 项目 | 值 |
|---|---|
| 源仓迁移起点 | `9e080dc` |
| 过滤后迁移基线 | `69e6c9c` |
| 独立仓入口提交 | `a59fb26` |
| 完整提交映射 | `.git/filter-repo/commit-map`（本地迁移工作区保留） |

若需追溯任一历史提交，请使用本地迁移工作区的 `commit-map` 将源提交哈希映射为本仓提交哈希；全零值表示该源提交没有本仓路径的内容。

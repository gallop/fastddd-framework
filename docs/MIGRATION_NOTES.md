# FastDDD 项目改造记录

> 本文档记录从旧仓库迁移到三层架构新项目的完整过程，供后续开发者了解项目历史。

## 改造背景

旧仓库 `fast-ddd-demo-app` 实际是完整的代码生成平台后端，包含 15+ Controller、20+ Service 和 AI 设计工作室，但命名为"demo"不专业。前端 `frontend-platform-vue` 命名也不统一。为配合专利申请和项目长远发展，于 2026-04-20 进行了架构重组。

## 旧项目 → 新项目变更对照

| 维度 | 旧项目 | 新项目 |
|------|--------|--------|
| 项目目录 | `D:\usr\workspace_2026\agent teams\fastddd` | `D:\usr\workspace_2026\fastddd` |
| 目录结构 | 22 个模块扁平排列 | 三层目录分组（framework/tooling/studio） |
| 应用模块 | `fast-ddd-demo-app` | `fastddd-studio/fastddd-studio-server` |
| 前端模块 | `frontend-platform-vue` | `fastddd-studio/fastddd-studio-ui` |
| Java 包名 | `com.gallop.fastddd.demo` | `com.gallop.fastddd.studio` |
| 主类名 | `FastDddDemoApplication` | `FastDddStudioApplication` |
| Context-path | `/fast-ddd-demo` | `/fastddd-studio` |
| 版本号 | v0.0.1 | v0.2.0 |

## 三层架构设计

```
fastddd/                           ← 根 POM（聚合 + 依赖管理）
├── fastddd-framework/             ← 框架层（14 模块）
│   ├── fast-ddd-common/           ← 基础工具
│   ├── fast-ddd-security-starter/ ← 安全 SPI
│   └── ...                        ← 其他框架模块
├── fastddd-tooling/               ← 工具层（6 模块）
│   ├── fastddd-code-generator/    ← 代码生成引擎
│   └── ...                        ← 其他工具模块
├── fastddd-studio/                ← 设计工作室（2 模块）
│   ├── fastddd-studio-server/     ← 后端
│   └── fastddd-studio-ui/         ← 前端
└── fastddd-examples/              ← 示例应用
```

### Maven POM 层级

- 根 POM `<modules>` 只引用 4 个聚合目录
- 聚合 POM（framework/tooling/studio）仅做 `<modules>` 聚合，`<packaging>pom</packaging>`
- 子模块的 `<parent>` 直接指向根 POM，`<relativePath>../../pom.xml</relativePath>`
- 聚合 POM 不作为 parent，避免三层继承导致的属性覆盖问题

### 跨层依赖（已知，Maven 可正常解析）

- `fastddd-maven-plugin`（工具层）→ `fast-ddd-common`、`fast-ddd-web`（框架层）
- `fast-ddd-analyzer-spring-boot-starter`（框架层）→ `fastddd-code-generator`（工具层）

## 改动统计

| 类别 | 文件数 |
|------|--------|
| Java 包名迁移（demo → studio） | ~138 |
| POM relativePath 更新 | 22 |
| POM 新建/修改 | 8 |
| 配置文件更新 | 4 |
| 前端文件更新 | 4 |
| Infra 文件更新 | 6 |
| 文档整理（迁移+分类） | 43 |
| **总计** | **~225** |

## 文档整理

### 保留并分类（23 个技术文档）

| 分类 | 文档数 | 内容 |
|------|--------|------|
| `docs/architecture/` | 5 | 架构设计、DDD 最佳实践、充血模型示例 |
| `docs/guides/` | 7 | 快速开始、CQRS 指南、编码规范、PRD |
| `docs/security/` | 2 | 安全模块改造计划和总结 |
| `docs/ai/` | 3 | AI 分析计划、任务清单、建表任务 |
| `docs/devops/` | 4 | Docker 部署、SSE 最佳实践、Jackson 配置 |
| `docs/api/` | 2 | API 参考、一体化生成器评审 |

### 丢弃的文档（~18 个）

- `conversation_history.md`（368KB 对话日志）
- `FastDDD_框架使用指引文档.md`（被使用指南取代，groupId 错误）
- `工具类.md`（包名完全错误）
- `docs/ci-cd-*.md`（6 个，超一年未更新）
- `docs/jacksonConfig方案.md`、`docs/sse问题解决.md` 等（调试笔记，已被正式文档包含）

### 公众号文章

- v1 系列（8 篇）→ `articles/v1/`
- v2 系列（10 篇）→ `articles/v2/`

## 安全模块改造历史（v0.0.x 阶段）

安全模块经历了四阶段改造，核心变更：

1. **SPI 架构**：`SecurityIdentityService`、`SecurityPermissionService`、`SecurityAuditService` 解耦
2. **JWT 过滤器修复**：`shouldNotFilter()` 使用 `getServletPath()` 替代 `getRequestURI()`
3. **permitAllPaths 精确化**：从通配符改为确切路径（如 `/api/auth/login` 而非 `/api/auth/**`）
4. **SecurityUserInfo 扩展**：添加 `userType`、`status` 字段适配前端

## 远程仓库

- Git 远程：`http://192.168.0.160/gallop-group/fastDDD.git`
- 旧仓库保留 v0.0.1 tag 作为历史归档
- 新项目从 v0.2.0 开始

## 已知技术债

| 项目 | 说明 |
|------|------|
| `fastddd-templates/pom.xml` 版本硬编码 | 所有依赖使用硬编码版本号，未使用根 POM 的 `<dependencyManagement>` |
| 根 POM dependencyManagement 不完整 | security-starter、ai-starter、templates 等未纳入管理 |
| 测试文件 context-path 硬编码 | `ProjectInitializrControllerTest.java` 中硬编码了旧路径 |

## 验证结果

- `mvn clean compile -DskipTests`：26 模块全部编译通过（41 秒）
- 后端启动：context-path `/fastddd-studio` 生效
- 前端启动：Vite proxy 正确路由
- 登录/项目管理/AI 设计工作室：功能正常

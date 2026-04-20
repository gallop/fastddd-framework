# FastDoc - 数据库文档生成工具

FastDoc 是 FastDDD 框架中的数据库文档生成工具，能够自动从数据库中提取元数据并生成美观的 Markdown 和 HTML 格式文档。

## 功能特性

- 🚀 **自动化文档生成**：从数据库自动提取表结构、字段信息、索引、外键等元数据
- 📊 **多格式输出**：支持 Markdown 和 HTML 两种格式的文档输出
- 🎨 **美观的样式**：内置精美的 HTML 样式，生成专业级文档
- 🔧 **高度可配置**：支持自定义输出目录、文档标题、作者信息等
- 📱 **响应式设计**：生成的 HTML 文档支持移动端查看
- 🗂️ **批量导出**：支持导出整个数据库或单个表的文档
- 🔍 **智能过滤**：自动识别并跳过系统表，支持表名过滤

## 支持的数据库

- MySQL 5.7+
- PostgreSQL 9.6+
- Oracle 11g+
- SQL Server 2012+
- H2 Database
- SQLite

## 快速开始

### 1. REST API 方式

FastDoc 提供了完整的 REST API 接口，可以通过 HTTP 请求生成和下载文档。

#### 获取数据库元数据
```http
GET /api/fastdoc/metadata
```

#### 生成数据库 Markdown 文档
```http
GET /api/fastdoc/database/markdown?title=我的数据库文档&author=gallop
```

#### 生成数据库 HTML 文档
```http
GET /api/fastdoc/database/html?title=我的数据库文档&author=gallop
```

#### 生成指定表的文档
```http
GET /api/fastdoc/table/{tableName}/markdown
GET /api/fastdoc/table/{tableName}/html
```

#### 导出文档到文件系统
```http
POST /api/fastdoc/export/database
Content-Type: application/json

{
    "outputDir": "/path/to/output",
    "format": "html",
    "title": "数据库文档",
    "author": "gallop"
}
```

### 2. Maven 插件方式

在项目的 `pom.xml` 中添加 FastDDD Maven 插件：

```xml
<plugin>
    <groupId>com.gallop</groupId>
    <artifactId>fastddd-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <!-- 数据库连接配置 -->
        <jdbcUrl>jdbc:mysql://localhost:3306/your_database</jdbcUrl>
        <username>your_username</username>
        <password>your_password</password>
        <driverClassName>com.mysql.cj.jdbc.Driver</driverClassName>
        
        <!-- 文档生成配置 -->
        <outputDir>target/fastdoc</outputDir>
        <outputFormat>html</outputFormat>
        <title>我的数据库文档</title>
        <description>这是一个自动生成的数据库文档</description>
        <author>gallop</author>
        <version>1.0.0</version>
        
        <!-- 可选配置 -->
        <includeSystemTables>false</includeSystemTables>
        <tableNamePattern>user_*,order_*</tableNamePattern>
        <generateSeparateTableDocs>true</generateSeparateTableDocs>
    </configuration>
</plugin>
```

#### 生成文档
```bash
# 生成数据库文档
mvn fastddd:generate-doc

# 生成代码和文档
mvn fastddd:generate-code fastddd:generate-doc
```

### 3. 编程方式

```java
@Autowired
private FastDocService fastDocService;

@Autowired
private FastDocTemplateService templateService;

@Autowired
private FastDocExportService exportService;

// 获取数据库元数据
DatabaseDocMetadata metadata = fastDocService.extractDatabaseMetadata();

// 生成 Markdown 文档
String markdownDoc = templateService.generateDatabaseMarkdown(metadata, "数据库文档", "gallop");

// 生成 HTML 文档
String htmlDoc = templateService.generateDatabaseHtml(metadata, "数据库文档", "gallop");

// 导出到文件
FastDocExportService.ExportResult result = exportService.exportDatabaseDoc(
    "/path/to/output", "html", "数据库文档", "gallop"
);
```

## 配置说明

### Maven 插件配置参数

| 参数名 | 类型 | 必需 | 默认值 | 说明 |
|--------|------|------|--------|------|
| jdbcUrl | String | 是 | - | 数据库连接URL |
| username | String | 是 | - | 数据库用户名 |
| password | String | 是 | - | 数据库密码 |
| driverClassName | String | 是 | - | 数据库驱动类名 |
| outputDir | String | 否 | target/fastdoc | 输出目录 |
| outputFormat | String | 否 | html | 输出格式（html/markdown） |
| title | String | 否 | 数据库文档 | 文档标题 |
| description | String | 否 | - | 文档描述 |
| author | String | 否 | FastDoc | 文档作者 |
| version | String | 否 | 1.0.0 | 文档版本 |
| includeSystemTables | Boolean | 否 | false | 是否包含系统表 |
| tableNamePattern | String | 否 | - | 表名过滤模式（逗号分隔，支持通配符） |
| generateSeparateTableDocs | Boolean | 否 | false | 是否生成单独的表文档 |

### REST API 参数

#### 查询参数
- `title`: 文档标题
- `author`: 文档作者
- `includeSystemTables`: 是否包含系统表（true/false）
- `tablePattern`: 表名过滤模式

#### 请求体参数（导出接口）
```json
{
    "outputDir": "输出目录路径",
    "format": "输出格式（html/markdown）",
    "title": "文档标题",
    "author": "文档作者",
    "description": "文档描述",
    "version": "文档版本",
    "includeSystemTables": false,
    "tableNamePattern": "表名过滤模式",
    "generateSeparateTableDocs": true
}
```

## 生成的文档结构

### HTML 文档特性
- 📱 响应式设计，支持移动端查看
- 🎨 现代化的 UI 设计
- 🔍 表格搜索和排序功能
- 📊 数据库统计信息
- 🗂️ 表格目录导航
- 💡 字段类型高亮显示

### Markdown 文档特性
- 📝 标准 Markdown 格式
- 📊 表格形式展示字段信息
- 🔗 内部链接导航
- 📋 完整的索引和外键信息

### 文档内容包含
1. **数据库基本信息**
   - 数据库名称和类型
   - 版本信息
   - 连接信息
   - 生成时间

2. **统计信息**
   - 表总数
   - 字段总数
   - 索引总数
   - 外键总数

3. **表详细信息**
   - 表名和注释
   - 字段列表（名称、类型、长度、是否为空、默认值、注释）
   - 主键信息
   - 索引信息
   - 外键关系

## 使用示例

### 示例1：生成完整数据库文档

```bash
mvn fastddd:generate-doc \
  -DjdbcUrl=jdbc:mysql://localhost:3306/ecommerce \
  -Dusername=root \
  -Dpassword=123456 \
  -DoutputFormat=html \
  -Dtitle="电商系统数据库文档" \
  -Dauthor="开发团队"
```

### 示例2：只生成用户相关表的文档

```xml
<configuration>
    <jdbcUrl>jdbc:mysql://localhost:3306/ecommerce</jdbcUrl>
    <username>root</username>
    <password>123456</password>
    <tableNamePattern>user_*,customer_*</tableNamePattern>
    <title>用户模块数据库文档</title>
</configuration>
```

### 示例3：生成 Markdown 格式文档

```bash
curl -X GET "http://localhost:8080/api/fastdoc/database/markdown?title=API文档&author=gallop" \
  -H "Accept: application/octet-stream" \
  --output database-doc.md
```

### 示例4：批量导出所有表的文档

```bash
curl -X POST "http://localhost:8080/api/fastdoc/export/tables" \
  -H "Content-Type: application/json" \
  -d '{
    "outputDir": "/tmp/docs",
    "format": "html",
    "generateSeparateTableDocs": true
  }'
```

## 最佳实践

1. **版本控制**：将生成的文档加入版本控制，跟踪数据库结构变化
2. **自动化集成**：在 CI/CD 流程中集成文档生成，确保文档始终是最新的
3. **定期更新**：建议在数据库结构变更后及时更新文档
4. **团队共享**：将生成的 HTML 文档部署到内部文档站点，方便团队查阅

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查数据库连接参数是否正确
   - 确认数据库服务是否正常运行
   - 验证用户权限是否足够

2. **生成的文档为空**
   - 检查是否有权限访问数据库元数据
   - 确认表名过滤模式是否正确
   - 验证数据库中是否存在表

3. **中文乱码问题**
   - 确保数据库连接URL包含正确的字符编码参数
   - 例如：`jdbc:mysql://localhost:3306/db?useUnicode=true&characterEncoding=utf8`

### 日志调试

启用调试日志：
```properties
logging.level.com.gallop.fastddd.web.fastdoc=DEBUG
```

## 许可证

FastDoc 遵循 Apache 2.0 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进 FastDoc！

## 联系方式

- 作者：gallop
- 邮箱：gallop@example.com
- 项目地址：https://github.com/gallop/fastddd
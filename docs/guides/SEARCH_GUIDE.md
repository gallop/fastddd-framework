# FastDDD Search 模块使用指南

> 轻量级全文搜索，SQL、Meilisearch 和 disabled 三种显式模式，5 分钟接入。

## 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-search</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**可选依赖**（运行时）：
- SQL 后端：默认模式，必须包含 MyBatis Plus、至少一个 `BaseMapper` Bean；缺失时启动失败并提示修复方式
- Meilisearch 后端：需额外引入 `fast-ddd-search`（已内置 meilisearch-java SDK，optional scope）+ 运行 Meilisearch 服务

## 2. SQL 后端启动

SQL 是默认值；使用它的应用必须已引入 `fast-ddd-crud` 并注册至少一个 MyBatis-Plus Mapper：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `fastddd.search.enabled` | `true` | 模块总开关 |
| `fastddd.search.backend` | `sql` | 搜索后端 |
| `fastddd.search.sql.full-text-enabled` | `auto` | 全文检索策略 |

## 3. 使用方式

### 3.1 注入 SearchService

```java
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final SearchService searchService;  // 框架自动注入，无需泛型

    public SearchResult<Product> search(String keyword, int page) {
        SearchQuery query = SearchQuery.builder()
                .keyword(keyword)
                .field("name")                    // 搜索字段（Java 属性名，自动转下划线）
                .field("description")
                .filter("status", SearchQuery.Filter.eq(1))   // 精确过滤
                .filter("categoryId", SearchQuery.Filter.in(List.of(1, 2, 3)))
                .sort(SearchQuery.Sort.desc("createTime"))     // 排序
                .pageNum(page)
                .pageSize(10)
                .build();

        return searchService.search(query, Product.class);
    }
}
```

### 3.2 多字段关键词搜索

```java
SearchQuery query = SearchQuery.builder()
        .keyword("手机")
        .field("name")
        .field("description")
        .field("brand")
        .build();
// SQL: WHERE name LIKE '%手机%' OR description LIKE '%手机%' OR brand LIKE '%手机%'
// Meilisearch: 全字段全文检索（自动分词）
```

### 3.3 过滤条件

```java
// 精确匹配
SearchQuery.Filter.eq(1)

// 不等于
SearchQuery.Filter.ne(0)

// 范围
SearchQuery.Filter.gte(100)       // >= 100
SearchQuery.Filter.lt(1000)       // < 1000

// IN / NOT IN
SearchQuery.Filter.in(List.of(1, 2, 3))
SearchQuery.Filter.notIn(List.of(0, -1))

// 空值判断
SearchQuery.Filter.isNull()
SearchQuery.Filter.notNull()
```

### 3.4 排序

```java
SearchQuery query = SearchQuery.builder()
        .keyword("手机")
        .field("name")
        .sort(SearchQuery.Sort.desc("createTime"))
        .sort(SearchQuery.Sort.asc("price"))
        .build();
```

### 3.5 结果处理

```java
SearchResult<Product> result = searchService.search(query, Product.class);

// 分页信息
result.getTotal();       // 总记录数
result.getPageNum();     // 当前页码
result.getPageSize();    // 每页大小
result.getPages();       // 总页数
result.isEmpty();        // 是否为空

// 转为通用 PageResult（兼容现有接口）
PageResult<Product> pageResult = result.toPageResult();

// 后端标识
result.getBackendType();  // "sql" 或 "meilisearch"
```

### 3.6 Controller 中使用

搜索模块不提供 Controller，由业务层自行暴露端点（避免与 `BaseController.search()` 冲突）：

```java
@RestController
@RequestMapping("/api/products")
public class ProductController extends BaseController<ProductService, Product> {

    private final SearchService searchService;

    public ProductController(ProductService service, SearchService searchService) {
        super(service);
        this.searchService = searchService;
    }

    @PostMapping("/search/fulltext")
    public ApiResponse<SearchResult<Product>> fulltextSearch(@RequestBody SearchQuery query) {
        return ApiResponse.success(searchService.search(query, Product.class));
    }
}
```

## 4. SQL 后端详解

### 4.1 LIKE 模式（默认）

无 FULLTEXT 索引时自动使用 LIKE 子串匹配：

```sql
-- keyword="手机", fields=["name","description"]
WHERE (name LIKE '%手机%' OR description LIKE '%手机%')
  AND status = 1
ORDER BY create_time DESC
LIMIT 10 OFFSET 0
```

### 4.2 FULLTEXT 全文检索

MySQL 环境下自动检测方言，使用 `MATCH...AGAINST`（需要 FULLTEXT 索引）：

```sql
-- keyword="手机", fields=["name","description"]
WHERE MATCH(name, description) AGAINST('手机' IN BOOLEAN MODE)
  AND status = 1
ORDER BY create_time DESC
LIMIT 10 OFFSET 0
```

**创建 FULLTEXT 索引**：

```sql
ALTER TABLE product ADD FULLTEXT INDEX ft_name_desc (name, description);
```

**全文检索策略配置**：

```yaml
fastddd:
  search:
    sql:
      full-text-enabled: auto    # auto(检测方言) | always(强制) | never(禁用)
```

| 值 | 行为 |
|---|------|
| `auto` | MySQL 方言自动启用 FULLTEXT，其他数据库用 LIKE |
| `always` | 强制 FULLTEXT，非 MySQL 数据库会降级到 LIKE 并打印警告 |
| `never` | 始终使用 LIKE |

## 5. Meilisearch 后端

### 5.1 安装 Meilisearch

```bash
# Docker 方式
docker run -d --name meilisearch \
  -p 7700:7700 \
  -e MEILI_MASTER_KEY=your-master-key \
  getmeili/meilisearch:latest

# 或下载二进制（~30MB）
curl -L https://install.meilisearch.com | sh
./meilisearch --master-key your-master-key
```

### 5.2 切换配置

```yaml
fastddd:
  search:
    enabled: true
    backend: meilisearch
    meilisearch:
      host: "http://localhost:7700"
      api-key: "your-master-key"
      timeout: 5s
      index-prefix: "fastddd_"
```

**代码无需任何修改**，`SearchService` 自动切换到 Meilisearch 后端。

### 5.3 索引管理

```java
// 创建索引（配置 searchable/filterable/sortable 字段）
searchService.createIndex("products", Product.class);

// 索引单条文档
searchService.index("products", product);

// 批量索引
searchService.indexBatch("products", productList);

// 删除文档
searchService.removeFromIndex("products", String.valueOf(productId));

// 删除索引
searchService.dropIndex("products");
```

SQL 后端下 `index()`/`removeFromIndex()` 等方法为 no-op，无需条件判断。

### 5.4 按索引定制配置

```yaml
fastddd:
  search:
    backend: meilisearch
    meilisearch:
      host: "http://localhost:7700"
      api-key: "your-master-key"
    indexes:
      products:
        searchable-fields: [name, description, brand]
        filterable-fields: [status, categoryId, price]
        sortable-fields: [price, createTime]
      orders:
        searchable-fields: [orderNo, customerName]
        filterable-fields: [status, amount]
        sortable-fields: [createTime, amount]
```

### 5.5 高亮

```java
SearchQuery query = SearchQuery.builder()
        .keyword("手机")
        .field("name")
        .field("description")
        .highlight(true)   // 启用高亮（仅 Meilisearch 有效）
        .build();

SearchResult<Product> result = searchService.search(query, Product.class);
Map<String, List<String>> highlights = result.getHighlights();
// {"name": ["华为<em>手机</em>P60"], "description": ["旗舰<em>手机</em>，搭载..."]}
```

## 6. 运行场景

| 场景 | 配置 | 行为 |
|------|------|------|
| **SQL LIKE** | `backend=sql`（默认）+ MyBatis-Plus `BaseMapper` | QueryWrapper 查询；依赖或 Mapper 缺失时启动失败 |
| **SQL FULLTEXT** | `backend=sql` + MySQL | 自动 MATCH...AGAINST |
| **Meilisearch** | `backend=meilisearch` + SDK 在 classpath | Meilisearch 全文搜索 + 高亮 |
| **禁用** | `backend=disabled` 或 `enabled=false` | 不加载 `SearchService`；用于未配置搜索后端的消费者 |

## 7. 搜索架构

```
                         ┌─ SearchService (SPI 接口)
                         │
           ┌─────────────┼─────────────┐
           │                           │
   SqlSearchService          MeilisearchSearchService
           │                           │
   ┌───────┴───────┐           ┌──────┴──────┐
   │               │           │             │
QueryWrapper   DatabaseDialect  Meilisearch  Jackson
(如 LIKE)      (如 FULLTEXT)    SDK 0.16.1   桥接
```

**SQL 读取流程**：`SearchQuery` → `QueryWrapperBuilder` → `BaseMapper.selectPage()` → `SearchResult`

**Meilisearch 读取流程**：`SearchQuery` → `SearchRequest` → SDK → Jackson 反序列化 → `SearchResult`

## 8. 异常处理

| 异常 | 场景 | 处理建议 |
|------|------|----------|
| 无异常，返回空结果 | SQL 查询无匹配 | 正常业务场景 |
| `SearchBackendException` | Meilisearch 通信失败 | 捕获后降级或提示 |
| `SearchBackendException` | 索引操作失败 | 检查 Meilisearch 服务状态 |

```java
try {
    SearchResult<Product> result = searchService.search(query, Product.class);
} catch (SearchBackendException e) {
    // 基础设施故障，降级处理
    log.error("Search backend failed: {}", e.getMessage());
    result = SearchResult.empty(1, 10);  // 返回空结果
}
```

## 9. 切换后端示例

**开发环境**（SQL，零配置）：

```yaml
# application-dev.yml — 无需任何搜索配置，默认 SQL
```

**生产环境**（Meilisearch）：

```yaml
# application-prod.yml
fastddd:
  search:
    backend: meilisearch
    meilisearch:
      host: "http://meilisearch:7700"
      api-key: "${MEILISEARCH_API_KEY}"
```

**业务代码完全不变**，仅改配置即可切换后端。

# FastDDD Search Module Usage Guide

**English** | [简体中文](SEARCH_GUIDE.zh-CN.md)

> Lightweight full-text search with three explicit modes — SQL, Meilisearch, and disabled — up and running in 5 minutes.

## 1. Add the Dependency

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-search</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Optional dependencies** (runtime):
- SQL backend: the default mode; requires MyBatis Plus and at least one `BaseMapper` bean — if either is missing, startup fails with a message explaining how to fix it
- Meilisearch backend: additionally requires `fast-ddd-search` (the meilisearch-java SDK is already bundled with optional scope) plus a running Meilisearch service

## 2. SQL Backend Startup

SQL is the default; applications using it must already include `fast-ddd-crud` and register at least one MyBatis-Plus Mapper:

| Property | Default | Description |
|--------|--------|------|
| `fastddd.search.enabled` | `true` | Master switch for the module |
| `fastddd.search.backend` | `sql` | Search backend |
| `fastddd.search.sql.full-text-enabled` | `auto` | Full-text search strategy |

## 3. Usage

### 3.1 Inject SearchService

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

### 3.2 Multi-Field Keyword Search

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

### 3.3 Filter Conditions

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

### 3.4 Sorting

```java
SearchQuery query = SearchQuery.builder()
        .keyword("手机")
        .field("name")
        .sort(SearchQuery.Sort.desc("createTime"))
        .sort(SearchQuery.Sort.asc("price"))
        .build();
```

### 3.5 Result Handling

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

### 3.6 Using It in a Controller

The search module does not provide a Controller; expose endpoints yourself in the business layer (to avoid conflicting with `BaseController.search()`):

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

## 4. SQL Backend In Depth

### 4.1 LIKE Mode (Default)

Automatically falls back to LIKE substring matching when no FULLTEXT index exists:

```sql
-- keyword="手机", fields=["name","description"]
WHERE (name LIKE '%手机%' OR description LIKE '%手机%')
  AND status = 1
ORDER BY create_time DESC
LIMIT 10 OFFSET 0
```

### 4.2 FULLTEXT Full-Text Search

On MySQL the dialect is auto-detected and `MATCH...AGAINST` is used (requires a FULLTEXT index):

```sql
-- keyword="手机", fields=["name","description"]
WHERE MATCH(name, description) AGAINST('手机' IN BOOLEAN MODE)
  AND status = 1
ORDER BY create_time DESC
LIMIT 10 OFFSET 0
```

**Creating a FULLTEXT index**:

```sql
ALTER TABLE product ADD FULLTEXT INDEX ft_name_desc (name, description);
```

**Full-text search strategy configuration**:

```yaml
fastddd:
  search:
    sql:
      full-text-enabled: auto    # auto(检测方言) | always(强制) | never(禁用)
```

| Value | Behavior |
|---|------|
| `auto` | FULLTEXT is enabled automatically for the MySQL dialect; other databases use LIKE |
| `always` | Forces FULLTEXT; non-MySQL databases degrade to LIKE and print a warning |
| `never` | Always uses LIKE |

## 5. Meilisearch Backend

### 5.1 Install Meilisearch

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

### 5.2 Switching Configuration

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

**No code changes are needed** — `SearchService` automatically switches to the Meilisearch backend.

### 5.3 Index Management

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

Under the SQL backend, methods such as `index()`/`removeFromIndex()` are no-ops, so no conditional checks are needed.

### 5.4 Per-Index Configuration

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

### 5.5 Highlighting

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

## 6. Runtime Scenarios

| Scenario | Configuration | Behavior |
|------|------|------|
| **SQL LIKE** | `backend=sql` (default) + MyBatis-Plus `BaseMapper` | QueryWrapper-based query; startup fails if the dependency or Mapper is missing |
| **SQL FULLTEXT** | `backend=sql` + MySQL | Automatic MATCH...AGAINST |
| **Meilisearch** | `backend=meilisearch` + SDK on the classpath | Meilisearch full-text search + highlighting |
| **Disabled** | `backend=disabled` or `enabled=false` | `SearchService` is not loaded; for consumers that have not configured a search backend |

## 7. Search Architecture

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

**SQL read flow**: `SearchQuery` → `QueryWrapperBuilder` → `BaseMapper.selectPage()` → `SearchResult`

**Meilisearch read flow**: `SearchQuery` → `SearchRequest` → SDK → Jackson deserialization → `SearchResult`

## 8. Exception Handling

| Exception | Scenario | Suggested Handling |
|------|------|----------|
| No exception, empty result returned | SQL query has no matches | Normal business scenario |
| `SearchBackendException` | Meilisearch communication failure | Catch and degrade, or surface an error message |
| `SearchBackendException` | Index operation failure | Check the Meilisearch service status |

```java
try {
    SearchResult<Product> result = searchService.search(query, Product.class);
} catch (SearchBackendException e) {
    // 基础设施故障，降级处理
    log.error("Search backend failed: {}", e.getMessage());
    result = SearchResult.empty(1, 10);  // 返回空结果
}
```

## 9. Backend Switching Examples

**Development environment** (SQL, zero configuration):

```yaml
# application-dev.yml — 无需任何搜索配置，默认 SQL
```

**Production environment** (Meilisearch):

```yaml
# application-prod.yml
fastddd:
  search:
    backend: meilisearch
    meilisearch:
      host: "http://meilisearch:7700"
      api-key: "${MEILISEARCH_API_KEY}"
```

**Business code stays completely unchanged** — switch backends by changing configuration only.

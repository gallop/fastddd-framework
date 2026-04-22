/**
 * FastDDD Search 模块 — 轻量级全文搜索引擎抽象。
 *
 * <p>提供 {@link com.gallop.fastddd.search.SearchService} SPI 接口，
 * 支持两种后端运行时切换（仅改配置，代码零修改）：</p>
 *
 * <ul>
 *   <li><b>SQL（默认）</b> — 基于 MyBatis Plus QueryWrapper，
 *       支持 LIKE 子串匹配和 MySQL FULLTEXT 全文检索，
 *       零额外依赖，适合开发/测试环境和轻量场景。</li>
 *   <li><b>Meilisearch</b> — 基于 Meilisearch 引擎（Rust 单进程，~290MB），
 *       内置 Jieba 中文分词，支持高亮、聚合、排序等高级搜索功能，
 *       适合需要高性能全文搜索的生产环境。</li>
 * </ul>
 *
 * <p>核心组件：</p>
 * <ul>
 *   <li>{@link com.gallop.fastddd.search.SearchService} — 搜索引擎 SPI 接口</li>
 *   <li>{@link com.gallop.fastddd.search.SearchQuery} — 查询构建器</li>
 *   <li>{@link com.gallop.fastddd.search.SearchResult} — 搜索结果</li>
 *   <li>{@link com.gallop.fastddd.search.SearchIndexRegistry} — 索引注册表</li>
 *   <li>{@link com.gallop.fastddd.search.sql.SqlSearchService} — SQL 后端实现</li>
 *   <li>{@link com.gallop.fastddd.search.meilisearch.MeilisearchSearchService} — Meilisearch 后端实现</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-SNAPSHOT
 */
package com.gallop.fastddd.search;
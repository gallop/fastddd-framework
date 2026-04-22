package com.gallop.fastddd.search;

/**
 * 搜索后端基础设施异常。
 * <p>
 * 当搜索引擎（Meilisearch 等）通信失败或返回错误时抛出，
 * 调用方可以据此区分"无结果"和"基础设施故障"。
 *
 * @author gallop
 */
public class SearchBackendException extends RuntimeException {

    public SearchBackendException(String message) {
        super(message);
    }

    public SearchBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.gallop.fastddd.identity.core.tck;

import java.util.List;

/**
 * TCK 上游响应词表（W7-d）：与具体 IdP 线协议无关的语义化上游形态。
 *
 * <p>宿主 harness 负责把每个形态渲染为其真实线协议形态（HTTP 状态码 / 信封字段 / 响应时延等）；
 * 契约基类 {@link DirectorySourceContractTck} 的断言域 = 端口级语义（异常类型 + 上游击数 +
 * 页形态不变量 + 规则 B），不断言 reasonCode 字符串或具体线协议字段。
 *
 * <p>语义分域（与 DirectorySource 端口 javadoc 契约一一对应）：
 * <ul>
 *   <li>权限族（{@link PermissionDenied} / {@link AuthEstablishmentDenied}）→ fail-fast，禁止退避重试；</li>
 *   <li>瞬态族（{@link RateLimited} / {@link UpstreamOverloaded} / {@link ReadTimeout}）→ 源侧有界重试，
 *       耗尽后 Unavailable；</li>
 *   <li>{@link DeterminateFailure}（未核对业务码）→ 确定失败不重试，Unavailable；</li>
 *   <li>{@link MalformedSuccess}（严格成功三要素反面形态）→ malformed-response，Unavailable 不重试——
 *       注意 5xx/429 携成功体属过载优先域（M1-TAG-02），不在 malformed 域；</li>
 *   <li>{@link DepartmentGone}（仅 persons 流）→ 合法空终页，规则 B：缺失不判删除。</li>
 * </ul>
 */
public sealed interface UpstreamResponse {

    /** 上游部门对象（语义形态；宿主渲染为线协议字段，如 open_department_id）。 */
    record UpstreamDepartment(String id, String parentId, String name) {
    }

    /** 上游人员对象（语义形态；宿主渲染为线协议字段，如 open_id / department_ids[]）。 */
    record UpstreamPerson(String openId, String name, String enName, List<String> departmentIds) {
    }

    /** 部门页（departments 流）：items + 下一页游标 + 是否还有更多。 */
    record DepartmentsPage(List<UpstreamDepartment> items, String nextToken, boolean hasMore)
            implements UpstreamResponse {
    }

    /** 人员页（persons 流）：items + 下一页游标 + 是否还有更多。 */
    record PersonsPage(List<UpstreamPerson> items, String nextToken, boolean hasMore)
            implements UpstreamResponse {
    }

    /** 数据端点权限不足——fail-fast 语义（禁止当离职/删除解释、禁止退避风暴）。 */
    record PermissionDenied(String detail) implements UpstreamResponse {
    }

    /** 凭据建立失败（如 tenant token 被拒）——fail-fast 语义，数据端点零外呼。 */
    record AuthEstablishmentDenied(String detail) implements UpstreamResponse {
    }

    /** 限流（429 / 99991400 族）——瞬态，有界重试。 */
    record RateLimited(String detail) implements UpstreamResponse {
    }

    /** 上游过载（5xx 族）——瞬态，有界重试；过载判定优先于一切业务码解释（M1-TAG-02）。 */
    record UpstreamOverloaded(String detail) implements UpstreamResponse {
    }

    /** 读超时——瞬态，有界重试。 */
    record ReadTimeout(String detail) implements UpstreamResponse {
    }

    /** 确定失败（未核对业务码兜底域）——不重试，Unavailable。 */
    record DeterminateFailure(String detail) implements UpstreamResponse {
    }

    /** 部门已不存在（上游并发删除，40008 语义）——仅 persons 流；规则 B：空终页不判删除。 */
    record DepartmentGone(String detail) implements UpstreamResponse {
    }

    /** 畸形成功信封（W22-C1 严格成功三要素 / 分页严格校验反面形态）——malformed-response，不重试。 */
    record MalformedSuccess(MalformedShape shape) implements UpstreamResponse {
    }

    /** 畸形形态枚举（8 值；5xx/429 × 成功体属过载优先重试域，不在本域）。 */
    enum MalformedShape {
        /** 响应体非 JSON。 */
        NON_JSON_BODY,
        /** 信封缺成功标志（业务码字段缺失）。 */
        SUCCESS_FLAG_MISSING,
        /** 成功标志类型错误（业务码非整型）。 */
        SUCCESS_FLAG_WRONG_TYPE,
        /** 分页标志缺失（has_more 不在场）。 */
        PAGER_FLAG_MISSING,
        /** 分页标志类型错误。 */
        PAGER_FLAG_WRONG_TYPE,
        /** data.items 缺失。 */
        ITEMS_MISSING,
        /** data.items 非数组。 */
        ITEMS_WRONG_TYPE,
        /** items 元素缺必填键字段（如部门缺 id / 人员缺 open_id）。 */
        ITEM_MISSING_REQUIRED_FIELD
    }
}

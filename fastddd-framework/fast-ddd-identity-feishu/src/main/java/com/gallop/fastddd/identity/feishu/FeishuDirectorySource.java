package com.gallop.fastddd.identity.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.port.DirectorySource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 飞书目录源适配器（W3-b，W3 设计 §2；W0-4 §1.3/1.4 2026-09-28 编码前复核）：
 * contact/v3 部门子级（{@code fetch_child=false} 逐层 BFS）+ 按部门查人员；
 * tenant_access_token 由 {@link FeishuDirectoryClient} 缓存管理。
 *
 * <p><b>部门遍历（显式 BFS）</b>：不用 {@code fetch_child=true} 单流——官方对
 * 大部门禁用递归（400/43010），逐层子级遍历无该形态；从根 {@code "0"} 出发，
 * 每层分页（page_size ≤ 50）后把发现的子部门入队。**首页为合成根部门**
 * （{@code departmentId="0"}、parent=null、name=null——不虚构）：children 接口
 * 只返回根的子部门不含根本身，而人员流按部门逐个遍历，缺根条目会漏掉根直属
 * 人员（find_by_department 对 {@code department_id=0} 恰好返回根直属人员）。
 * BFS 全部状态编码进游标（对编排不透明；源实例无会话状态，可并发复用）。</p>
 *
 * <p><b>多部门任职</b>：{@link DirectoryPerson#departmentIds()} 直接透传
 * find_by_department 的 {@code department_ids[]}（open_department_id 族）——
 * 任职关系随人员对象携带，无独立关系流。同一人员会随其每个部门逐页重复出现，
 * 去重属 W3-c 存储责任（W3-a 执行记录 §4 已登记）。</p>
 *
 * <p><b>错误/边界语义（不得解释为删除/离职）</b>：权限族（230002/40004/40014/
 * 41050/42008）与 tenant token 配置错误 → {@code DirectorySourceForbiddenException}
 * fail-fast（客户端零重试）；瞬时族（传输/429/5xx/99991400）在客户端按
 * {@code directory.retry.*} 有界重试后抛 {@code DirectorySourceUnavailableException}；
 * 部门被上游并发删除（40008）→ departments 侧跳过该父部门继续 BFS、persons 侧
 * 返回空终页（端口契约明文）；空页/空目录 = 合法终页。本类不记日志、不记录
 * 人员明细（PII 最小化；异常只含原因码/错误码/计数）。</p>
 *
 * <p><b>防无限遍历</b>：BFS 不做去重集（上游环/重复会让游标队列自循环）——
 * 无限分页由编排用例的 {@code maxPagesPerRun} 页数护栏兜底（整代废弃）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class FeishuDirectorySource implements DirectorySource {

    /** 飞书根部门 id（children 路径参数与 find_by_department 的 department_id 通用值）。 */
    static final String ROOT_DEPARTMENT_ID = "0";

    private static final ObjectMapper CURSOR_MAPPER = new ObjectMapper();

    private final FeishuLoginProperties loginProperties;
    private final FeishuDirectoryClient client;

    /**
     * @param loginProperties 凭据与范围键来源（{@code app-id}/{@code app-secret}/
     *                        {@code expected-tenant-key}；redirect-uri 属登录链路，目录不要求）
     * @param properties      目录端点/超时配置
     * @param maxRetries      幂等调用附加重试次数（{@code directory.retry.max-attempts}）
     * @param retryBackoff    固定退避（{@code directory.retry.backoff}）
     */
    public FeishuDirectorySource(FeishuLoginProperties loginProperties,
                                 FeishuDirectoryProperties properties,
                                 int maxRetries,
                                 java.time.Duration retryBackoff) {
        List<String> missing = missingDirectoryKeys(loginProperties);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("fastddd.identity.directory.enabled=true 且引入"
                    + " fast-ddd-identity-feishu 作目录源时，以下配置必填（当前缺失）：\n  - "
                    + String.join("\n  - ", missing)
                    + "\n（expected-tenant-key 是镜像同步范围 DirectoryScope 的租户键；"
                    + "宿主使用其他目录源时请自行提供 DirectorySource Bean，本适配器会退让）");
        }
        this.loginProperties = loginProperties;
        this.client = new FeishuDirectoryClient(loginProperties, properties, maxRetries, retryBackoff);
    }

    /** 目录模式必填键（区别于登录链路：不要求 redirect-uri）。 */
    static List<String> missingDirectoryKeys(FeishuLoginProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties.getAppId() == null || properties.getAppId().isBlank()) {
            missing.add("fastddd.identity.feishu.app-id");
        }
        if (properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            missing.add("fastddd.identity.feishu.app-secret");
        }
        if (properties.getExpectedTenantKey() == null || properties.getExpectedTenantKey().isBlank()) {
            missing.add("fastddd.identity.feishu.expected-tenant-key");
        }
        return missing;
    }

    /** M1 单范围：tenant_key = expected-tenant-key，app_scope = app-id（与登录链 ExternalIdentity 的 app 维度一致）。 */
    @Override
    public DirectoryScope scope() {
        return new DirectoryScope(loginProperties.getExpectedTenantKey(), loginProperties.getAppId());
    }

    @Override
    public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
        if (query.cursor() == null) {
            // 首页：合成根部门（不外呼；children 接口不含根本身，人员流需要根条目覆盖根直属人员）
            DirectoryDepartment root = new DirectoryDepartment(ROOT_DEPARTMENT_ID, null, null);
            String cursor = encodeCursor(new BfsState(ROOT_DEPARTMENT_ID, null, List.of()));
            return new DirectoryPage<>(List.of(root), cursor, true);
        }
        BfsState state = decodeCursor(query.cursor());
        while (true) {
            FeishuDirectoryClient.FeishuPage<FeishuDirectoryClient.DepartmentItem> page;
            try {
                page = client.children(state.parentId(), state.pageToken(), query.pageSize());
            } catch (FeishuDirectoryClient.DepartmentGoneSignal gone) {
                // 上游并发删除该父部门：跳过（非错误、非删除判定），继续队列
                state = state.advancePastParent();
                if (state == null) {
                    return DirectoryPage.last(List.of());
                }
                continue;
            }
            List<DirectoryDepartment> items = new ArrayList<>(page.items().size());
            List<String> discovered = new ArrayList<>(page.items().size());
            for (FeishuDirectoryClient.DepartmentItem item : page.items()) {
                items.add(new DirectoryDepartment(item.openDepartmentId(),
                        blankToNull(item.parentDepartmentId()), item.name()));
                discovered.add(item.openDepartmentId());
            }
            if (page.hasMore()) {
                BfsState next = new BfsState(state.parentId(), page.pageToken(),
                        append(state.pendingParents(), discovered));
                return new DirectoryPage<>(items, encodeCursor(next), true);
            }
            // 该父部门枚举完毕：队列推进（发现的子部门已入队）
            List<String> queue = append(state.pendingParents(), discovered);
            if (queue.isEmpty()) {
                return DirectoryPage.last(items);
            }
            return new DirectoryPage<>(items,
                    encodeCursor(new BfsState(queue.get(0), null, queue.subList(1, queue.size()))), true);
        }
    }

    @Override
    public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
        try {
            FeishuDirectoryClient.FeishuPage<FeishuDirectoryClient.PersonItem> page =
                    client.persons(departmentId, query.cursor(), query.pageSize());
            List<DirectoryPerson> items = new ArrayList<>(page.items().size());
            for (FeishuDirectoryClient.PersonItem item : page.items()) {
                items.add(new DirectoryPerson(item.openId(), item.name(), item.enName(),
                        item.departmentIds()));
            }
            if (page.hasMore()) {
                return new DirectoryPage<>(items, page.pageToken(), true);
            }
            return DirectoryPage.last(items);
        } catch (FeishuDirectoryClient.DepartmentGoneSignal gone) {
            // 端口契约：部门不存在（上游并发删除）→ 空终页，不抛异常、不解释为删除
            return DirectoryPage.last(List.of());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> append(List<String> base, List<String> extra) {
        List<String> merged = new ArrayList<>(base.size() + extra.size());
        merged.addAll(base);
        merged.addAll(extra);
        return List.copyOf(merged);
    }

    /** BFS 游标状态（对编排不透明，Base64url(JSON) 编码）。 */
    private record BfsState(String parentId, String pageToken, List<String> pendingParents) {

        /** 当前父部门已消失：跳到下一个待枚举父部门（无剩余 = null）。 */
        BfsState advancePastParent() {
            if (pendingParents.isEmpty()) {
                return null;
            }
            return new BfsState(pendingParents.get(0), null, pendingParents.subList(1, pendingParents.size()));
        }
    }

    private static String encodeCursor(BfsState state) {
        ObjectNode node = CURSOR_MAPPER.createObjectNode();
        node.put("p", state.parentId());
        if (state.pageToken() != null) {
            node.put("t", state.pageToken());
        }
        ArrayNode queue = node.putArray("q");
        state.pendingParents().forEach(queue::add);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static BfsState decodeCursor(String cursor) {
        try {
            JsonNode node = CURSOR_MAPPER.readTree(Base64.getUrlDecoder().decode(cursor));
            JsonNode parent = node.path("p");
            JsonNode token = node.path("t");
            JsonNode queue = node.path("q");
            if (!parent.isTextual() || parent.asText().isBlank() || !queue.isArray()) {
                throw new IllegalArgumentException("cursor shape");
            }
            List<String> pending = new ArrayList<>(queue.size());
            for (JsonNode entry : queue) {
                if (!entry.isTextual()) {
                    throw new IllegalArgumentException("cursor queue entry");
                }
                pending.add(entry.asText());
            }
            return new BfsState(parent.asText(), token.isTextual() ? token.asText() : null,
                    List.copyOf(pending));
        } catch (IllegalArgumentException | java.io.IOException e) {
            throw new DirectoryContractViolationException("invalid-cursor",
                    "目录部门流游标无法解码（长度=" + (cursor == null ? 0 : cursor.length())
                            + "）——非本源产出的游标，整代废弃");
        }
    }
}

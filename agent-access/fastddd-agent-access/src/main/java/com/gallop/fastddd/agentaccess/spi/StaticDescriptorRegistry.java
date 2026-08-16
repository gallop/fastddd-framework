package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.InternalReason;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link DescriptorRegistry} 静态受控实现：只接受构建期显式注册，
 * 构建完成后只读（TM-10：无任何扫描入口——类路径上不存在「注册即发现」路径）。
 *
 * <p>注册约束：同一 {@code (actionId, actionVersion)} 重复注册即拒绝
 * （禁覆盖式 put / shadow 注册）；{@code enabled=false} 的条目允许注册
 * （表达「已发布但停用」），但 resolve 永远拒绝。</p>
 *
 * <p>registry 不可变性：build 后无修改入口；线程安全由只读结构保证。</p>
 */
public final class StaticDescriptorRegistry implements DescriptorRegistry {

    private static final InternalReason NOT_REGISTERED =
            new InternalReason("descriptor.unregistered", ActionOutcomeCode.ACTION_NOT_EXPOSED);
    private static final InternalReason DISABLED =
            new InternalReason("descriptor.disabled", ActionOutcomeCode.ACTION_NOT_EXPOSED);
    private static final InternalReason VERSION_DRIFTED =
            new InternalReason("descriptor.version.drifted", ActionOutcomeCode.ACTION_NOT_EXPOSED);

    private final Map<String, BusinessActionDescriptor> registeredByExactKey;
    private final Set<String> registeredActionIds;

    private StaticDescriptorRegistry(Map<String, BusinessActionDescriptor> registeredByExactKey) {
        this.registeredByExactKey = Map.copyOf(registeredByExactKey);
        // actionId 集合直接取自注册描述符，不从 exactKey 反解——
        // actionVersion 若含 '@'，lastIndexOf 反解会误读 actionId（漂移误判为未注册）
        Set<String> ids = new HashSet<>();
        registeredByExactKey.values().forEach(descriptor -> ids.add(descriptor.actionId()));
        this.registeredActionIds = Set.copyOf(ids);
    }

    /** 显式注册构建器（每个条目独立 add；重复 (actionId, actionVersion) 拒绝）。 */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BusinessActionDescriptor resolve(String actionId, String actionVersion) {
        if (actionId == null || actionId.isBlank() || actionVersion == null || actionVersion.isBlank()) {
            // 缺失标识/版本无法证明「已显式发布」，与未注册同路径拒绝
            throw new ActionNotExposedException(NOT_REGISTERED);
        }
        BusinessActionDescriptor descriptor = registeredByExactKey.get(exactKey(actionId, actionVersion));
        if (descriptor == null
                || !descriptor.actionId().equals(actionId)
                || !descriptor.actionVersion().equals(actionVersion)) {
            // 版本漂移细分：同 actionId 存在其他已注册版本 → 漂移；否则未注册。
            // 外部结果码统一 ACTION_NOT_EXPOSED；细因是内部 reason，仅供审计。
            // 标识含 @ 的极端拼接碰撞会让 lookup 命中他人条目——命中后校验
            // (id, version) 一致性，不一致视同未命中（保守拒绝，不返回他人描述符）
            InternalReason reason = registeredActionIds.contains(actionId) ? VERSION_DRIFTED : NOT_REGISTERED;
            throw new ActionNotExposedException(reason);
        }
        if (!descriptor.enabled()) {
            throw new ActionNotExposedException(DISABLED);
        }
        return descriptor;
    }

    @Override
    public int registeredCount() {
        return registeredByExactKey.size();
    }

    private static String exactKey(String actionId, String actionVersion) {
        return actionId + "@" + actionVersion;
    }

    /** 显式注册构建器。 */
    public static final class Builder {

        private final Map<String, BusinessActionDescriptor> entries = new LinkedHashMap<>();

        /**
         * 显式注册一个描述符。
         *
         * <p>key 边界：{@code actionId + "@" + actionVersion} 拼接在标识本身含
         * {@code @} 的极端场景下可能碰撞——注册侧表现为<b>误判重复而拒绝注册</b>，
         * resolve 侧由命中后 (id, version) 一致性校验兜底为<b>视同未命中</b>——
         * 两侧均保守失败，不存在放行风险；规范动作标识不含 {@code @}。</p>
         *
         * @throws IllegalArgumentException 描述符缺失或 (actionId, actionVersion) 重复
         */
        public Builder register(BusinessActionDescriptor descriptor) {
            if (descriptor == null) {
                throw new IllegalArgumentException("descriptor 必选");
            }
            String key = exactKey(descriptor.actionId(), descriptor.actionVersion());
            if (entries.containsKey(key)) {
                throw new IllegalArgumentException(
                        "重复注册 (actionId, actionVersion) 被拒绝（禁覆盖式 put）：" + key);
            }
            entries.put(key, descriptor);
            return this;
        }

        public StaticDescriptorRegistry build() {
            return new StaticDescriptorRegistry(entries);
        }
    }
}

package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.InternalReason;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private final java.util.Set<String> registeredActionIds;

    private StaticDescriptorRegistry(Map<String, BusinessActionDescriptor> registeredByExactKey) {
        this.registeredByExactKey = Map.copyOf(registeredByExactKey);
        java.util.Set<String> ids = new java.util.HashSet<>();
        registeredByExactKey.keySet().forEach(key -> ids.add(actionIdOf(key)));
        this.registeredActionIds = java.util.Set.copyOf(ids);
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
        if (descriptor == null) {
            // 版本漂移细分：同 actionId 存在其他已注册版本 → 漂移；否则未注册。
            // 外部结果码统一 ACTION_NOT_EXPOSED；细因是内部 reason，仅供审计
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

    private static String actionIdOf(String exactKey) {
        return exactKey.substring(0, exactKey.lastIndexOf('@'));
    }

    /** 显式注册构建器。 */
    public static final class Builder {

        private final Map<String, BusinessActionDescriptor> entries = new LinkedHashMap<>();

        /**
         * 显式注册一个描述符。
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

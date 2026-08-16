package com.gallop.fastddd.agentaccess.contract;

import java.util.Set;

/**
 * 已发布业务动作描述符（Contract §5.3 权威字段表）。
 *
 * <p>只有显式 Descriptor 标记的 Action 才允许从 Agent Access Gateway 进入
 * 业务用例；禁止自动扫描并暴露 Controller、CRUD、Repository、SQL 或
 * {@code CommandHandler}——MCP tool、OpenAPI operation 或注解只能帮助发现，
 * 不能替代 Descriptor 的显式发布和本地授权（TM-10）。</p>
 *
 * <p><b>与 FastAgent ActionDefinition 所有权分离</b>：本类型只以
 * {@code actionId + actionVersion} 引用上游已治理发布的动作，不复刻、不推导
 * 上游定义；版本漂移（引用版本 ≠ 本端已注册版本）由 resolver fail-closed
 * 拒绝，不做「最相近版本」回退。类型级禁依赖 FastAgent 由 Enforcer/
 * ArchUnit 兜底（AST-06 同源）。</p>
 *
 * @param actionId          稳定动作标识
 * @param actionVersion     动作契约版本
 * @param effect            效果类型（READ/WRITE/TASK）
 * @param resourceTypes     可作用的业务资源类型（非空）
 * @param inputSchemaRef    版本化输入契约引用
 * @param outputSchemaRef   版本化输出契约引用
 * @param requiredPurposes  合法用途集合（非空）
 * @param dataClassification 输入输出数据分类上限
 * @param idempotencyMode   幂等模式（WRITE/TASK 必选；READ 可选）
 * @param preconditionMode  前置条件模式（必选）
 * @param reconciliationMode 对账模式（WRITE/TASK 必选；READ 可选）
 * @param timeoutClass      业务超时类别（不携带任意毫秒值）
 * @param enabled           是否已启用（未启用/未发布即拒绝）
 */
public record BusinessActionDescriptor(
        String actionId,
        String actionVersion,
        Effect effect,
        Set<String> resourceTypes,
        String inputSchemaRef,
        String outputSchemaRef,
        Set<String> requiredPurposes,
        String dataClassification,
        IdempotencyMode idempotencyMode,
        PreconditionMode preconditionMode,
        ReconciliationMode reconciliationMode,
        String timeoutClass,
        boolean enabled) {

    public BusinessActionDescriptor {
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        if (effect == null) {
            throw new IllegalArgumentException("effect 必选");
        }
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            throw new IllegalArgumentException("resourceTypes 必选且不得为空");
        }
        resourceTypes = Set.copyOf(resourceTypes);
        requireNonBlank("inputSchemaRef", inputSchemaRef);
        requireNonBlank("outputSchemaRef", outputSchemaRef);
        if (requiredPurposes == null || requiredPurposes.isEmpty()) {
            throw new IllegalArgumentException("requiredPurposes 必选且不得为空");
        }
        requiredPurposes = Set.copyOf(requiredPurposes);
        requireNonBlank("dataClassification", dataClassification);
        // §5.3 跨字段：idempotencyMode / reconciliationMode 在 WRITE/TASK 必选
        if ((effect == Effect.WRITE || effect == Effect.TASK) && idempotencyMode == null) {
            throw new IllegalArgumentException("idempotencyMode 在 WRITE/TASK 动作必选");
        }
        if (preconditionMode == null) {
            throw new IllegalArgumentException("preconditionMode 必选");
        }
        if ((effect == Effect.WRITE || effect == Effect.TASK) && reconciliationMode == null) {
            throw new IllegalArgumentException("reconciliationMode 在 WRITE/TASK 动作必选");
        }
        requireNonBlank("timeoutClass", timeoutClass);
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}

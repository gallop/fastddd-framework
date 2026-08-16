package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;

/**
 * 已发布动作描述符 read-only resolver（TM-10：显式注册，禁自动扫描）。
 *
 * <p>只有显式注册的 Descriptor 才可被解析；禁止扫描 Controller/CRUD/
 * Repository/CommandHandler/MCP tool 冒充发布。resolve 是精确版本匹配：
 * 请求版本 ≠ 已注册版本（无论高低）即版本漂移，fail-closed 拒绝为
 * {@code ACTION_NOT_EXPOSED}，不做「最相近版本」回退。</p>
 *
 * <p>实现必须保证：未注册、{@code enabled=false}、版本漂移三种输入抛出
 * {@link ActionNotExposedException} 且外部结果码统一为
 * {@code ACTION_NOT_EXPOSED}（存在性差异只允许体现在内部审计 reason，
 * 不得外泄到错误响应）。</p>
 */
public interface DescriptorRegistry {

    /**
     * 解析已发布动作描述符。
     *
     * @param actionId      动作标识
     * @param actionVersion 动作契约版本（精确匹配）
     * @return 已启用的描述符
     * @throws ActionNotExposedException 未注册 / disabled / 版本漂移
     */
    BusinessActionDescriptor resolve(String actionId, String actionVersion);

    /** 已注册条目数（= 显式注册数；供「无扫描增量」断言使用）。 */
    int registeredCount();
}

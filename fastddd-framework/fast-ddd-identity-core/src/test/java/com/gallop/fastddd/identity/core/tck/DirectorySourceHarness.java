package com.gallop.fastddd.identity.core.tck;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectorySource;

/**
 * TCK 宿主 SPI（W7-d）：被测 {@link DirectorySource} 实现的测试装配点。
 *
 * <p>自实现方（IdP 适配器宿主）实现本接口 + 继承 {@link DirectorySourceContractTck} 即可运行
 * 全套端口契约断言。本仓参考实现（feishu）在本仓内 dogfood 验证（W7-d 判据）。
 *
 * <p>约定：
 * <ul>
 *   <li>{@link #newScript()} 与 {@link #newSource(DirectoryUpstreamScript)} 产出的 script 与源
 *       绑定同一上游桩——源的外呼驱动 script 消费；</li>
 *   <li>harness 若持有需释放的资源（HTTP 桩、连接等），实现 {@link AutoCloseable}——
 *       契约基类在每测试后自动 close；</li>
 *   <li>瞬态重试预算须与真实缺省配置一致（参考实现 = 2 附加重试）。</li>
 * </ul>
 */
public interface DirectorySourceHarness {

    /** 新建上游脚本（每测试一个）。 */
    DirectoryUpstreamScript newScript();

    /** 以完整合法凭据构造被测源（与 script 绑定同一上游桩）。 */
    DirectorySource newSource(DirectoryUpstreamScript script);

    /**
     * 凭据缺失形态构造：期望被测实现 fail-closed——构造期即抛 {@link RuntimeException}
     * （消息非空），且不发生任何上游外呼（script 计数全零）。
     */
    DirectorySource newSourceWithMissingCredentials(DirectoryUpstreamScript script);

    /** 源侧瞬态重试预算（附加重试次数；单次外呼总尝试上限 = 1 + 此值）。 */
    int transientRetryBudget();

    /** 期望同步范围（{@link DirectorySource#scope()} 组件级断言用）。 */
    DirectoryScope expectedScope();
}

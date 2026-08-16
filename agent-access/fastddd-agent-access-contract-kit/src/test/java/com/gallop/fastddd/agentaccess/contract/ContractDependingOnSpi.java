package com.gallop.fastddd.agentaccess.contract;

import com.gallop.fastddd.agentaccess.spi.DescriptorRegistry;

/**
 * 违例样例：契约包依赖接缝包（B1 反例；仅测试导入用）。
 */
public final class ContractDependingOnSpi {

    private ContractDependingOnSpi() {
    }

    /** 违例依赖：.contract 不得引用 .spi 类型。 */
    @SuppressWarnings("unused")
    static DescriptorRegistry illegalReference() {
        throw new UnsupportedOperationException("违例样例");
    }
}

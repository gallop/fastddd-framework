package com.gallop.fastddd.agentaccess.contract;

/**
 * ExecutionPermit 最小载体接口（FDD-AA-10 骨架期占位）。
 *
 * <p>完整 {@code ExecutionPermit} 协议 DTO（Contract §5.2 全部字段）由 FDD-AA-11 实现；
 * 本接口先固定「许可必须携带已验证 Caller」这一最小语义，供 {@code .spi} 分流与
 * kit 装配校验在骨架期引用，避免提前冻结完整 Permit 结构。</p>
 */
public interface ExecutionPermitCarrier {

    /** 已验证调用责任链（来自许可本体，不是请求体自报身份）。 */
    CallerContext caller();
}

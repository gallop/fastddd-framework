package com.gallop.fastddd.identity.core.error;

/**
 * 入站目录事件拒收（W5 设计 §5.1，W5-d；M-1/M-30）：验签失败、密文不可信、
 * 信封畸形、scope 外（tenant_key/app_id 与宿主预期不符）。
 *
 * <p>调用方映射 401（验签族）——fail-closed：不确定的报文不落 Inbox、
 * 不产任何事实。平台侧重试由其自身的 ACK 语义驱动（拒收 = 无 ACK），
 * 重试风暴风险由 scope-外忽略分支（M-30 unconsumedType）分流。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryEventRejectedException extends RuntimeException {

    private final String reasonCode;

    public DirectoryEventRejectedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    /** 稳定原因码（signature-mismatch / decrypt-failed / malformed / scope-mismatch）。 */
    public String reasonCode() {
        return reasonCode;
    }
}

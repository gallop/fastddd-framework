package com.gallop.fastddd.identity.core.domain;

/**
 * {@code DirectoryEventChannel.receive} 的裁决产物（W5 设计 §5.1，W5-d）。
 *
 * <p><b>三态互斥契约</b>（实现方与消费方共同受约束）：</p>
 * <ul>
 *   <li>{@code challenge != null}：URL 验证请求（{@code url_verification}）——
 *       调用方 1s 内<b>逐字回显</b> challenge 明文（先解密后取值），不落任何
 *       存储、不产事实（M-2，设计 §11 矩阵——2026-10-09 R6-P3-2 更正，原误标
 *       M-3）；其余字段为 null；</li>
 *   <li>{@code event != null && !unconsumedType}：规范化事件就绪——调用方落
 *       Inbox（去重键 eventId）后 ACK（M-3 重复投递幂等吸收，设计 §11 矩阵——
 *       2026-10-09 R6-P3-2 更正，原误标 M-2）；</li>
 *   <li>{@code event != null && unconsumedType}：信封合法但事件类型不在消费集
 *       （如 {@code contact.scope.updated_v3}，W0-6 P-3 邻接发现）——调用方
 *       <b>不落 Inbox</b>、200 忽略 + 结构化日志零事实（M-30「拒收或忽略」
 *       取忽略分支，避免平台重试风暴）；event 保留仅供电面日志引用。</li>
 * </ul>
 *
 * @param challenge      URL 验证回显值（三态之一）
 * @param event          规范化事件（三态之一/之二）
 * @param unconsumedType 事件类型不在消费集（仅 event != null 时可有意义）
 * @author gallop
 * @since 1.1.0
 */
public record WebhookReceipt(String challenge, InboundEvent event, boolean unconsumedType) {

    public WebhookReceipt {
        if (challenge == null && event == null) {
            throw new IllegalArgumentException("challenge 与 event 至少其一（三态互斥契约）");
        }
        if (challenge != null && event != null) {
            throw new IllegalArgumentException("challenge 与 event 互斥（三态互斥契约）");
        }
    }

    /** 是否应落入 Inbox（消费类型事件）。 */
    public boolean shouldPersist() {
        return event != null && !unconsumedType;
    }
}

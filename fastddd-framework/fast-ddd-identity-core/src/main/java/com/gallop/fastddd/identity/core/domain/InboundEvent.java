package com.gallop.fastddd.identity.core.domain;

/**
 * 规范化入站目录事件（W5 设计 §5.1/§7.1，W5-d）：{@code DirectoryEventChannel}
 * 对原始 webhook 报文完成验签/解密/解析后的协议中立形态。
 *
 * <p>锚 v2.0 信封（W0-6 P-2 文档级冻结候选）：{@code eventId} ←
 * {@code header.event_id}（事件唯一标识，去重键）；{@code createTimeMillis} ←
 * {@code header.create_time}（毫秒串，事件发送时间——<b>非</b>资源修改时间、
 * 非全局单调序，W0-6 P-8）；{@code tenantKey}/{@code appId} ← 信封归属
 * （scope 外判定输入）；{@code payloadJson} = {@code event} 节点原文明文
 * （解密后），供 worker 侧 {@code resolveObject} 二次解析。</p>
 *
 * @param eventId        事件唯一标识（去重键；null/blank 非法）
 * @param eventType      事件类型名（如 {@code contact.user.updated_v3}）
 * @param createTimeMillis 事件创建时间戳（毫秒；0 = 信封缺失，防重放窗判定按缺失拒）
 * @param tenantKey      信封租户键
 * @param appId          信封应用 ID
 * @param payloadJson    {@code event} 节点原文明文 JSON
 * @author gallop
 * @since 1.1.0
 */
public record InboundEvent(String eventId, String eventType, long createTimeMillis,
                           String tenantKey, String appId, String payloadJson) {

    public InboundEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 必须非空（header.event_id）");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 必须非空（header.event_type）");
        }
        if (payloadJson == null) {
            throw new IllegalArgumentException("payloadJson 必须非 null（event 节点原文）");
        }
    }
}

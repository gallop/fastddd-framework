package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryEventExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;

import java.util.Optional;

/**
 * 目录事件通道端口（W5 设计 §3/§5.1/§7.1，W5-d）：入站接收链校验解码 +
 * 事件→回查目标解析。设计 §3 模块表未列本端口（W5-d 差异登记：单事件消费面
 * 的协议侧出口），语义归属「事件源适配」族。
 *
 * <p><b>receive 契约（§5.1 接收链全序，顺序即安全序）</b>：</p>
 * <ol>
 *   <li><b>验签</b>：sha256(timestamp + nonce + Encrypt Key + body) 比对
 *       {@code X-Lark-Signature}；Encrypt Key 未配置时退 Verification Token
 *       比对（二选一）——失败抛 {@link DirectoryEventRejectedException}
 *       （调用方 401，M-1 fail-closed）；</li>
 *   <li><b>解密</b>（若启用）：AES-256-CBC，key = SHA-256(Encrypt Key)，
 *       base64(iv + 密文)，去 PKCS7 填充——失败按拒收（密文不可信）；</li>
 *   <li><b>防重放</b>：信封 {@code header.create_time}（毫秒）入窗判定，
 *       窗外抛 {@link DirectoryEventExpiredException}（调用方 403；窗长为
 *       本地策略，实现方配置）；</li>
 *   <li><b>scope 判定</b>：tenant_key/app_id 与宿主预期不符 → 拒收（非本
 *       应用的事件，M-30「拒收或忽略」取拒收分支）；</li>
 *   <li><b>challenge 回显</b>：URL 验证请求 → {@link WebhookReceipt#challenge()}
 *       逐字回显（M-2，不落存储）。序差注记（R6-P3-2）：实现短路在本步——
 *       {@code url_verification} 无 {@code header.create_time}，按线性序字面执行
 *       必死于③防重放；本清单为安全序表述，challenge 特例在③④之前裁决
 *       （{@code FeishuEventChannel} 类 javadoc 同注）；</li>
 *   <li><b>规范化</b>：v2.0 信封六字段 + event 节点原文 → {@link WebhookReceipt}；
 *       事件类型不在消费集 → {@code unconsumedType=true}（调用方 200 忽略 +
 *       结构化日志零事实，M-30）。</li>
 * </ol>
 *
 * <p><b>resolveObject 契约（§7.1）</b>：消费类型事件 → 键二元组（人员 =
 * {@code event.object.open_id}；部门 = {@code event.object.open_department_id}，
 * W0-6 P-3 冻结候选）；<b>empty = 类型不在消费集</b>（调用方 markProcessed +
 * 结构化日志——接收链已忽略的防御纵深）；在集而键缺失 →
 * {@link com.gallop.fastddd.identity.core.error.DirectoryContractViolationException}
 * （协议漂移/实现缺陷，fail-loud）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryEventChannel {

    /** 接收链校验与解码（§5.1 全序，见接口 javadoc；纯本地计算，零外呼零存储）。 */
    WebhookReceipt receive(DirectoryWebhookRequest request);

    /** 事件 → 回查目标（empty = 非消费类型）。 */
    Optional<RecheckTarget> resolveObject(String eventType, String payloadJson);
}

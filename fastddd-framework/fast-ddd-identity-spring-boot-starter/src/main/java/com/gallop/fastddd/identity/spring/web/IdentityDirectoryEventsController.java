package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.InboundEvent;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryEventExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 目录增量事件接收端点（W5 设计 §5.1 接收链宿主侧，W5-d）：固定常量路径，
 * 每请求 = 限流 → 有界读体 → 通道六步序（验签/解密/防重放/scope/challenge/
 * 规范化，纯本地零外呼）→ challenge 回显或入箱 ACK。
 *
 * <p><b>状态码口径（W5-d 冻结）</b>：</p>
 * <ul>
 *   <li><b>429</b>：限流拒收（固定窗口分钟粒度，缺省 100 req/min——不读体即拒，
 *       设计 §10 "算法/数值 W5-d 冻结" 兑现）；</li>
 *   <li><b>413</b>：请求体超 {@code max-body-bytes} 上界（缺省 64KB；有界读体，
 *       不无界消费内存）；</li>
 *   <li><b>401</b>：鉴别/形态/scope 拒收（M-1——结构化安全 WARN，无 body 无密钥）；</li>
 *   <li><b>403</b>：防重放窗外（M-4——拒收零落库）；</li>
 *   <li><b>200</b>：challenge 回显（M-2：1 秒内原样返回 {@code {"challenge":...}}，
 *       加密形态已先解密）/ 非消费类型忽略（M-30：结构化日志零事实）/ 入箱成功
 *       与重复投递（M-3：event_id 主键幂等吸收，二次 ACK 成功）。</li>
 * </ul>
 *
 * <p><b>入箱即 ACK</b>：消费归宿主 worker（claim 租约/at-least-once，§7）——
 * 端点不做任何回查/事实写入（3 秒 ACK 窗口内只承担一条 INSERT）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class IdentityDirectoryEventsController {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityDirectoryEventsController.class);

    private final DirectoryEventChannel channel;
    private final DirectoryEventInbox inbox;
    private final FastDddIdentityProperties.Directory.Events.Webhook webhookConfig;
    private final FixedWindowRateLimiter rateLimiter;

    public IdentityDirectoryEventsController(DirectoryEventChannel channel,
                                             DirectoryEventInbox inbox,
                                             FastDddIdentityProperties.Directory.Events.Webhook webhookConfig) {
        if (webhookConfig == null || webhookConfig.getRateLimit() < 1) {
            throw new IllegalArgumentException(
                    "webhook.rateLimit 必须 ≥ 1（收到：" + (webhookConfig == null ? "null" : webhookConfig.getRateLimit()) + "）");
        }
        if (webhookConfig.getMaxBodyBytes() < 1) {
            throw new IllegalArgumentException(
                    "webhook.maxBodyBytes 必须 ≥ 1（收到：" + webhookConfig.getMaxBodyBytes() + "）");
        }
        this.channel = channel;
        this.inbox = inbox;
        this.webhookConfig = webhookConfig;
        this.rateLimiter = new FixedWindowRateLimiter(webhookConfig.getRateLimit());
    }

    @PostMapping(IdentityDirectoryEventsEndpoints.EVENTS_PATH)
    ResponseEntity<Map<String, String>> receive(HttpServletRequest request) throws IOException {
        // 限流先行（不读体即拒）
        if (!rateLimiter.tryAcquire(System.currentTimeMillis())) {
            LOG.warn("[directory-events] webhook 限流拒收（429）：窗口配额 {} req/min 已耗尽",
                    webhookConfig.getRateLimit());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "identity_event_rate_limited"));
        }
        // 有界读体（readNBytes；超界即拒——上界+1 读法区分"恰等于上界"与"超界"）
        int maxBody = webhookConfig.getMaxBodyBytes();
        byte[] bodyBytes = request.getInputStream().readNBytes(maxBody + 1);
        if (bodyBytes.length > maxBody) {
            LOG.warn("[directory-events] webhook 请求体超界拒收（413）：上界 {} 字节", maxBody);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "identity_event_payload_too_large"));
        }
        DirectoryWebhookRequest webhookRequest = new DirectoryWebhookRequest(
                new String(bodyBytes, StandardCharsets.UTF_8),
                request.getHeader("X-Lark-Signature"),
                request.getHeader("X-Lark-Request-Timestamp"),
                request.getHeader("X-Lark-Request-Nonce"));
        WebhookReceipt receipt;
        try {
            receipt = channel.receive(webhookRequest);
        } catch (DirectoryEventRejectedException e) {
            // M-1：鉴别/形态/scope 拒收——结构化安全日志（原因码白名单，无 body 无密钥）
            LOG.warn("[directory-events] 事件拒收（M-1）：reason={}", e.reasonCode());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "identity_event_rejected", "reason", e.reasonCode()));
        } catch (DirectoryEventExpiredException e) {
            // M-4：防重放窗外——拒收零落库
            LOG.warn("[directory-events] 事件超防重放窗拒收（M-4）：window={}",
                    webhookConfig.getRateLimit() >= 0 ? "local-policy" : "unreachable");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "identity_event_expired"));
        }
        if (receipt.challenge() != null) {
            // M-2：url_verification——1 秒内原样返回 {"challenge": ...}（加密形态已先解密）
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("challenge", receipt.challenge()));
        }
        if (receipt.unconsumedType()) {
            // M-30：非消费类型——200 忽略 + 结构化日志，零事实
            InboundEvent ignored = receipt.event();
            LOG.info("[directory-events] 非消费事件类型忽略（M-30）：eventType={}, eventId={}",
                    ignored.eventType(), ignored.eventId());
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        InboundEvent event = receipt.event();
        boolean inserted = inbox.persist(EventEnvelope.received(
                event.eventId(),
                new DirectoryScope(event.tenantKey(), event.appId()),
                event.eventType(),
                event.payloadJson(),
                Instant.now()));
        if (inserted) {
            LOG.info("[directory-events] 事件入箱待消费：eventId={}, eventType={}",
                    event.eventId(), event.eventType());
        } else {
            // M-3：重复投递同 eventId——主键幂等吸收，二次 ACK 成功（消费恰一次效果）
            LOG.info("[directory-events] 重复投递幂等吸收（M-3）：eventId={}", event.eventId());
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** 固定窗口限流（分钟粒度；单实例 CAS 无锁——W5-d 冻结算法；跨实例不聚合，属本地保守面）。 */
    static final class FixedWindowRateLimiter {

        private final int limit;

        private final AtomicReference<Window> window = new AtomicReference<>(new Window(0, 0));

        FixedWindowRateLimiter(int limit) {
            this.limit = limit;
        }

        boolean tryAcquire(long nowMillis) {
            long minute = Math.floorDiv(nowMillis, 60_000L);
            while (true) {
                Window current = window.get();
                if (current.minute != minute) {
                    if (window.compareAndSet(current, new Window(minute, 1))) {
                        return true;
                    }
                    continue;
                }
                if (current.count >= limit) {
                    return false;
                }
                if (window.compareAndSet(current, new Window(minute, current.count + 1))) {
                    return true;
                }
            }
        }

        private static final class Window {

            private final long minute;
            private final int count;

            private Window(long minute, int count) {
                this.minute = minute;
                this.count = count;
            }
        }
    }
}

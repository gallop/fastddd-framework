package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.InboundEvent;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryEventExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事件接收端点控制器级测试（W5 设计 §5.1 宿主侧状态码口径，W5-d）：M-1 401
 * 零入箱 / M-2 challenge 回显 / M-3 重复投递幂等吸收二次 ACK / M-4 403 零落库 /
 * M-30 非消费类型 200 忽略零事实 / 429 限流先于读体 / 413 超界先于通道 /
 * 恰等上界放行 / 构造期配置校验。standalone MockMvc + 通道/收件箱桩——
 * 六步序本身的契约由 feishu 模块 {@code FeishuEventChannelTest} 覆盖，
 * 本类只冻结端点状态码映射与入箱形态。
 *
 * @author gallop
 * @since 1.1.0
 */
class IdentityDirectoryEventsControllerTest {

    // ---- 桩（记录面 + 定向面；断言只看记录） ----

    /** 通道桩：receipt 定向返回 / failure 定向抛出；receive 计数供"限流先于读体"断言。 */
    static final class ScriptedChannel implements DirectoryEventChannel {
        WebhookReceipt receipt = new WebhookReceipt("ck-default", null, false);
        RuntimeException failure;
        int receives;

        @Override
        public WebhookReceipt receive(DirectoryWebhookRequest request) {
            receives++;
            if (failure != null) {
                throw failure;
            }
            return receipt;
        }

        @Override
        public Optional<RecheckTarget> resolveObject(String eventType, String payloadJson) {
            throw new UnsupportedOperationException("端点路径不走 resolveObject");
        }
    }

    /** 收件箱桩：记录入箱信封；insertReturns 定向幂等吸收语义。 */
    static final class RecordingInbox implements DirectoryEventInbox {
        final List<EventEnvelope> persisted = new ArrayList<>();
        boolean insertReturns = true;

        @Override
        public boolean persist(EventEnvelope envelope) {
            persisted.add(envelope);
            return insertReturns;
        }

        @Override
        public List<EventEnvelope> claim(int limit, Duration lease) {
            throw new UnsupportedOperationException("端点路径不走 claim");
        }

        @Override
        public int reclaimExpired() {
            throw new UnsupportedOperationException("端点路径不走 reclaim");
        }

        @Override
        public void markProcessed(String eventId) {
            throw new UnsupportedOperationException("端点路径不走 markProcessed");
        }

        @Override
        public void markFailed(String eventId, String reason) {
            throw new UnsupportedOperationException("端点路径不走 markFailed");
        }
    }

    // ---- 构造工具 ----

    private static FastDddIdentityProperties.Directory.Events.Webhook webhook(int rateLimit, int maxBodyBytes) {
        FastDddIdentityProperties properties = new FastDddIdentityProperties();
        FastDddIdentityProperties.Directory.Events.Webhook webhook =
                properties.getDirectory().getEvents().getWebhook();
        webhook.setRateLimit(rateLimit);
        webhook.setMaxBodyBytes(maxBodyBytes);
        return webhook;
    }

    private static MockMvc mvc(ScriptedChannel channel, RecordingInbox inbox,
                               int rateLimit, int maxBodyBytes) {
        return MockMvcBuilders.standaloneSetup(new IdentityDirectoryEventsController(
                channel, inbox, webhook(rateLimit, maxBodyBytes))).build();
    }

    private static InboundEvent inboundEvent() {
        return new InboundEvent("ev-1", "contact.user.updated_v3", Instant.now().toEpochMilli(),
                "tenant-1", "cli_app", "{\"object\":{\"open_id\":\"ou_1\"}}");
    }

    // ---- M-1：鉴别/形态/scope 拒收 → 401 零入箱 ----

    @Test
    void m1_rejectedEventGives401WithReasonAndZeroPersist() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.failure = new DirectoryEventRejectedException("signature-mismatch", "签名不符（测试桩）");
        RecordingInbox inbox = new RecordingInbox();

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("identity_event_rejected"))
                .andExpect(jsonPath("$.reason").value("signature-mismatch"));

        assertThat(inbox.persisted).isEmpty();
    }

    // ---- M-2：challenge 回显（JSON 形态冻结） ----

    @Test
    void m2_challengeIsEchoedAsJsonWithoutPersist() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt("ck-url-verification", null, false);
        RecordingInbox inbox = new RecordingInbox();

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.challenge").value("ck-url-verification"));

        assertThat(inbox.persisted).isEmpty();
    }

    // ---- M-3：入箱成功与重复投递（两种都 200 ACK） ----

    @Test
    void m3_newEventIsPersistedAndAcked() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt(null, inboundEvent(), false);
        RecordingInbox inbox = new RecordingInbox();

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(inbox.persisted).hasSize(1);
        EventEnvelope envelope = inbox.persisted.get(0);
        assertThat(envelope.eventId()).isEqualTo("ev-1");
        assertThat(envelope.eventType()).isEqualTo("contact.user.updated_v3");
        assertThat(envelope.payload()).contains("ou_1");
        assertThat(envelope.scope()).isEqualTo(new DirectoryScope("tenant-1", "cli_app"));
    }

    @Test
    void m3_duplicateDeliveryIsAbsorbedWithSecondAck() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt(null, inboundEvent(), false);
        RecordingInbox inbox = new RecordingInbox();
        inbox.insertReturns = false;

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(inbox.persisted).hasSize(1);
    }

    // ---- M-4：防重放窗外 → 403 零落库 ----

    @Test
    void m4_expiredEventGives403WithZeroPersist() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.failure = new DirectoryEventExpiredException("事件越过防重放窗（测试桩）");
        RecordingInbox inbox = new RecordingInbox();

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("identity_event_expired"));

        assertThat(inbox.persisted).isEmpty();
    }

    // ---- M-30：非消费类型 → 200 忽略零事实 ----

    @Test
    void m30_unconsumedTypeIsIgnoredWithoutPersist() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt(null, new InboundEvent("ev-scope",
                "contact.scope.updated_v3", Instant.now().toEpochMilli(), "tenant-1", "cli_app", "{}"), true);
        RecordingInbox inbox = new RecordingInbox();

        mvc(channel, inbox, 100, 64 * 1024)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        assertThat(inbox.persisted).isEmpty();
    }

    // ---- 429：限流先于读体（通道零触碰） ----

    @Test
    void rateLimitExhaustionGives429WithoutTouchingChannel() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt("ck-1", null, false);
        RecordingInbox inbox = new RecordingInbox();
        MockMvc mvc = mvc(channel, inbox, 1, 64 * 1024);

        mvc.perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                .andExpect(status().isOk());

        // 同一固定窗口内第二次请求：不读体即拒——通道与收件箱均零触碰
        mvc.perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH)
                        .content("anything-not-read"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("identity_event_rate_limited"));

        assertThat(channel.receives).isEqualTo(1);
        assertThat(inbox.persisted).isEmpty();
    }

    // ---- 413：超界拒收（上界+1 读法，通道零触碰） ----

    @Test
    void oversizedBodyGives413BeforeChannel() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        RecordingInbox inbox = new RecordingInbox();
        byte[] oversized = "x".repeat(17).getBytes(StandardCharsets.UTF_8);

        mvc(channel, inbox, 100, 16)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("identity_event_payload_too_large"));

        assertThat(channel.receives).isZero();
        assertThat(inbox.persisted).isEmpty();
    }

    /** 恰等于上界的请求体放行（readNBytes(max+1) 上界+1 读法区分"恰等"与"超界"）。 */
    @Test
    void exactMaxBodyIsAccepted() throws Exception {
        ScriptedChannel channel = new ScriptedChannel();
        channel.receipt = new WebhookReceipt("ck-exact", null, false);
        RecordingInbox inbox = new RecordingInbox();
        byte[] exact = "x".repeat(16).getBytes(StandardCharsets.UTF_8);

        mvc(channel, inbox, 100, 16)
                .perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content(exact))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").value("ck-exact"));
    }

    // ---- 构造期配置校验（fail-fast） ----

    @Test
    void constructorRejectsNonPositiveRateLimit() {
        assertThatThrownBy(() -> new IdentityDirectoryEventsController(
                new ScriptedChannel(), new RecordingInbox(), webhook(0, 64 * 1024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateLimit");
    }

    @Test
    void constructorRejectsNonPositiveMaxBodyBytes() {
        assertThatThrownBy(() -> new IdentityDirectoryEventsController(
                new ScriptedChannel(), new RecordingInbox(), webhook(100, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBodyBytes");
    }
}

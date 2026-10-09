package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryEventInboxAdmin;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 增量事件管理端点控制器级测试（W5 设计 §5.1 管理面四态责任序，W5-d）：
 * ① 认证在场缺失 → 401（收件箱零触碰）；② eventId 协议外（空白/超宽）→ 400；
 * ③ 逐操作策略裁决（INBOX_REPLAY，null-target 冻结签名）→ 403；④ 无可重放行
 * → 404；成功 → 200 + 审计行（操作码/操作者/入参/成功位）+ 审计失败不影响
 * 成功响应（best-effort）。HTTP 级链上形态（CSRF/会话/链无关防线）由
 * {@code IdentityDirectoryEventsHttpFlowTest} 覆盖，本类冻结四态映射与审计形态。
 *
 * @author gallop
 * @since 1.1.0
 */
class IdentityDirectoryEventsAdminControllerTest {

    private static final Actor ADMIN = new Actor("tenant-1", PrincipalId.of("ou-admin-1"),
            PrincipalType.HUMAN);

    // ---- 桩 ----

    static final class StubActorResolver implements ActorResolver {
        boolean anonymous;
        int consultations;

        @Override
        public Actor currentActor() {
            consultations++;
            if (anonymous) {
                throw new IdentityRequiredException("无认证在场（测试桩）");
            }
            return ADMIN;
        }

        @Override
        public java.util.Optional<Actor> tryCurrentActor() {
            return java.util.Optional.of(ADMIN);
        }
    }

    /** 收件箱管理桩：记录 replay 调用，replayReturns 定向四态之 404/200。 */
    static final class StubInboxAdmin implements DirectoryEventInboxAdmin {
        final List<String> replayed = new ArrayList<>();
        boolean replayReturns = true;

        @Override
        public boolean replay(String eventId) {
            replayed.add(eventId);
            return replayReturns;
        }
    }

    /** 策略桩：denied 定向抛（透传 operation/target 语义），缺省放行。 */
    static final class StubPolicy implements IdentityAdminPolicy {
        boolean denied;
        IdentityAdminOperation seenOperation;
        int consultations;

        @Override
        public void checkPrincipalAdmin(IdentityAdminOperation operation, Actor actor,
                                        PrincipalId targetPrincipalId) {
            consultations++;
            seenOperation = operation;
            if (denied) {
                throw new IdentityAdminDeniedException(operation.name(),
                        targetPrincipalId == null ? null : targetPrincipalId.value(),
                        "非管理员（测试策略桩）");
            }
        }
    }

    /** 审计桩：记录写入行；failure 定向验证 best-effort。 */
    static final class RecordingAuditWriter implements IdentityAuditWriter {
        final List<IdentityAuditRecord> records = new ArrayList<>();
        boolean failure;

        @Override
        public void write(IdentityAuditRecord record) {
            if (failure) {
                throw new IllegalStateException("审计写入失败（测试桩）");
            }
            records.add(record);
        }
    }

    private static MockMvc mvc(StubActorResolver resolver, StubInboxAdmin inboxAdmin,
                               StubPolicy policy, RecordingAuditWriter auditWriter) {
        return MockMvcBuilders.standaloneSetup(new IdentityDirectoryEventsAdminController(
                inboxAdmin, policy, auditWriter, resolver)).build();
    }

    private static String replayPath(String eventId) {
        return "/api/identity/admin/directory/events/" + eventId + "/replay";
    }

    // ---- ① 认证在场缺失 → 401 ----

    @Test
    void anonymousCallGives401WithoutTouchingInboxOrPolicy() throws Exception {
        StubActorResolver resolver = new StubActorResolver();
        resolver.anonymous = true;
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();
        StubPolicy policy = new StubPolicy();

        mvc(resolver, inboxAdmin, policy, new RecordingAuditWriter())
                .perform(post(replayPath("ev-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("identity_unauthorized"));

        assertThat(inboxAdmin.replayed).isEmpty();
        assertThat(policy.consultations).isZero();
    }

    // ---- ② eventId 协议外 → 400 ----

    @Test
    void blankEventIdGives400() throws Exception {
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();

        mvc(new StubActorResolver(), inboxAdmin, new StubPolicy(), new RecordingAuditWriter())
                .perform(post(replayPath(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_event_id"));

        assertThat(inboxAdmin.replayed).isEmpty();
    }

    @Test
    void overlongEventIdGives400() throws Exception {
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();
        String overlong = "e".repeat(129);

        mvc(new StubActorResolver(), inboxAdmin, new StubPolicy(), new RecordingAuditWriter())
                .perform(post(replayPath(overlong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_event_id"));

        assertThat(inboxAdmin.replayed).isEmpty();
    }

    // ---- ③ 策略裁决拒绝 → 403（INBOX_REPLAY，null-target） ----

    @Test
    void policyDenialGives403WithOperationAndNullTargetSemantics() throws Exception {
        StubPolicy policy = new StubPolicy();
        policy.denied = true;
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();

        mvc(new StubActorResolver(), inboxAdmin, policy, new RecordingAuditWriter())
                .perform(post(replayPath("ev-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("identity_admin_denied"))
                .andExpect(jsonPath("$.operation").value("INBOX_REPLAY"));

        // 逐操作裁决透传 INBOX_REPLAY；无固定目标主体（null，策略签名零变更）
        assertThat(policy.seenOperation).isEqualTo(IdentityAdminOperation.INBOX_REPLAY);
        assertThat(inboxAdmin.replayed).isEmpty();
    }

    // ---- ④ 无可重放行 → 404 ----

    @Test
    void notReplayableGives404() throws Exception {
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();
        inboxAdmin.replayReturns = false;

        mvc(new StubActorResolver(), inboxAdmin, new StubPolicy(), new RecordingAuditWriter())
                .perform(post(replayPath("ev-gone")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("identity_event_not_replayable"))
                .andExpect(jsonPath("$.eventId").value("ev-gone"));
    }

    // ---- 成功 → 200 + 审计行 ----

    @Test
    void successfulReplayGives200AndWritesAuditRow() throws Exception {
        StubInboxAdmin inboxAdmin = new StubInboxAdmin();
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();

        mvc(new StubActorResolver(), inboxAdmin, new StubPolicy(), auditWriter)
                .perform(post(replayPath("ev-failed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("ev-failed"))
                .andExpect(jsonPath("$.outcome").value("requeued"));

        assertThat(inboxAdmin.replayed).containsExactly("ev-failed");
        assertThat(auditWriter.records).hasSize(1);
        IdentityAuditRecord record = auditWriter.records.get(0);
        assertThat(record.actorPrincipalId()).isEqualTo(ADMIN.principalId());
        assertThat(record.operation()).isEqualTo("identity-admin-inbox-replay");
        assertThat(record.detail()).isEqualTo("eventId=ev-failed");
        assertThat(record.success()).isTrue();
    }

    /** 审计 best-effort：写入失败只记日志，不影响成功响应。 */
    @Test
    void auditFailureDoesNotAffectSuccessResponse() throws Exception {
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();
        auditWriter.failure = true;

        mvc(new StubActorResolver(), new StubInboxAdmin(), new StubPolicy(), auditWriter)
                .perform(post(replayPath("ev-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("requeued"));

        assertThat(auditWriter.records).isEmpty();
    }
}

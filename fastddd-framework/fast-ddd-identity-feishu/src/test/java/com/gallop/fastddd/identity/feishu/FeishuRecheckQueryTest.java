package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 回查单查适配器单测（W5 设计 §7.3 第②步，W5-d）：users/get 与 departments/get
 * 字段映射、四布尔停用派生、部门 40015 → ABSENT（Optional.empty）、根部门归化
 * （parent="0" → null）、传输故障 → Unavailable（有界重试后）、请求形态
 * （user_id_type/department_id_type 查询参）。
 *
 * @author gallop
 * @since 1.1.0
 */
class FeishuRecheckQueryTest {

    private FeishRecheckFixture fixture() throws IOException {
        return new FeishRecheckFixture();
    }

    /** 桩 + 适配器（maxRetries=1，退避 20ms——重试计数可断言且总时长可控）。 */
    static final class FeishRecheckFixture implements AutoCloseable {
        final FeishuStubServer stub = new FeishuStubServer();
        final FeishuRecheckQuery query;

        FeishRecheckFixture() throws IOException {
            query = new FeishuRecheckQuery(
                    FeishuStubServer.propertiesAgainst(stub),
                    FeishuStubServer.directoryPropertiesAgainst(stub),
                    1, Duration.ofMillis(20));
        }

        @Override
        public void close() {
            stub.close();
        }
    }

    @Test
    void personDetailMapsToObservationWithActiveStatus() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setUserDetailHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                    FeishuStubServer.userDetail("ou_9", "张三", "San Zhang",
                            false, false, false, false, "od_0", "od_1")));

            Optional<ObservedDirectoryObject> observed =
                    f.query.fetchObserved(DirectoryObjectType.PERSON, "ou_9");

            assertThat(observed).isPresent();
            assertThat(observed.get().deactivated()).isFalse();
            assertThat(observed.get().person().openId()).isEqualTo("ou_9");
            assertThat(observed.get().person().name()).isEqualTo("张三");
            assertThat(observed.get().person().enName()).isEqualTo("San Zhang");
            assertThat(observed.get().person().departmentIds()).containsExactly("od_0", "od_1");
            assertThat(f.stub.lastUserDetailPath()).endsWith("/open-apis/contact/v3/users/ou_9");
            assertThat(FeishuStubServer.queryParam(f.stub.lastUserDetailQuery(), "user_id_type"))
                    .isEqualTo("open_id");
        }
    }

    @Test
    void eachDeactivationFlagAloneDerivesDeactivated() throws Exception {
        boolean[][] cases = {
                {true, false, false, false},   // is_frozen
                {false, true, false, false},   // is_resigned
                {false, false, true, false},   // is_exited
                {false, false, false, true}    // is_unjoin
        };
        for (boolean[] flags : cases) {
            try (FeishRecheckFixture f = fixture()) {
                f.stub.setUserDetailHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                        FeishuStubServer.userDetail("ou_9", "张三", "San",
                                flags[0], flags[1], flags[2], flags[3])));

                Optional<ObservedDirectoryObject> observed =
                        f.query.fetchObserved(DirectoryObjectType.PERSON, "ou_9");

                assertThat(observed).as("frozen=%s resigned=%s exited=%s unjoin=%s",
                        flags[0], flags[1], flags[2], flags[3]).isPresent();
                assertThat(observed.get().deactivated()).isTrue();
            }
        }
    }

    @Test
    void departmentDetailMapsAndNormalizesRootSentinelParent() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setDepartmentDetailHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                    FeishuStubServer.departmentDetail("od_9", "0", "总部", false)));

            Optional<ObservedDirectoryObject> observed =
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_9");

            assertThat(observed).isPresent();
            assertThat(observed.get().deactivated()).isFalse();
            assertThat(observed.get().department().departmentId()).isEqualTo("od_9");
            assertThat(observed.get().department().parentDepartmentId()).isNull();
            assertThat(observed.get().department().name()).isEqualTo("总部");
            assertThat(f.stub.lastDepartmentDetailPath()).endsWith("/open-apis/contact/v3/departments/od_9");
            assertThat(FeishuStubServer.queryParam(f.stub.lastDepartmentDetailQuery(),
                    "department_id_type")).isEqualTo("open_department_id");
        }
    }

    @Test
    void nonRootParentIsKeptVerbatim() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setDepartmentDetailHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                    FeishuStubServer.departmentDetail("od_child", "od_9", "研发部", false)));

            Optional<ObservedDirectoryObject> observed =
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_child");

            assertThat(observed.get().department().parentDepartmentId()).isEqualTo("od_9");
        }
    }

    @Test
    void deletedDepartmentDerivesDeactivated() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setDepartmentDetailHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                    FeishuStubServer.departmentDetail("od_9", "0", "总部", true)));

            Optional<ObservedDirectoryObject> observed =
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_9");

            assertThat(observed.get().deactivated()).isTrue();
        }
    }

    @Test
    void departmentNotExistCodeYieldsAuthoritativeAbsent() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setDepartmentDetailHandler(exchange ->
                    FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.contactError(40015)));

            Optional<ObservedDirectoryObject> observed =
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_gone");

            assertThat(observed).isEmpty();
            assertThat(f.stub.departmentDetailHits.get()).isEqualTo(1);
        }
    }

    @Test
    void upstreamOverloadIsUnavailableAfterBoundedRetries() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            // 503 过载 → RetryableSignal（过载判定先于业务码解释，M1-TAG-02）；
            // 断连形态不在此断言计数——JDK HttpClient 对无响应断连有透明重试，计数不定
            f.stub.setDepartmentDetailHandler(exchange ->
                    FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.contactError(503)));

            assertThatThrownBy(() ->
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_9"))
                    .isInstanceOf(DirectorySourceUnavailableException.class);
            // 首次 + 1 次重试 = 2 次有界尝试（表 7-1⑥：故障 ≠ 删除——不上抛即零墓碑前提）
            assertThat(f.stub.departmentDetailHits.get()).isEqualTo(2);
        }
    }

    @Test
    void droppedConnectionIsUnavailable() throws Exception {
        try (FeishRecheckFixture f = fixture()) {
            f.stub.setDepartmentDetailHandler(exchange -> FeishuStubServer.dropConnection());

            assertThatThrownBy(() ->
                    f.query.fetchObserved(DirectoryObjectType.DEPARTMENT, "od_9"))
                    .isInstanceOf(DirectorySourceUnavailableException.class);
        }
    }
}

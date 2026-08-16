package com.gallop.fastddd.agentaccess.contract;

/**
 * Agent Access 协议版本常量（Contract fastddd-agent-access/0.1 §5.1 / AA-04 §2.2 四版本轴）。
 *
 * <p>四版本轴互不推导：</p>
 * <ul>
 *   <li>制品版本：{@code 0.1.0-jdk21-alpha.1}（POM）</li>
 *   <li>契约文档版本：{@code fastddd-agent-access/0.1}（文档标识，不出现在 wire）</li>
 *   <li>wire {@code schemaVersion}：{@value #WIRE_SCHEMA_VERSION}（本常量；不得写成契约文档标识）</li>
 *   <li>{@code digestSchemaVersion}：{@value #DIGEST_SCHEMA_VERSION}（摘要域分离用）</li>
 * </ul>
 */
public final class AgentAccessProtocolVersions {

    /** wire 结构版本（CallerContext/ExecutionPermit 等 protocol DTO 的 schemaVersion 值）。 */
    public static final String WIRE_SCHEMA_VERSION = "0.1";

    /** 授权/绑定摘要的 schema 版本（域分离与摘要可比性判定用）。 */
    public static final int DIGEST_SCHEMA_VERSION = 1;

    private AgentAccessProtocolVersions() {
    }
}

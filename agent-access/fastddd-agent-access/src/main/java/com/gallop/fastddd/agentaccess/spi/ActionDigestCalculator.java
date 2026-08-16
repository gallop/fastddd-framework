package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * 双域摘要计算器（Contract §5.5 canonical 规则 3/4/5；golden vectors v1）。
 *
 * <p>摘要算法：{@code SHA-256( UTF-8( domainTag ‖ U+0000 ‖ JCS(投影后对象) ) )}，
 * 输出小写 hex。域分离标签（v1 冻结值，golden vectors 表头同源）：</p>
 * <ul>
 *   <li>{@link #EFFECT_DIGEST_DOMAIN_TAG} {@code fastddd.agentaccess.effectDigest/v1}；</li>
 *   <li>{@link #AUTHORIZATION_BINDING_DIGEST_DOMAIN_TAG}
 *       {@code fastddd.agentaccess.authorizationBindingDigest/v1}。</li>
 * </ul>
 *
 * <p>同一投影对象在两个标签下必须产生不同摘要（GV-06/07 哨兵）。投影对象由
 * 调用方构造（effectProjection/bindingProjection 或宿主侧等价投影），本类不
 * 决定字段进域规则——「effectDigest 不得包含可变绑定项」由投影构造处保证
 * （§5.5 规则 2），摘要计算只做确定性序列化。</p>
 *
 * <p>{@code digestSchemaVersion}（当前 {@link AgentAccessProtocolVersions#DIGEST_SCHEMA_VERSION}
 * = 1）：消费方校验入口 {@link #requireSupportedDigestSchemaVersion(int)}——未识别
 * 版本必须拒绝，禁止猜测降级或沿用旧算法静默计算（§5.5 规则 5）。</p>
 */
public final class ActionDigestCalculator {

    /** effectDigest 域分离标签（v1 冻结值）。 */
    public static final String EFFECT_DIGEST_DOMAIN_TAG = "fastddd.agentaccess.effectDigest/v1";

    /** authorizationBindingDigest 域分离标签（v1 冻结值）。 */
    public static final String AUTHORIZATION_BINDING_DIGEST_DOMAIN_TAG =
            "fastddd.agentaccess.authorizationBindingDigest/v1";

    private ActionDigestCalculator() {
    }

    /** effectDigest：稳定业务效果语义摘要（域分离 + JCS + SHA-256 小写 hex）。 */
    public static String effectDigest(Map<String, Object> effectProjection) {
        return digest(EFFECT_DIGEST_DOMAIN_TAG, effectProjection);
    }

    /** authorizationBindingDigest：授权绑定摘要（域分离 + JCS + SHA-256 小写 hex）。 */
    public static String authorizationBindingDigest(Map<String, Object> bindingProjection) {
        return digest(AUTHORIZATION_BINDING_DIGEST_DOMAIN_TAG, bindingProjection);
    }

    /**
     * 域分离摘要计算（golden vectors §1 算法定义）。
     *
     * @throws IllegalArgumentException domainTag/projection 缺失，或投影含非法值
     *                                  （由 {@link JsonCanonicalizer} fail-closed）
     */
    public static String digest(String domainTag, Map<String, Object> projection) {
        if (domainTag == null || domainTag.isBlank()) {
            throw new IllegalArgumentException("domainTag 必选且不得为空白");
        }
        if (projection == null) {
            throw new IllegalArgumentException("projection 必选");
        }
        String canonical = JsonCanonicalizer.canonicalize(projection);
        MessageDigest sha256 = newSha256();
        sha256.update(domainTag.getBytes(StandardCharsets.UTF_8));
        sha256.update((byte) 0); // U+0000 分隔符
        sha256.update(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sha256.digest());
    }

    /**
     * digest schema 版本校验（消费侧）：未识别版本 fail-closed 拒绝
     * （§5.5 规则 5：禁止猜测降级或沿用旧算法静默计算）。
     */
    public static void requireSupportedDigestSchemaVersion(int digestSchemaVersion) {
        if (digestSchemaVersion != AgentAccessProtocolVersions.DIGEST_SCHEMA_VERSION) {
            throw new IllegalArgumentException("digestSchemaVersion 不受支持：期望 "
                    + AgentAccessProtocolVersions.DIGEST_SCHEMA_VERSION + "，实际 " + digestSchemaVersion);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // JDK 规范保证 SHA-256 存在（Every Java implementation must support）
            throw new IllegalStateException("JDK 缺少 SHA-256 算法（违反 JDK 规范）", e);
        }
    }
}

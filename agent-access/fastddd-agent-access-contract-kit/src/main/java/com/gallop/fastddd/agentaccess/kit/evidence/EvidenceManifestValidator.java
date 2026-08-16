package com.gallop.fastddd.agentaccess.kit.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 证据 manifest 校验器（AA-05 R8 自动化承接）：schema 完备性与一致性校验，
 * 输出 issue 列表（空列表=通过）——不抛异常，供宿主构建以 {@code issues.isEmpty()}
 * 作为门禁、issue 全文进构建日志。
 *
 * <p>校验规则：</p>
 * <ol>
 *   <li>schemaVersion 必须等于当前版本（未知版本 fail-closed，不做前向猜测）；</li>
 *   <li>制品坐标非空白、checksum 形态为 {@code sha256:}+64 位小写 hex；</li>
 *   <li>testResults 与 registeredOnlyTestIds 的每个 ID 必须出自 {@link TestIdCatalog}
 *       全集，且两集合不相交（同一 ID 不得既执行又只登记）；</li>
 *   <li>任一 PASSED 时 {@code sensitiveScanFindings} 必须为空（AST-02：
 *       敏感扫描 0 命中是 PASSED 的必要条件）；</li>
 *   <li>generatedAt 必选。</li>
 * </ol>
 *
 * <p>本校验器不证明「测试真实运行过」——它校验填报结构的一致性；
 * 执行真实性由构建产物（surefire 报告、conformance 输出）与 checksum 对账承载。</p>
 */
public final class EvidenceManifestValidator {

    private static final Pattern SHA256_CHECKSUM =
            Pattern.compile("sha256:[0-9a-f]{64}");

    public List<String> validate(EvidenceManifest manifest) {
        List<String> issues = new ArrayList<>();
        if (manifest == null) {
            issues.add("manifest 必选");
            return List.copyOf(issues);
        }
        if (manifest.schemaVersion() != EvidenceManifest.CURRENT_SCHEMA_VERSION) {
            issues.add("schemaVersion 不支持：" + manifest.schemaVersion()
                    + "（当前 " + EvidenceManifest.CURRENT_SCHEMA_VERSION + "，未知版本 fail-closed）");
        }
        if (isBlank(manifest.artifactCoordinates())) {
            issues.add("artifactCoordinates 必选（被证明制品的 Maven 坐标）");
        }
        if (manifest.artifactChecksum() == null
                || !SHA256_CHECKSUM.matcher(manifest.artifactChecksum()).matches()) {
            issues.add("artifactChecksum 必须是 sha256:+64 位小写 hex 形态");
        }
        for (String testId : manifest.testResults().keySet()) {
            if (!TestIdCatalog.isKnown(testId)) {
                issues.add("testResults 含未知 test ID：" + testId + "（必须出自 AA-03/Contract §12/向量文档全集）");
            }
        }
        for (String testId : manifest.registeredOnlyTestIds()) {
            if (!TestIdCatalog.isKnown(testId)) {
                issues.add("registeredOnlyTestIds 含未知 test ID：" + testId);
            }
        }
        for (Map.Entry<String, TestOutcome> entry : manifest.testResults().entrySet()) {
            if (manifest.registeredOnlyTestIds().contains(entry.getKey())) {
                issues.add("test ID 同时出现在执行结果与只登记集合：" + entry.getKey()
                        + "（不得既宣称执行又只登记）");
            }
            if (entry.getValue() == null) {
                issues.add("testResults 结果缺失：" + entry.getKey());
            }
        }
        boolean anyPassed = manifest.testResults().containsValue(TestOutcome.PASSED);
        if (anyPassed && !manifest.sensitiveScanFindings().isEmpty()) {
            issues.add("存在 PASSED 但敏感扫描非 0 命中（AST-02：0 命中是 PASSED 的必要条件）");
        }
        if (manifest.generatedAt() == null) {
            issues.add("generatedAt 必选");
        }
        return List.copyOf(issues);
    }

    public boolean isValid(EvidenceManifest manifest) {
        return validate(manifest).isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.gallop.fastddd.agentaccess.kit.evidence;

import com.gallop.fastddd.agentaccess.kit.conformance.PersistentDedupConformanceContract;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * FDD-AA-15/12.5 kit 制品化形态断言（AA-04 §7 MC-8）：
 * <ul>
 *   <li>kit 普通 jar 以 compile scope 传递 {@code junit-jupiter-api}——证据是 kit
 *       <b>main</b> 源码（{@code PersistentDedupConformanceContract}）本身标注
 *       {@code @Test}：只有 JUnit API 在 compile 域，宿主 test scope 引 kit 后
 *       继承 conformance 基类才能免自配 API 依赖（Testcontainers 等仍需宿主自配，
 *       见 kit README 传递边界）；</li>
 *   <li>kit main 工具面（arch 规则/故障注入/扫描器/证据模型）与 conformance 基类
 *       <b>同源发布</b>：全部类来自同一 code source（同 jar/classes 目录），
 *       不存在散落多个制品的漂移。</li>
 * </ul>
 *
 * <p><b>边界登记（不冒充）</b>：clean consumer（空工程只依赖 kit 制品）的消费
 * 证据归 FDD-AA-16；本测试只锁定 kit 制品形态自洽（同源 + API 传递形态）。</p>
 */
class KitArtifactShapeTest {

    /** kit main 的 conformance 基类携带 @Test 注解 → junit-jupiter-api 在 kit compile 传递域 */
    @Test
    void conformanceContractCarriesJupiterTestAnnotations() {
        Method[] methods = PersistentDedupConformanceContract.class.getDeclaredMethods();
        assertTrue(methods.length > 0, "conformance 基类必须有可继承的测试方法");
        boolean anyTestAnnotated = Arrays.stream(methods)
                .anyMatch(m -> m.isAnnotationPresent(org.junit.jupiter.api.Test.class));
        assertTrue(anyTestAnnotated,
                "kit main 必须在 compile 域携带 JUnit5 注解（普通 jar 传递形态，非 test-jar）");
    }

    /** kit main 工具面与 conformance 基类来自同一 code source（同 jar/同 classes 目录发布） */
    @Test
    void kitToolSurfaceShipsOnTheSameCodeSourceAsTheContract() {
        URL contractSource = codeSourceOf(PersistentDedupConformanceContract.class);
        for (String fqn : new String[] {
                "com.gallop.fastddd.agentaccess.arch.AgentAccessArchitecture",
                "com.gallop.fastddd.agentaccess.kit.fault.FaultInjection",
                "com.gallop.fastddd.agentaccess.kit.scan.SensitiveDataScanner",
                "com.gallop.fastddd.agentaccess.kit.evidence.EvidenceManifestValidator",
                "com.gallop.fastddd.agentaccess.kit.evidence.TestCoverageCatalog"}) {
            try {
                Class<?> tool = Class.forName(fqn);
                assertEquals(contractSource, codeSourceOf(tool),
                        fqn + " 必须与 conformance 基类同源发布（同一 kit main jar）");
            } catch (ClassNotFoundException e) {
                fail("kit main 缺少工具类: " + fqn);
            }
        }
    }

    private static URL codeSourceOf(Class<?> type) {
        // 测试运行时 codeSource 不会为 null；防御性 fail 而非 NPE
        java.security.ProtectionDomain domain = type.getProtectionDomain();
        assertNotNull(domain, type + " 缺少 ProtectionDomain");
        assertNotNull(domain.getCodeSource(), type + " 缺少 CodeSource");
        return domain.getCodeSource().getLocation();
    }
}

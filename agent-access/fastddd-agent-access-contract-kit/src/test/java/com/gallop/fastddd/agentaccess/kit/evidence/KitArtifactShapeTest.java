package com.gallop.fastddd.agentaccess.kit.evidence;

import com.gallop.fastddd.agentaccess.kit.conformance.PersistentDedupConformanceContract;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * FDD-AA-15/12.5 kit 制品化形态断言（AA-04 §7 MC-8）：
 * <ul>
 *   <li>kit 普通 jar 以 compile scope 传递 JUnit5——证据是 kit <b>main</b> 源码
 *       （{@code PersistentDedupConformanceContract}）本身标注 {@code @Test}：
 *       只有 junit-jupiter-api 在 compile 域，宿主 test scope 引 kit 后才能
 *       无需自配 JUnit 直接继承这些 conformance 基类；</li>
 *   <li>kit main 工具面（arch 规则/故障注入/扫描器/证据模型）在同一 jar 可用，
 *       PostgreSqlDedupConformanceTest 即 reactor 内消费证明。</li>
 * </ul>
 *
 * <p><b>边界登记（不冒充）</b>：clean consumer（空工程只依赖 kit 制品）的消费
 * 证据归 FDD-AA-16；本测试只锁定 kit 制品形态自洽。</p>
 */
class KitArtifactShapeTest {

    /** kit main 的 conformance 基类携带 @Test 注解 → JUnit api 在 kit compile 传递域 */
    @Test
    void conformanceContractCarriesJupiterTestAnnotations() {
        Method[] methods = PersistentDedupConformanceContract.class.getDeclaredMethods();
        assertTrue(methods.length > 0, "conformance 基类必须有可继承的测试方法");
        boolean anyTestAnnotated = Arrays.stream(methods)
                .anyMatch(m -> m.isAnnotationPresent(org.junit.jupiter.api.Test.class));
        assertTrue(anyTestAnnotated,
                "kit main 必须在 compile 域携带 JUnit5 注解（普通 jar 传递形态，非 test-jar）");
    }

    /** kit main 工具面在同一 jar 完整可用（arch/scan/fault/evidence 四族） */
    @Test
    void kitToolSurfaceIsOnTheSameJar() {
        for (String fqn : new String[] {
                "com.gallop.fastddd.agentaccess.arch.AgentAccessArchitecture",
                "com.gallop.fastddd.agentaccess.kit.fault.FaultInjection",
                "com.gallop.fastddd.agentaccess.kit.scan.SensitiveDataScanner",
                "com.gallop.fastddd.agentaccess.kit.evidence.EvidenceManifestValidator",
                "com.gallop.fastddd.agentaccess.kit.evidence.TestCoverageCatalog"}) {
            try {
                assertNotNull(Class.forName(fqn), fqn + " 必须随 kit main jar 发布");
            } catch (ClassNotFoundException e) {
                fail("kit main 缺少工具类: " + fqn);
            }
        }
    }
}

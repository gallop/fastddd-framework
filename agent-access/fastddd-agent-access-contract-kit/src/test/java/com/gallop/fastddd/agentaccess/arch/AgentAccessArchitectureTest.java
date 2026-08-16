package com.gallop.fastddd.agentaccess.arch;

import com.gallop.fastagent.FastAgentSideStub;
import com.gallop.fastddd.agentaccess.arch.violation.HostDependingOnGatewayImplementation;
import com.gallop.fastddd.agentaccess.arch.violation.ReferringToFastAgent;
import com.gallop.fastddd.agentaccess.contract.ContractDependingOnSpi;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AgentAccessArchitecture} B1/AST-06 规则测试：主制品真实字节码通过
 * 全部边界规则 + 各违例样例确实触发失败（规则不是摆设）。
 *
 * <p>违例样例位于 {@code arch.violation} 测试包（包名刻意落在被检包模式内，
 * 类只在单测导入时进入检查范围，不污染主制品）。</p>
 */
class AgentAccessArchitectureTest {

    /**
     * 主制品四包（.contract/.spi/.gateway/.permit）真实编译输出。
     *
     * <p>从主制品代码源（jar/classes 目录）精确导入，而非按包名扫描 classpath——
     * 本测试源码中的违例样例类刻意落在被检包名内（如 {@code .contract} 下的
     * {@link ContractDependingOnSpi}），按包扫描会把它们误计入主制品自检。</p>
     */
    private static JavaClasses mainArtifactClasses() {
        java.net.URL artifactLocation = com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor.class
                .getProtectionDomain().getCodeSource().getLocation();
        return new ClassFileImporter().importUrl(artifactLocation);
    }

    @Test
    void mainArtifactPassesAllBoundaryRules() {
        assertDoesNotThrow(() -> AgentAccessArchitecture.checkMainArtifact(mainArtifactClasses()));
    }

    @Test
    void contractDependingOnSpiIsCaught() {
        JavaClasses violating = new ClassFileImporter().importClasses(ContractDependingOnSpi.class);
        assertThatThrownBy(() -> AgentAccessArchitecture.contractIsSelfContained().check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Architecture Violation")
                .hasMessageContaining(ContractDependingOnSpi.class.getSimpleName());
    }

    @Test
    void hostDependingOnGatewayImplementationIsCaught() {
        JavaClasses violating =
                new ClassFileImporter().importClasses(HostDependingOnGatewayImplementation.class);
        // hostPackages 传违例 fixture 实际所在包：that() 必须真正选中类，
        // 否则 ArchUnit 抛的是「空选」通用失败而非 Architecture Violation（假绿）
        assertThatThrownBy(() -> AgentAccessArchitecture
                .hostMayOnlyUseGovernedSeams("com.gallop.fastddd.agentaccess.arch.violation..")
                .check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Architecture Violation")
                .hasMessageContaining(HostDependingOnGatewayImplementation.class.getSimpleName());
    }

    @Test
    void fastAgentDependencyIsCaught() {
        // 样例字节码引用 com.gallop.fastagent.FastAgentSideStub（test 源码自建 stub，
        // 不引入 Maven 依赖——Enforcer 管 jar 依赖树，本规则管字节码引用，两者互证）
        JavaClasses violating = new ClassFileImporter()
                .importClasses(ReferringToFastAgent.class, FastAgentSideStub.class);
        assertThatThrownBy(() -> AgentAccessArchitecture.noFastAgentDependency().check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Architecture Violation")
                .hasMessageContaining(ReferringToFastAgent.class.getSimpleName());
    }

    @Test
    void emptyHostPackagesIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentAccessArchitecture.hostMayOnlyUseGovernedSeams());
        assertThrows(IllegalArgumentException.class,
                () -> AgentAccessArchitecture.hostMayOnlyUseGovernedSeams((String[]) null));
    }
}

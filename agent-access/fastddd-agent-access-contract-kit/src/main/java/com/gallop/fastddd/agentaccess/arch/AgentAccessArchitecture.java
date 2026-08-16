package com.gallop.fastddd.agentaccess.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * agent-access 依赖边界 ArchUnit 规则集（FDD-AA-04 §3.2 包职责 / AA03 威胁
 * 矩阵 B1：构建期断言；FDD-AA-12 落点 4）。
 *
 * <p><b>物理落 kit 模块</b>（非主制品）：ArchUnit 依赖由 kit POM 显式管理，
 * 主制品 {@code fastddd-agent-access} 保持纯 JDK。规则对象可被宿主测试
 * 复用（与主制品同源，不各自手抄）。</p>
 *
 * <p>B1 是构建期断言，不是运行时沙箱：无 JPMS 下它不阻止同 JVM 恶意代码
 * 的反射绕过（B2 是编译期包边界 + 唯一入口装配断言，各司其职）。</p>
 *
 * <p>谓词语义：每条规则以「依赖目标 ∈ agent-access 根包 <b>减去</b> 被检包
 * 自身与其允许依赖的包」表达——不枚举兄弟包清单，因此根包直接类型与未来
 * 新增子包自动纳入检查（新增子包若要获得合法依赖位，必须显式修订本规则
 * 的豁免清单，不出现静默漏检）。</p>
 *
 * <p>规则清单（AA-04 §3.2 冻结方向）：</p>
 * <ul>
 *   <li>{@link #contractIsSelfContained()}：{@code .contract} 仅 JDK，不依赖
 *       agent-access 其他任何类型；</li>
 *   <li>{@link #spiDependsOnlyOnContract()}：{@code .spi} 只依赖
 *       {@code .contract}（+ JDK + 同包）；</li>
 *   <li>{@link #gatewayDependsOnlyOnSpiAndContract()}：{@code .gateway}
 *       只依赖 {@code .spi} + {@code .contract}（+ JDK + 同包）；</li>
 *   <li>{@link #permitDependsOnlyOnContractAndSpi()}：{@code .permit}
 *       只依赖 {@code .contract} + {@code .spi}（+ JDK + 同包）；</li>
 *   <li>{@link #noFastAgentDependency()}：主制品不依赖
 *       {@code com.gallop.fastagent..}（AST-06：L3 边界，与 Enforcer
 *       banned-dependencies 同源互证）；</li>
 *   <li>{@link #hostMayOnlyUseGovernedSeams(String...)}：宿主业务包只允许
 *       依赖 {@code .contract}/{@code .spi}（治理接缝），禁止依赖
 *       {@code .gateway}/{@code .permit} 实现包。</li>
 * </ul>
 */
public final class AgentAccessArchitecture {

    /** 主制品根包。 */
    public static final String AGENT_ACCESS_ROOT = "com.gallop.fastddd.agentaccess..";

    private static final String CONTRACT = "..agentaccess.contract..";
    private static final String SPI = "..agentaccess.spi..";
    private static final String GATEWAY = "..agentaccess.gateway..";
    private static final String PERMIT = "..agentaccess.permit..";

    private AgentAccessArchitecture() {
    }

    /** B1：.contract 不依赖 agent-access 其他任何类型（仅 JDK + 同包）。 */
    public static ArchRule contractIsSelfContained() {
        return noClasses().that().resideInAPackage(CONTRACT)
                .should().dependOnClassesThat(
                        resideInAPackage(AGENT_ACCESS_ROOT).and(not(resideInAnyPackage(CONTRACT))))
                .because("契约包仅 JDK：.contract 被 .spi/.gateway/.permit 单向依赖，不得反向");
    }

    /** B1：.spi 只依赖 .contract（+ JDK + 同包）。 */
    public static ArchRule spiDependsOnlyOnContract() {
        return noClasses().that().resideInAPackage(SPI)
                .should().dependOnClassesThat(
                        resideInAPackage(AGENT_ACCESS_ROOT).and(not(resideInAnyPackage(CONTRACT, SPI))))
                .because("接缝包只依赖契约：.spi 不得依赖实现包（.gateway/.permit）、kit/arch 或未来子包");
    }

    /** B1：.gateway 只依赖 .spi + .contract（+ JDK + 同包）。 */
    public static ArchRule gatewayDependsOnlyOnSpiAndContract() {
        return noClasses().that().resideInAPackage(GATEWAY)
                .should().dependOnClassesThat(
                        resideInAPackage(AGENT_ACCESS_ROOT).and(not(resideInAnyPackage(CONTRACT, SPI, GATEWAY))))
                .because("网关实现只编排接缝与契约：不得依赖 .permit 实现、kit/arch 或未来子包");
    }

    /** B1：.permit 只依赖 .contract + .spi（+ JDK + 同包）。 */
    public static ArchRule permitDependsOnlyOnContractAndSpi() {
        return noClasses().that().resideInAPackage(PERMIT)
                .should().dependOnClassesThat(
                        resideInAPackage(AGENT_ACCESS_ROOT).and(not(resideInAnyPackage(CONTRACT, SPI, PERMIT))))
                .because("许可实现只依赖契约与接缝：不得依赖 .gateway 实现、kit/arch 或未来子包");
    }

    /** AST-06：主制品不依赖 FastAgent 侧任何类型（L3 边界；与 Enforcer 互证）。 */
    public static ArchRule noFastAgentDependency() {
        return noClasses().that().resideInAPackage(AGENT_ACCESS_ROOT)
                .should().dependOnClassesThat()
                .resideInAPackage("com.gallop.fastagent..")
                .because("agent-access 主制品不依赖 FastAgent（ADR-007 L3 边界；Enforcer banned-dependencies 同源）");
    }

    /**
     * 宿主侧参数化规则：业务包只允许依赖治理接缝（{@code .contract}/{@code .spi}），
     * 禁止依赖 {@code .gateway}/{@code .permit} 实现包——实现类的唯一合法引用方
     * 是宿主显式 {@code @Configuration} 装配（装配处不在业务包内，由宿主自行
     * 划定装配包并纳入本规则豁免边界之外）。
     *
     * @param hostPackages 宿主业务包前缀（至少一个）
     * @return 逐包规则
     */
    public static ArchRule hostMayOnlyUseGovernedSeams(String... hostPackages) {
        if (hostPackages == null || hostPackages.length == 0) {
            throw new IllegalArgumentException("hostPackages 必选且不得为空");
        }
        return noClasses().that().resideInAnyPackage(hostPackages)
                .should().dependOnClassesThat()
                .resideInAnyPackage(GATEWAY, PERMIT)
                .because("业务代码只经 .spi 接缝交互：直接依赖 .gateway/.permit 实现即绕过受治理入口");
    }

    /**
     * AST-06 宿主参数化规则（TM-15）：宿主 Egress/Connector 包不得依赖
     * FastAgent 侧任何类型——结构上排除「FastDDD/Egress 直写 FastAgent
     * Receipt/Outcome 权威表」的路径（权威表只由 FastAgent Java Kernel 追加，
     * Contract §5.6 尾段；跨仓同向锁定见 FastAgent V04-04）。
     *
     * <p>依赖断言是结构防线；「Egress 调用账本只含 invoke/query 语义行」是
     * 运行时断言，归宿主测试（本规则不代签）。</p>
     *
     * @param egressPackages 宿主 Egress/Connector 包前缀（至少一个）
     * @return 逐包规则
     */
    public static ArchRule hostEgressMustNotTouchFastAgent(String... egressPackages) {
        if (egressPackages == null || egressPackages.length == 0) {
            throw new IllegalArgumentException("egressPackages 必选且不得为空");
        }
        return noClasses().that().resideInAnyPackage(egressPackages)
                .should().dependOnClassesThat()
                .resideInAPackage("com.gallop.fastagent..")
                .because("宿主 Egress 不得依赖 FastAgent 权威类型（TM-15：权威 Receipt/Outcome 只由"
                        + " FastAgent Kernel 追加；本仓规则与 FastAgent V04-04 同向锁定）");
    }

    /**
     * 主制品自身 B1/AST-06 全量自检（kit 测试与宿主 CI 复用）。
     *
     * @param classes 主制品编译输出（宿主可用 ClassFileImporter 按包导入）
     */
    public static void checkMainArtifact(JavaClasses classes) {
        contractIsSelfContained().check(classes);
        spiDependsOnlyOnContract().check(classes);
        gatewayDependsOnlyOnSpiAndContract().check(classes);
        permitDependsOnlyOnContractAndSpi().check(classes);
        noFastAgentDependency().check(classes);
    }
}

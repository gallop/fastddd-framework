package com.gallop.fastddd.common.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * DDD 分层架构规则 — 可复用的测试工具类。
 * <p>
 * 使用方式：在项目测试中继承或调用 {@link #allRules(String)} 获取全部规则，
 * 配合 {@link TestFactory} 动态生成测试。
 *
 * <pre>{@code
 * @TestFactory
 * Stream<DynamicTest> dddRules() {
 *     return DddLayerRules.allRules("com.example.shop")
 *             .stream()
 *             .map(r -> DynamicTest.dynamicTest(r.getName(), () -> r.check(importClasses("com.example.shop"))));
 * }
 * }</pre>
 *
 * @author gallop
 * @since 1.0.0
 */
public final class DddLayerRules {

    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String PERSISTENCE = "..infrastructure.persistence..";
    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INTERFACES = "..interfaces..";

    private DddLayerRules() {
    }

    /**
     * 返回所有 DDD 分层规则列表
     *
     * @param basePackage 项目基础包名（如 "com.example.shop"）
     * @return 不可变规则列表
     */
    public static List<ArchRule> allRules(String basePackage) {
        return List.of(
                domainShouldNotDependOnInfrastructure(),
                applicationShouldNotDependOnPersistence(),
                interfacesShouldNotDependOnPersistence(),
                interfacesShouldNotDependOnDomain(basePackage),
                noCyclicDependencies(basePackage)
        );
    }

    /** 领域层不应依赖基础设施层 */
    public static ArchRule domainShouldNotDependOnInfrastructure() {
        return noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat()
                .resideInAPackage(INFRASTRUCTURE)
                .because("Domain layer must not depend on infrastructure (PO, Mapper, etc.)");
    }

    /** 应用层不应直接依赖持久化层（应通过 Repository 接口） */
    public static ArchRule applicationShouldNotDependOnPersistence() {
        return noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat()
                .resideInAPackage(PERSISTENCE)
                .because("Application layer should depend on domain Repository interfaces, not persistence implementations");
    }

    /** 接口层不应直接依赖持久化层 */
    public static ArchRule interfacesShouldNotDependOnPersistence() {
        return noClasses()
                .that().resideInAPackage(INTERFACES)
                .should().dependOnClassesThat()
                .resideInAPackage(PERSISTENCE)
                .because("Interfaces layer should use Application Services or DTOs, not persistence directly");
    }

    /**
     * 接口层不应直接依赖领域层实体（应通过 DTO 和 Application Service）。
     * DTO 子包（interfaces.dto）豁免，因为 Response.from() 需要引用领域实体做转换。
     */
    public static ArchRule interfacesShouldNotDependOnDomain(String basePackage) {
        return noClasses()
                .that().resideInAPackage(INTERFACES)
                .and().resideOutsideOfPackage(basePackage + "..interfaces.dto..")
                .should().dependOnClassesThat()
                .resideInAPackage(DOMAIN)
                .because("Controllers should use DTOs from the dto sub-package, not domain entities directly");
    }

    /** 限界上下文之间不应存在循环依赖 */
    public static ArchRule noCyclicDependencies(String basePackage) {
        return slices().matching(basePackage + ".(*)..")
                .should().beFreeOfCycles()
                .because("Bounded contexts must not have cyclic dependencies");
    }

    /**
     * 导入项目类（辅助方法，供测试使用）
     */
    public static JavaClasses importClasses(String basePackage) {
        return new ClassFileImporter().importPackages(basePackage);
    }
}

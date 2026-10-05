package com.gallop.fastddd.security.audit;

import com.gallop.fastddd.web.response.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v1.0.1 判型守卫契约验证：@AuditLog 切面按返回值信封判定 success。
 *
 * <p>覆盖三组行为：
 * <ol>
 *   <li><b>失败信封记失败</b>：直返或 {@link ResponseEntity} 一层包裹的
 *       {@link ApiResponse} 且 code != 200（沿 {@code isSuccess()} 200 硬判定）时，
 *       审计记 {@code success=false} 且 {@code errorMessage} 承载信封 message；
 *       非信封/空 body 返回维持 {@code success=true}（1.0.0 语义）。</li>
 *   <li><b>回退开关</b>：{@code fastddd.security.audit.result-judging.enabled=false}
 *       （编程式 setter 与 Spring 环境注入双路）整体回退 1.0.0 恒 success=true 语义；
 *       键须 kebab-case 全字面量——@Value 无宽松绑定，缺省 true。</li>
 *   <li><b>守卫自防护</b>：信封子类覆写 {@code isFailure()} 抛出时兜底恒记
 *       success=true，被审计方法返回值原样透传（守卫绝不污染请求路径）。</li>
 * </ol>
 *
 * @author gallop
 * @since 1.0.1
 */
class AuditLogAspectResultJudgingTest {

    /** 失败信封消息样本（取自 studio-pro 实测失真场景的失败文案形态）。 */
    private static final String FAILURE_MESSAGE = "已发布/已执行的设计不可被 AI 覆盖";

    /**
     * 捕获型 {@link AuditLogService} 假件：三个落账口全捕获
     * （@AuditLog async 缺省 true → 实际走 {@link #saveAuditLogAsync}）。
     */
    static class CapturingAuditLogService implements AuditLogService {
        volatile AuditLogEntity captured;
        /** 区分落账口（sync/async/persist）——sink 选择回归时令断言可红。 */
        volatile String lastSink;

        @Override
        public void saveAuditLog(AuditLogEntity auditLogEntity) {
            captured = auditLogEntity;
            lastSink = "sync";
        }

        @Override
        public void saveAuditLogAsync(AuditLogEntity auditLogEntity) {
            captured = auditLogEntity;
            lastSink = "async";
        }

        @Override
        public void persist(AuditLogEntity auditLogEntity) {
            captured = auditLogEntity;
            lastSink = "persist";
        }
    }

    /** 判型探针载体：真 @AuditLog 注解（value 供 operation；logResult 缺省 false 不序列化）。 */
    @SuppressWarnings("unused")
    static class Fixture {
        @AuditLog("判型探针")
        public ApiResponse<?> probe() {
            return null;
        }
    }

    /** 信封子类：覆写 isFailure() 抛出，验证守卫自防护兜底。 */
    static class ThrowingEnvelope extends ApiResponse<Object> {
        ThrowingEnvelope() {
            super(400, "subclass-boom", null, 0L);
        }

        @Override
        public boolean isFailure() {
            throw new IllegalStateException("subclass override boom");
        }
    }

    /** 信封子类：覆写 isFailure() 抛 Error——验证 Throwable 级兜底（Error 非 Exception，窄 catch 会逃逸）。 */
    static class ThrowingErrorEnvelope extends ApiResponse<Object> {
        ThrowingErrorEnvelope() {
            super(400, "error-boom", null, 0L);
        }

        @Override
        public boolean isFailure() {
            throw new AssertionError("subclass override error boom");
        }
    }

    /**
     * 判定回退开关的装配配置：直接 new（不经自动配置），令 @Value setter 注入路径
     * 成为唯一变量——键绑定与缺省值断言不被其他条件装配稀释。
     */
    @Configuration
    static class RollbackSwitchConfig {
        @Bean
        CapturingAuditLogService auditLogService() {
            return new CapturingAuditLogService();
        }

        @Bean
        AuditLogAspect auditLogAspect(AuditLogService auditLogService) {
            return new AuditLogAspect(auditLogService);
        }
    }

    /** 以指定返回值驱动切面 around 全程（Mockito 桩 joinPoint+真注解 Method），返回捕获实体。 */
    private AuditLogEntity audit(Object proceedResult) throws Throwable {
        CapturingAuditLogService service = new CapturingAuditLogService();
        return audit(new AuditLogAspect(service), service, proceedResult);
    }

    private AuditLogEntity audit(AuditLogAspect aspect, CapturingAuditLogService service,
                                 Object proceedResult) throws Throwable {
        Method method = Fixture.class.getMethod("probe");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(proceedResult);

        Object returned = aspect.around(joinPoint, method.getAnnotation(AuditLog.class));

        assertThat(returned).isSameAs(proceedResult);
        assertThat(service.captured).as("审计实体应落账").isNotNull();
        assertThat(service.lastSink).as("@AuditLog async 缺省 true 须走 saveAuditLogAsync").isEqualTo("async");
        return service.captured;
    }

    @Test
    @DisplayName("直返失败信封记 success=false+errorMessage；非信封返回维持 true")
    void failureEnvelopeDirectReturnRecordsFailure() throws Throwable {
        AuditLogEntity failed = audit(ApiResponse.error(400, FAILURE_MESSAGE));
        assertThat(failed.getSuccess()).isFalse();
        assertThat(failed.getErrorMessage()).isEqualTo(FAILURE_MESSAGE);

        AuditLogEntity blankMessage = audit(ApiResponse.error(400, ""));
        assertThat(blankMessage.getSuccess()).isFalse();
        assertThat(blankMessage.getErrorMessage())
                .as("信封 message 空白时不覆写 errorMessage（hasText 门）").isNull();

        ApiResponse<Object> nullCode = new ApiResponse<>(null, "no-code", null, 0L);
        AuditLogEntity nullCodeEntity = audit(nullCode);
        assertThat(nullCodeEntity.getSuccess())
                .as("code=null 沿 200 硬判定计失败").isFalse();
        assertThat(nullCodeEntity.getErrorMessage()).isEqualTo("no-code");

        AuditLogEntity plain = audit("plain-string-not-envelope");
        assertThat(plain.getSuccess()).isTrue();
        assertThat(plain.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("ResponseEntity 一层包裹的信封参与判定；空 body/非信封 body 维持 true")
    void responseEntityWrappedEnvelopeJudged() throws Throwable {
        AuditLogEntity failed = audit(ResponseEntity.ok(ApiResponse.failure(400, FAILURE_MESSAGE)));
        assertThat(failed.getSuccess()).isFalse();
        assertThat(failed.getErrorMessage()).isEqualTo(FAILURE_MESSAGE);

        AuditLogEntity ok = audit(ResponseEntity.ok(ApiResponse.success("payload")));
        assertThat(ok.getSuccess()).isTrue();

        AuditLogEntity bodyless = audit(ResponseEntity.ok().build());
        assertThat(bodyless.getSuccess()).isTrue();

        AuditLogEntity nonEnvelopeBody = audit(ResponseEntity.ok("raw-body"));
        assertThat(nonEnvelopeBody.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("回退开关编程式关闭：失败信封仍记 success=true 且 errorMessage 不落")
    void rollbackSwitchRestoresLegacySemantics() throws Throwable {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditLogAspect aspect = new AuditLogAspect(service);
        aspect.setResultJudgingEnabled(false);

        AuditLogEntity entity = audit(aspect, service, ApiResponse.error(400, FAILURE_MESSAGE));
        assertThat(entity.getSuccess()).isTrue();
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("回退开关经 Spring 环境绑定：kebab-case 键注入 false 回退；缺省落 true 生效")
    void rollbackSwitchKeyBindsThroughSpringEnvironment() {
        new ApplicationContextRunner()
                .withUserConfiguration(RollbackSwitchConfig.class)
                .withPropertyValues("fastddd.security.audit.result-judging.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                    CapturingAuditLogService service = context.getBean(CapturingAuditLogService.class);
                    AuditLogEntity entity = audit(context.getBean(AuditLogAspect.class), service,
                            ApiResponse.error(400, FAILURE_MESSAGE));
                    assertThat(entity.getSuccess())
                            .as("enabled=false 须回退 1.0.0 恒 true 语义").isTrue();
                });

        new ApplicationContextRunner()
                .withUserConfiguration(RollbackSwitchConfig.class)
                .run(context -> {
                    CapturingAuditLogService service = context.getBean(CapturingAuditLogService.class);
                    AuditLogEntity entity = audit(context.getBean(AuditLogAspect.class), service,
                            ApiResponse.error(400, FAILURE_MESSAGE));
                    assertThat(entity.getSuccess())
                            .as("缺省（未配置）须落判型语义（表达式内嵌缺省 true）").isFalse();
                });
    }

    @Test
    @DisplayName("信封子类覆写 isFailure 抛出（Exception 与 Error 级）：守卫兜底恒 true 且返回值原样透传")
    void throwingEnvelopeSubclassFallsBackToLegacyTrue() throws Throwable {
        ThrowingEnvelope envelope = new ThrowingEnvelope();
        AuditLogEntity entity = audit(envelope);
        assertThat(entity.getSuccess()).isTrue();
        assertThat(entity.getErrorMessage()).isNull();

        ThrowingErrorEnvelope errorEnvelope = new ThrowingErrorEnvelope();
        AuditLogEntity errorEntity = audit(errorEnvelope);
        assertThat(errorEntity.getSuccess())
                .as("Error 级覆写抛出亦须兜底恒 true（Throwable 级 catch）").isTrue();
        assertThat(errorEntity.getErrorMessage()).isNull();
    }
}

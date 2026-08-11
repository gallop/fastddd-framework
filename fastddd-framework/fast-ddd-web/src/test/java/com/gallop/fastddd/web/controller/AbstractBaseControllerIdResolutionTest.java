package com.gallop.fastddd.web.controller;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ID-001 特征测试：锁定 {@link AbstractBaseController#resolveId(String)} 当前的 IdType 推测行为。
 *
 * <p>刻画现状（不改生产行为）：{@code resolveId} 依据 MyBatis-Plus {@link TableInfoHelper} 解析出的
 * {@link IdType} 决定 Java 类型——{@code ASSIGN_ID}/{@code AUTO} 视为 {@code Long}（数值解析），
 * 其余（{@code INPUT}/{@code ASSIGN_UUID}/{@code NONE}）一律按原始 {@code String} 返回。
 * 该逻辑把 JDBC 侧 {@code IdType} 与 Java 侧 ID 类型混为一谈（见 ADR-001），ID-005 将以
 * {@code IdCodec<ID>} 替代。本测试在无 Spring 环境下通过 {@link TableInfoHelper#initTableInfo}
 * 显式注册实体 TableInfo，精确刻画 IdType 分支。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class AbstractBaseControllerIdResolutionTest {

    /**
     * 继承 BasePO 的 ASSIGN_ID 实体（ID-003 后 BasePO.id 不再带字段级 @TableId，
     * IdType 由 MP 默认 ASSIGN_ID 提供——本 fixture 刻画"无字段注解"分支）。
     */
    @TableName("t_assign_id")
    static class AssignIdFixture extends BasePO<Long> {
    }

    /**
     * 重声明 id 为 INPUT 策略（刻画"子类字段级 @TableId 重声明"机制）。
     * 字段隐藏仅用于挂载 @TableId(INPUT)；类型与 BasePO<Long> 对齐，resolveId 只读 TableInfo 的 IdType。
     */
    @TableName("t_input")
    static class InputFixture extends BasePO<Long> {
        @TableId(type = IdType.INPUT)
        private Long id;

        @Override
        public Long getId() { return id; }

        @Override
        public void setId(Long id) { this.id = id; }
    }

    /** 重声明 id 为 AUTO 策略（字段类型对齐 BasePO<Long>）。 */
    @TableName("t_auto")
    static class AutoFixture extends BasePO<Long> {
        @TableId(type = IdType.AUTO)
        private Long id;

        @Override
        public Long getId() { return id; }

        @Override
        public void setId(Long id) { this.id = id; }
    }

    /** 无 TableInfo 注册的实体——用于刻画 fallback 分支。 */
    @TableName("t_unregistered")
    static class UnregisteredFixture extends BasePO<Long> {
    }

    /** 每个实体一个具体控制器子类：使 resolveEntityClass 经参数化超类解析到 args[1]=实体 Class。 */
    static class AssignIdController extends AbstractBaseController<IService<AssignIdFixture, Long>, AssignIdFixture> {
    }

    static class AutoController extends AbstractBaseController<IService<AutoFixture, Long>, AutoFixture> {
    }

    static class InputController extends AbstractBaseController<IService<InputFixture, Long>, InputFixture> {
    }

    static class UnregisteredController extends AbstractBaseController<IService<UnregisteredFixture, Long>, UnregisteredFixture> {
    }

    @BeforeAll
    static void registerTableInfo() {
        // 无需 Spring：用 MP 内部 API 显式注册 TableInfo，使 resolveId 走 IdType 分支而非 fallback
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, AssignIdFixture.class);
        TableInfoHelper.initTableInfo(assistant, InputFixture.class);
        TableInfoHelper.initTableInfo(assistant, AutoFixture.class);
        // UnregisteredFixture 故意不注册，用于刻画 fallback
    }

    @Nested
    @DisplayName("IdType 分支：ASSIGN_ID / AUTO → Long 解析")
    class LongIdResolution {

        @Test
        @DisplayName("ASSIGN_ID 实体：数字串解析为 Long")
        void assignId_numericParsesToLong() {
            AssignIdController c = new AssignIdController();
            Serializable resolved = c.resolveId("12345");
            assertThat(resolved).isEqualTo(12345L).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("AUTO 实体：数字串解析为 Long")
        void auto_numericParsesToLong() {
            AutoController c = new AutoController();
            assertThat(c.resolveId("67890")).isEqualTo(67890L);
        }

        @Test
        @DisplayName("ASSIGN_ID 实体：非数字串抛 IllegalArgumentException（非领域类型错误）")
        void assignId_nonNumericThrows() {
            AssignIdController c = new AssignIdController();
            // 刻画缺陷：非数值 ID 抛 IllegalArgumentException 而非领域错误；ID-005 将提供类型化解析
            assertThatThrownBy(() -> c.resolveId("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid numeric ID");
        }
    }

    @Nested
    @DisplayName("IdType 分支：INPUT / 其它 → 原始 String")
    class StringIdResolution {

        @Test
        @DisplayName("INPUT 实体：任意串原样返回 String")
        void input_returnsRawString() {
            InputController c = new InputController();
            Serializable resolved = c.resolveId("ORD-2026-0001");
            assertThat(resolved).isEqualTo("ORD-2026-0001").isInstanceOf(String.class);
        }

        @Test
        @DisplayName("INPUT 实体：即使是数字也返回 String（不解析）")
        void input_numericStillReturnsString() {
            InputController c = new InputController();
            assertThat(c.resolveId("12345")).isEqualTo("12345").isInstanceOf(String.class);
        }
    }

    @Nested
    @DisplayName("Fallback 分支：无 TableInfo 时尝试 Long，失败回退 String")
    class FallbackResolution {

        @Test
        @DisplayName("未注册实体：数字串回退解析为 Long")
        void fallback_numericParsesToLong() {
            UnregisteredController c = new UnregisteredController();
            assertThat(c.resolveId("999")).isEqualTo(999L);
        }

        @Test
        @DisplayName("未注册实体：非数字串回退为 String")
        void fallback_nonNumericReturnsString() {
            UnregisteredController c = new UnregisteredController();
            assertThat(c.resolveId("abc")).isEqualTo("abc").isInstanceOf(String.class);
        }
    }
}

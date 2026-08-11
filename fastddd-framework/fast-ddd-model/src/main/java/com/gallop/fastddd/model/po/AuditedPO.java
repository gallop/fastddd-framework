package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 带审计字段的最小持久化基类（ID-003）。
 *
 * <p>承载记录级审计字段（创建/更新时间、创建/更新人）与逻辑删除标记，<b>不</b>包含主键。
 * 主键契约由 {@link BasePO} 提供。将审计与身份分离，使"有审计但无统一主键契约"的持久化对象
 * （或未来采用非 Long 主键的对象）可仅复用审计能力。
 *
 * <p>这些字段是<b>持久层</b>关注点（由 MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充、
 * 由 {@code @TableLogic} 解析逻辑删除），不应出现在领域层（见 ID-002 已将审计字段从领域基类移除）。
 *
 * <p><b>逻辑删除策略</b>（ID-003 步骤2 评估）：{@code @TableLogic} 作为框架默认保留在此基类——
 * 绝大多数业务表需要软删除。若个别表不需要软删除，应由具体 PO 在表结构层面处理（不继承
 * {@code @TableLogic} 的列），而非在基类移除——基类提供默认、子类按需是本框架既定取向。
 *
 * @author FastDDD Framework Team
 * @since 1.0.0-jdk21
 */
@Getter
@Setter
@ToString
public abstract class AuditedPO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录创建时间。INSERT 时由 MetaObjectHandler 自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间。INSERT/UPDATE 时自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人。INSERT 时自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private String createUser;

    /**
     * 最后更新人。INSERT/UPDATE 时自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    /**
     * 逻辑删除标记。
     *
     * <p><b>Note:</b> 配合唯一索引时，已删除记录仍占用唯一槽位。解法：
     * <ul>
     *   <li>PostgreSQL: {@code CREATE UNIQUE INDEX idx_name ON t(name) WHERE deleted = 0}</li>
     *   <li>MySQL 8.0+: 虚拟列 + 条件唯一索引</li>
     * </ul>
     */
    @TableLogic
    private Integer deleted = 0;
}

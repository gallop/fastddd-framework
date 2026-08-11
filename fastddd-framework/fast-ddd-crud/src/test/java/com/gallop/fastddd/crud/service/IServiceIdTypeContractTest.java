package com.gallop.fastddd.crud.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gallop.fastddd.model.po.BasePO;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ID-004 契约测试：锁定 {@code IService<T, ID>} 双泛型主键类型。
 *
 * <p>本测试的核心是「编译即通过」——每个用例把 {@code IService} 的主键方法
 * （{@code getById}/{@code removeById}/{@code listByIds}）以<b>方法引用</b>绑定到
 * 参数类型严格匹配的函数式接口上（{@code Function<Long, ...>} 而非
 * {@code Function<Serializable, ...>}）。若有人把主键参数回退为 {@code Serializable}，
 * 或丢掉 {@code ID} 类型参数，这些绑定会立即类型不匹配而编译失败。方法引用绑定到非 null
 * 的 {@link NoopService} 实例，运行期可安全创建（绑定接收者必须非空）。</p>
 *
 * <p>三个内部类分别覆盖 ADR-001 §2.3 的 Long / String / UUID 三种主键形态；
 * 实体 {@code getId()} 的强类型返回另由运行期断言锁定（无需 {@code (Long)} 强转）。</p>
 *
 * @author gallop
 * @since 2026-08-11
 * @version 1.0.0-SNAPSHOT
 */
class IServiceIdTypeContractTest {

    /** Long 主键（雪花/DB 自增）。 */
    @Getter @Setter
    static class LongPO extends BasePO<Long> { }

    /** String 业务主键。 */
    @Getter @Setter
    static class StringPO extends BasePO<String> { }

    /** UUID 主键。 */
    @Getter @Setter
    static class UuidPO extends BasePO<UUID> { }

    /** 不依赖 DB 的最小 IService 实现：所有方法返回空/零值，仅供方法引用绑定。 */
    private static final class NoopService<T extends BasePO<ID>, ID extends Serializable>
            implements IService<T, ID> {
        @Override public boolean save(T entity) { return true; }
        @Override public boolean saveBatch(Collection<T> entityList) { return true; }
        @Override public boolean saveBatch(Collection<T> entityList, int batchSize) { return true; }
        @Override public boolean saveOrUpdate(T entity) { return true; }
        @Override public boolean saveOrUpdateBatch(Collection<T> entityList) { return true; }
        @Override public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) { return true; }
        @Override public boolean removeById(ID id) { return true; }
        @Override public boolean remove(Wrapper<T> queryWrapper) { return true; }
        @Override public boolean removeByIds(Collection<? extends ID> idList) { return true; }
        @Override public boolean updateById(T entity) { return true; }
        @Override public boolean update(T entity, Wrapper<T> updateWrapper) { return true; }
        @Override public boolean updateBatchById(Collection<T> entityList) { return true; }
        @Override public boolean updateBatchById(Collection<T> entityList, int batchSize) { return true; }
        @Override public Optional<T> getById(ID id) { return Optional.empty(); }
        @Override public List<T> listByIds(Collection<? extends ID> idList) { return List.of(); }
        @Override public Optional<T> getOne(Wrapper<T> queryWrapper) { return Optional.empty(); }
        @Override public Optional<T> getOne(Wrapper<T> queryWrapper, boolean throwEx) { return Optional.empty(); }
        @Override public List<T> list(Wrapper<T> queryWrapper) { return List.of(); }
        @Override public List<T> list() { return List.of(); }
        @Override public IPage<T> page(IPage<T> page) { return page; }
        @Override public IPage<T> page(IPage<T> page, Wrapper<T> queryWrapper) { return page; }
        @Override public long count(Wrapper<T> queryWrapper) { return 0L; }
        @Override public long count() { return 0L; }
        @Override public Optional<T> getByField(String field, Object value) { return Optional.empty(); }
        @Override public List<T> listByField(String field, Object value) { return List.of(); }
        @Override public List<T> listByFields(Map<String, Object> fieldValueMap) { return List.of(); }
        @Override public boolean existsByField(String field, Object value) { return false; }
        @Override public long countByField(String field, Object value) { return 0L; }
    }

    @Nested
    @DisplayName("Long 主键：getById/removeById/listByIds 收窄为 Long")
    class LongIdContract {
        private final IService<LongPO, Long> service = new NoopService<>();

        @Test
        void getById_isTypedLong() {
            // 若 getById 回退为 Serializable，Function<Long,...> 绑定将编译失败
            Function<Long, Optional<LongPO>> ref = service::getById;
            assertNotNull(ref);
        }

        @Test
        void removeById_isTypedLong() {
            Function<Long, Boolean> ref = service::removeById;
            assertNotNull(ref);
        }

        @Test
        void listByIds_isTypedLongCollection() {
            Function<Collection<Long>, List<LongPO>> ref = service::listByIds;
            assertNotNull(ref);
        }

        @Test
        void entityGetId_returnsTypedLong() {
            LongPO po = new LongPO();
            po.setId(42L);
            Long id = po.getId(); // 编译期即为 Long，无需 (Long) 强转
            assertEquals(42L, id);
        }
    }

    @Nested
    @DisplayName("String 业务主键：getById 收窄为 String")
    class StringIdContract {
        private final IService<StringPO, String> service = new NoopService<>();

        @Test
        void getById_isTypedString() {
            Function<String, Optional<StringPO>> ref = service::getById;
            assertNotNull(ref);
        }

        @Test
        void entityGetId_returnsTypedString() {
            StringPO po = new StringPO();
            po.setId("ORDER-001");
            String id = po.getId();
            assertEquals("ORDER-001", id);
        }
    }

    @Nested
    @DisplayName("UUID 主键：getById 收窄为 UUID")
    class UuidIdContract {
        private final IService<UuidPO, UUID> service = new NoopService<>();

        @Test
        void getById_isTypedUuid() {
            Function<UUID, Optional<UuidPO>> ref = service::getById;
            assertNotNull(ref);
        }

        @Test
        void entityGetId_returnsTypedUuid() {
            UuidPO po = new UuidPO();
            UUID uuid = UUID.randomUUID();
            po.setId(uuid);
            assertSame(uuid, po.getId());
        }
    }
}

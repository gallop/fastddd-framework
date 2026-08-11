package com.gallop.fastddd.monitor.sql;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SQL 监控 JDBC 代理工厂（MON-002）。
 *
 * <p>用 JDK {@link Proxy} 动态代理包装 {@link DataSource} → {@link Connection} → {@link Statement}，
 * 在 {@code Statement} 的执行类方法上计时，并回调 {@link SqlMonitoringExecutionRecorder}
 * 记录 Micrometer 指标与脱敏结构化日志。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅代理执行类方法（{@code execute}/{@code executeQuery}/{@code executeUpdate}/
 *       {@code executeBatch} 等），其余方法透明委托（{@code method.invoke(target, args)}），
 *       不影响 JDBC 语义。</li>
 *   <li>{@link Connection#createStatement}/{@code prepareStatement}/{@code prepareCall}
 *       的返回值被包装为 {@link Statement} 代理；PreparedStatement 的 SQL 在 prepare 时捕获。</li>
 *   <li>包装产物实现 {@link SqlMonitoringProxied}，供后置处理器避免重复包装。</li>
 *   <li>不引入任何第三方依赖——仅依赖 JDK 反射 API，符合"Spring 管理的 DataSource Proxy"方案。</li>
 * </ul>
 *
 * <p>已知局限：不代理 {@link java.sql.ResultSet}（结果集大小指标 {@code resultSetSize} 暂不采集）；
 * 批处理以整体计时（不拆分单条）；多 DataSource 消费者由装配条件
 * {@code @ConditionalOnSingleCandidate(DataSource.class)} 限定为单候选场景。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Slf4j
public final class SqlMonitoringJdbcProxy {

    /** {@link Statement} 上需要计时的执行类方法名。 */
    private static final Set<String> TIMED_METHODS = new HashSet<>(Arrays.asList(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
            "executeBatch", "executeLargeBatch"));

    /** {@link Connection} 上返回 Statement 的工厂方法名。 */
    private static final Set<String> STATEMENT_FACTORY_METHODS = new HashSet<>(Arrays.asList(
            "createStatement", "prepareStatement", "prepareCall"));

    private SqlMonitoringJdbcProxy() {
    }

    /**
     * 包装一个 {@link DataSource}，使其返回的 Connection/Statement 受 SQL 监控。
     *
     * @param original 原始 DataSource（典型为 Spring Boot 自动配置的 HIKARI/DBCP 等）
     * @param recorder 执行记录器（Spring bean，已完成依赖注入）
     * @return 实现了 {@link DataSource} + {@link SqlMonitoringProxied} 的 JDK 代理
     */
    public static DataSource wrapDataSource(DataSource original,
                                            SqlMonitoringExecutionRecorder recorder) {
        DataSourceInvocationHandler handler = new DataSourceInvocationHandler(original, recorder);
        return (DataSource) Proxy.newProxyInstance(
                SqlMonitoringProxied.class.getClassLoader(),
                proxyInterfaces(original),
                handler);
    }

    /**
     * DataSource 代理：拦截 {@code getConnection*}，将返回的 Connection 再包装。
     */
    private static final class DataSourceInvocationHandler implements InvocationHandler {

        private final DataSource target;
        private final SqlMonitoringExecutionRecorder recorder;

        DataSourceInvocationHandler(DataSource target, SqlMonitoringExecutionRecorder recorder) {
            this.target = target;
            this.recorder = recorder;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(target, args);
            if (result instanceof Connection && !(result instanceof SqlMonitoringProxied)) {
                return wrapConnection((Connection) result, recorder);
            }
            return result;
        }
    }

    /**
     * 包装 Connection：拦截 createStatement/prepareStatement/prepareCall。
     */
    private static Connection wrapConnection(Connection target, SqlMonitoringExecutionRecorder recorder) {
        ConnectionInvocationHandler handler = new ConnectionInvocationHandler(target, recorder);
        return (Connection) Proxy.newProxyInstance(
                SqlMonitoringProxied.class.getClassLoader(),
                proxyInterfaces(target),
                handler);
    }

    private static final class ConnectionInvocationHandler implements InvocationHandler {

        private final Connection target;
        private final SqlMonitoringExecutionRecorder recorder;
        private volatile String connectionUrl;

        ConnectionInvocationHandler(Connection target, SqlMonitoringExecutionRecorder recorder) {
            this.target = target;
            this.recorder = recorder;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(target, args);
            if (STATEMENT_FACTORY_METHODS.contains(method.getName())
                    && result instanceof Statement
                    && !(result instanceof SqlMonitoringProxied)) {
                String capturedSql = extractFirstStringArg(args);
                return wrapStatement((Statement) result, recorder, this::resolveConnectionUrl, capturedSql);
            }
            return result;
        }

        private String resolveConnectionUrl() {
            if (connectionUrl == null) {
                try {
                    connectionUrl = target.getMetaData().getURL();
                } catch (Exception e) {
                    connectionUrl = "unknown";
                }
            }
            return connectionUrl;
        }
    }

    /**
     * 包装 Statement：拦截执行类方法并计时。
     *
     * @param capturedSql PreparedStatement 在 prepare 时捕获的 SQL；为 null 表示 plain Statement（运行时从 args 取）
     */
    private static Statement wrapStatement(Statement target, SqlMonitoringExecutionRecorder recorder,
                                           java.util.function.Supplier<String> connectionUrlSupplier,
                                           String capturedSql) {
        StatementInvocationHandler handler = new StatementInvocationHandler(
                target, recorder, connectionUrlSupplier, capturedSql);
        return (Statement) Proxy.newProxyInstance(
                SqlMonitoringProxied.class.getClassLoader(),
                proxyInterfaces(target),
                handler);
    }

    /**
     * 收集目标对象实现的所有接口，并追加 {@link SqlMonitoringProxied} 标记。
     *
     * <p>动态收集接口是为了让代理能被安全地强转为目标的子类型（如
     * {@code PreparedStatement}/{@code CallableStatement}）——这是 HikariCP / H2 等
     * 驱动返回具体 Statement 子类型时消费者代码（{@code (PreparedStatement) conn.prepareStatement(...)}）
     * 不抛 {@link ClassCastException} 的前提。
     */
    private static Class<?>[] proxyInterfaces(Object target) {
        java.util.Set<Class<?>> ifaces = new java.util.LinkedHashSet<>();
        collectInterfaces(target.getClass(), ifaces);
        // 确保核心 JDBC 接口在场（target 可能是动态子类，getInterfaces 链未必直接含 Statement/Connection）
        Class<?>[] result = new Class<?>[ifaces.size() + 1];
        int i = 0;
        for (Class<?> iface : ifaces) {
            result[i++] = iface;
        }
        result[i] = SqlMonitoringProxied.class;
        return result;
    }

    private static void collectInterfaces(Class<?> type, java.util.Set<Class<?>> out) {
        while (type != null && type != Object.class) {
            for (Class<?> iface : type.getInterfaces()) {
                if (out.add(iface)) {
                    collectInterfaces(iface, out);
                }
            }
            type = type.getSuperclass();
        }
    }

    private static final class StatementInvocationHandler implements InvocationHandler {

        private final Statement target;
        private final SqlMonitoringExecutionRecorder recorder;
        private final java.util.function.Supplier<String> connectionUrlSupplier;
        private String capturedSql;

        StatementInvocationHandler(Statement target, SqlMonitoringExecutionRecorder recorder,
                                   java.util.function.Supplier<String> connectionUrlSupplier,
                                   String capturedSql) {
            this.target = target;
            this.recorder = recorder;
            this.connectionUrlSupplier = connectionUrlSupplier;
            this.capturedSql = capturedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            // addBatch(String sql)：更新捕获的 SQL（plain Statement 批处理）
            if ("addBatch".equals(name) && args != null && args.length > 0 && args[0] instanceof String) {
                this.capturedSql = (String) args[0];
                return method.invoke(target, args);
            }

            if (!TIMED_METHODS.contains(name)) {
                return method.invoke(target, args);
            }

            String effectiveSql = resolveSql(name, args);
            long start = System.nanoTime();
            try {
                Object result = method.invoke(target, args);
                long elapsedMs = Math.max(0, (System.nanoTime() - start) / 1_000_000L);
                Integer affectedRows = (result instanceof Integer) ? (Integer) result : null;
                recorder.record(effectiveSql, elapsedMs, connectionUrlSupplier.get(),
                        true, null, affectedRows);
                return result;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                long elapsedMs = Math.max(0, (System.nanoTime() - start) / 1_000_000L);
                Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                recorder.record(effectiveSql, elapsedMs, connectionUrlSupplier.get(),
                        false, safeMessage(cause), null);
                throw cause;
            }
        }

        private String resolveSql(String methodName, Object[] args) {
            // execute(String)/executeQuery(String)/executeUpdate(String, ...) 等带 SQL 重载
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
            if (capturedSql != null) {
                return capturedSql;
            }
            return methodName;
        }

        private static String safeMessage(Throwable t) {
            String msg = t.getMessage();
            return msg != null ? msg : t.getClass().getSimpleName();
        }
    }

    private static String extractFirstStringArg(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            return (String) args[0];
        }
        return null;
    }
}

package com.gallop.fastddd.agentaccess.spi;

/**
 * 调用方身份未通过验证时的拒绝异常（安全事件信号）。
 *
 * <p>语义（AST-04 负向基线）：</p>
 * <ul>
 *   <li>表示<b>拒绝</b>，不是可重试的瞬态错误；消费方不得捕获本异常后以匿名
 *       {@code system} principal 兜底重试（Contract §5.1 不变量 3）；</li>
 *   <li>未认证上下文直调受治理入口必须抛出本类型，不得静默放行；</li>
 *   <li>消息只携带归因线索（路径、缺失的验证事实），不得携带完整 payload、
 *       prompt 或 secret（DataHandlingPolicy 默认 DROP/MASK）。</li>
 * </ul>
 */
public class CallerAuthenticationException extends RuntimeException {

    public CallerAuthenticationException(String message) {
        super(message);
    }

    public CallerAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

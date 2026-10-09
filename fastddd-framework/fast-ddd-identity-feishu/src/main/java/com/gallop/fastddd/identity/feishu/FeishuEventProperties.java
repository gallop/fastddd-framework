package com.gallop.fastddd.identity.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fastddd.identity.feishu.events.*} 飞书事件订阅配置（W5 设计 §5.1，W5-d）。
 *
 * <p>三键：{@code encrypt-key}（Encrypt Key——签名计算与 AES 解密同源）、
 * {@code verification-token}（Verification Token——明文事件鉴别）、
 * {@code replay-window}（防重放窗长，本地策略）。两密钥缺省空串 = 宿主未配置：
 * 接收链运行期 fail-closed（对应形态 401 拒收并指明配置缺失，不留"能收到
 * 但全拒"的模糊形态）——不订阅事件的宿主可双空（通道 Bean 存在但无消费方）。
 * 密钥值不打印不入日志（本类为纯配置载体）。</p>
 *
 * <p><b>防重放窗缺省 12h 的依据</b>：飞书平台对未确认事件的重试阶梯为
 * 15s/5min/1h/6h（至多 4 次），重试携带<b>原始</b> {@code header.create_time}
 * ——窗长必须覆盖最长重试时点（6h）+ 余量，否则合法重试会被误判重放拒收
 * （事件永不到达）。已入箱事件的重复投递由 event_id 唯一键幂等去重，窗长
 * 职责仅为"从未入箱事件"的防重放——宽窗无害，宿主可按自身策略收紧。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "fastddd.identity.feishu.events")
public class FeishuEventProperties {

    /** 事件加密密钥（Encrypt Key；空 = 未配置，加密形态 fail-closed 拒收）。 */
    private String encryptKey = "";

    /** 事件订阅校验 token（Verification Token；空 = 未配置，明文形态 fail-closed 拒收）。 */
    private String verificationToken = "";

    /** 防重放窗长（|now - header.create_time| 超窗即拒收；缺省覆盖平台最长重试 6h + 余量）。 */
    private Duration replayWindow = Duration.ofHours(12);

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public Duration getReplayWindow() {
        return replayWindow;
    }

    public void setReplayWindow(Duration replayWindow) {
        this.replayWindow = replayWindow;
    }
}

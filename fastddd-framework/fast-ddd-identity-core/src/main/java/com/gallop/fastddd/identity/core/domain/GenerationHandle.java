package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 代持有句柄（W3 设计 §6.1，R3）：显式携带 {@code scope + generationId + leaseToken}，
 * 一切对构建代/发布元的写操作必须凭本句柄通过联合校验
 * （token 匹配 ∧ 租约未过期 ∧ 代状态前置）——句柄不是"曾获得过租约"的证明，
 * 而是每次操作的授权能力对象。
 *
 * <p>被接管（takeover）后旧句柄的全部写操作被拒（{@code DirectoryLeaseLost}），
 * 保护新持有者；token 仅在所有权变更时轮换，续租不变。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class GenerationHandle {

    private final DirectoryScope scope;
    private final String generationId;
    private final String leaseToken;

    public GenerationHandle(DirectoryScope scope, String generationId, String leaseToken) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.leaseToken = Objects.requireNonNull(leaseToken, "leaseToken");
    }

    public DirectoryScope scope() {
        return scope;
    }

    public String generationId() {
        return generationId;
    }

    public String leaseToken() {
        return leaseToken;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenerationHandle
                && scope.equals(((GenerationHandle) o).scope)
                && generationId.equals(((GenerationHandle) o).generationId)
                && leaseToken.equals(((GenerationHandle) o).leaseToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, generationId, leaseToken);
    }

    @Override
    public String toString() {
        return "GenerationHandle[" + scope + "/" + generationId + "]";
    }
}

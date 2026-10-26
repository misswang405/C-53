package com.c53.modules.auth.service;

import java.time.Instant;

/** Redis 家族撤销失败后的持久化补偿。 */
public interface AuthCleanupService {

    /**
     * 在调用者已有事务中登记一个待执行的家族清理任务。
     *
     * <p>通过 Spring 注入的代理调用；没有活动事务会立即报错。业务家族撤销与任务写入
     * 必须同提交/同回滚，调用者不得捕获写入异常后继续提交业务事务。</p>
     *
     * @throws RuntimeException 无事务或任务写入失败时抛出，禁止吞掉以免业务事务无任务提交
     */
    void enqueue(String familyId, Instant expiresAt, String reason);

    /** 扫描并重试到期的待处理任务；单条失败不会阻断同批任务。 */
    void retryPending();
}

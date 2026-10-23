package ai.interviewer.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把副作用推迟到事务提交之后执行。
 *
 * <p>缓存写入和消息投递都属于「结果还没定」的副作用：事务回滚时数据库没有这条记录，
 * 而 Redis 或 Stream 里已经留了一条读得到、改不了的脏数据。这里统一挂到
 * afterCommit 上，没有活动事务时就直接执行。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

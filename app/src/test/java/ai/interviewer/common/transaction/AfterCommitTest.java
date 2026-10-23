package ai.interviewer.common.transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("提交后执行")
class AfterCommitTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("没有事务上下文时立刻执行")
    void runsImmediatelyWithoutTransaction() {
        AtomicBoolean executed = new AtomicBoolean();

        AfterCommit.run(() -> executed.set(true));

        assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("有事务上下文时等到提交之后才执行")
    void defersUntilCommit() {
        AtomicBoolean executed = new AtomicBoolean();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> executed.set(true));

        assertThat(executed).isFalse();
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();
        assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("事务回滚时什么都不执行")
    void doesNothingWhenTransactionRollsBack() {
        AtomicBoolean executed = new AtomicBoolean();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> executed.set(true));
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(executed).isFalse();
    }
}

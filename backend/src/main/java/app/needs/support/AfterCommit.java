package app.needs.support;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.logging.Logger;

/**
 * Runs work only once the current transaction has committed, so background jobs and live events
 * never see (or announce) data that was rolled back. Slow work (LLM calls) runs on virtual threads.
 */
@ApplicationScoped
public class AfterCommit {

    private static final Logger LOG = Logger.getLogger(AfterCommit.class);

    @Inject
    TransactionSynchronizationRegistry registry;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** After commit, on a background thread. */
    public void async(Runnable task) {
        register(() -> submit(task));
    }

    /** After commit, on the committing thread. For cheap work such as publishing live events. */
    public void sync(Runnable task) {
        register(task);
    }

    /** Background work outside any transaction. */
    public void submit(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Background task failed", e);
            }
        });
    }

    private void register(Runnable task) {
        registry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    task.run();
                }
            }
        });
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}

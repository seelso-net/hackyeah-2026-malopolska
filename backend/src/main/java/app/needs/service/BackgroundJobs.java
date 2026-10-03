package app.needs.service;

import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.MatchSuggestion;
import app.needs.model.Outcome;
import app.needs.support.AfterCommit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Work nobody clicks for: on startup, compute missing vectors (the seed has none) and prepare suggestions
 * for triaged cases; every hour, close resolved cases that residents never confirmed.
 */
@ApplicationScoped
public class BackgroundJobs {

    private static final Logger LOG = Logger.getLogger(BackgroundJobs.class);

    @Inject
    EmbeddingService embeddings;

    @Inject
    MatchingService matching;

    @Inject
    CaseWorkflow workflow;

    @Inject
    AfterCommit afterCommit;

    @Inject
    EntityManager em;

    private volatile boolean ready;

    /** True once startup vectors and suggestions are in place; /q/health and the demo script wait for it. */
    public boolean ready() {
        return ready;
    }

    void onStart(@Observes StartupEvent event) {
        afterCommit.submit(this::prepare);
    }

    void prepare() {
        try {
            long started = System.currentTimeMillis();
            embeddings.resetIfModelChanged();
            int vectors = embeddings.fillMissing();
            List<UUID> triaged = QuarkusTransaction.requiringNew().call(() -> em
                    .createQuery("select c.id from CaseFile c where c.status = :s order by c.number", UUID.class)
                    .setParameter("s", CaseStatus.TRIAGED)
                    .getResultList());
            int suggestions = 0;
            for (UUID id : triaged) {
                boolean pending = QuarkusTransaction.requiringNew().call(() -> MatchSuggestion.pendingFor(id).isPresent());
                if (!pending) {
                    matching.suggest(id);
                    suggestions++;
                }
            }
            LOG.infof("Startup AI work done in %d ms: %d vectors computed, %d suggestions prepared",
                    System.currentTimeMillis() - started, vectors, suggestions);
        } catch (RuntimeException e) {
            LOG.error("Startup AI work failed; check the AI settings (app.ai.mode, OPENAI_*)", e);
        } finally {
            ready = true;
        }
    }

    @Scheduled(every = "1h", delayed = "1m")
    void autoClose() {
        List<UUID> stale = QuarkusTransaction.requiringNew().call(() -> {
            List<CaseFile> resolved = CaseFile.list("status = ?1 and resolvedAt is not null", CaseStatus.RESOLVED);
            Instant now = Instant.now();
            return resolved.stream()
                    .filter(c -> c.resolvedAt.plus(Duration.ofDays(c.community.cfg().rules().autoClose())).isBefore(now))
                    .map(c -> c.id)
                    .toList();
        });
        for (UUID id : stale) {
            QuarkusTransaction.requiringNew().run(() -> {
                CaseFile c = CaseFile.findById(id);
                if (c.status != CaseStatus.RESOLVED) {
                    return;
                }
                CaseParticipant.<CaseParticipant>list("caseFile.id = ?1 and outcome is null", id)
                        .forEach(p -> p.outcome = Outcome.UNCONFIRMED);
                workflow.changeStatus(c, CaseStatus.CLOSED, null, "AUTO_CLOSED");
            });
        }
        if (!stale.isEmpty()) {
            LOG.infof("Auto-closed %d resolved cases nobody confirmed", stale.size());
        }
    }
}

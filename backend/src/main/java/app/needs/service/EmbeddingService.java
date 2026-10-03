package app.needs.service;

import app.needs.ai.Ai;
import app.needs.model.Actor;
import app.needs.model.AppSetting;
import app.needs.model.CaseFile;
import app.needs.model.LocalizedText;
import app.needs.model.Playbook;
import app.needs.model.PlaybookStep;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jboss.logging.Logger;

/**
 * Keeps vectors in step with the embedding model. Texts are read in short transactions and
 * embedded outside them, so slow model calls never hold database locks.
 */
@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

    private static final int REPORTS_PER_CASE = 10;

    @Inject
    Ai ai;

    @Inject
    EntityManager em;

    /** Vectors from different models cannot be compared, so a model change clears them all. */
    public void resetIfModelChanged() {
        String current = ai.embedderId();
        QuarkusTransaction.requiringNew().run(() -> {
            AppSetting setting = AppSetting.findById("embedder");
            if (setting != null && setting.value.equals(current)) {
                return;
            }
            if (setting != null) {
                LOG.infof("Embedding model changed (%s -> %s); recomputing every vector", setting.value, current);
                for (String table : List.of("case_file", "report", "actor", "playbook")) {
                    em.createNativeQuery("UPDATE " + table + " SET embedding = NULL").executeUpdate();
                }
                setting.value = current;
            } else {
                setting = new AppSetting();
                setting.key = "embedder";
                setting.value = current;
                setting.persist();
            }
        });
    }

    /** Computes every missing vector for cases, doers and playbooks. Returns how many were filled. */
    public int fillMissing() {
        int n = fill("select c.id from CaseFile c where c.embedding is null", this::refreshCase);
        n += fill("select a.id from Actor a where a.embedding is null", this::refreshActor);
        n += fill("select p.id from Playbook p where p.embedding is null", this::refreshPlaybook);
        return n;
    }

    private int fill(String query, Function<UUID, Boolean> refresh) {
        List<UUID> ids = QuarkusTransaction.requiringNew().call(() -> em.createQuery(query, UUID.class).getResultList());
        ids.forEach(refresh::apply);
        return ids.size();
    }

    /** A case means its title and summary plus what residents actually wrote (the latest reports). */
    public boolean refreshCase(UUID id) {
        String text = QuarkusTransaction.requiringNew().call(() -> {
            CaseFile c = CaseFile.findById(id);
            StringBuilder sb = new StringBuilder(c.title.joined()).append('\n');
            if (c.summary != null) {
                sb.append(c.summary.joined()).append('\n');
            }
            sb.append(c.categoryCode).append('\n');
            em.createQuery("select r.body from Report r where r.caseFile.id = :id order by r.createdAt desc", LocalizedText.class)
                    .setParameter("id", id)
                    .setMaxResults(REPORTS_PER_CASE)
                    .getResultList()
                    .forEach(body -> sb.append(body.original()).append('\n'));
            return sb.toString();
        });
        float[] v = ai.embed(text);
        QuarkusTransaction.requiringNew().run(() -> CaseFile.<CaseFile>findById(id).embedding = v);
        return true;
    }

    public boolean refreshActor(UUID id) {
        String text = QuarkusTransaction.requiringNew().call(() -> {
            Actor a = Actor.findById(id);
            return a.name + "\n" + (a.description == null ? "" : a.description.joined()) + "\n" + String.join(" ", a.capabilities);
        });
        float[] v = ai.embed(text);
        QuarkusTransaction.requiringNew().run(() -> Actor.<Actor>findById(id).embedding = v);
        return true;
    }

    public boolean refreshPlaybook(UUID id) {
        String text = QuarkusTransaction.requiringNew().call(() -> {
            Playbook p = Playbook.findById(id);
            StringBuilder sb = new StringBuilder(p.title.joined()).append('\n');
            if (p.problem != null) {
                sb.append(p.problem.joined()).append('\n');
            }
            sb.append(String.join(" ", p.categoryCodes)).append('\n');
            if (p.currentVersion != null) {
                for (PlaybookStep s : p.currentVersion.steps) {
                    sb.append(s.title().joined()).append('\n');
                }
            }
            return sb.toString();
        });
        float[] v = ai.embed(text);
        QuarkusTransaction.requiringNew().run(() -> Playbook.<Playbook>findById(id).embedding = v);
        return true;
    }
}

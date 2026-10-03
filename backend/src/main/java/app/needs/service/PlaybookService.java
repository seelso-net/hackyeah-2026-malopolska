package app.needs.service;

import app.needs.ai.Ai;
import app.needs.ai.AiTypes.DraftInput;
import app.needs.ai.AiTypes.DraftResult;
import app.needs.ai.AiTypes.UpdateNote;
import app.needs.ai.TextTools;
import app.needs.model.AppUser;
import app.needs.model.CaseEvent;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.DraftedBy;
import app.needs.model.Outcome;
import app.needs.model.Playbook;
import app.needs.model.PlaybookStatus;
import app.needs.model.PlaybookStep;
import app.needs.model.PlaybookVersion;
import app.needs.model.Visibility;
import app.needs.support.AfterCommit;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import app.needs.support.Problems;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * The knowledge loop: a confirmed case closes, the AI drafts the next playbook version from what the
 * doers actually did, and a moderator publishes it so the next community starts from a better recipe.
 */
@ApplicationScoped
public class PlaybookService {

    private static final Logger LOG = Logger.getLogger(PlaybookService.class);

    @Inject
    Ai ai;

    @Inject
    CaseWorkflow workflow;

    @Inject
    EmbeddingService embeddings;

    @Inject
    AfterCommit afterCommit;

    @Inject
    LiveEvents live;

    /** What the draft is based on: the playbook the case used (if any) and its latest steps. */
    private record DraftBasis(UUID playbookId, UUID baseVersionId, DraftInput input) {
    }

    /** CONFIRMED -> CLOSED, then the AI drafts the next playbook version (or a first one) from the case. */
    public void closeAndDraft(UUID caseId) {
        DraftBasis basis = QuarkusTransaction.requiringNew().call(() -> {
            CaseFile c = CaseFile.findById(caseId);
            if (c == null || c.status != CaseStatus.CONFIRMED) {
                return null;
            }
            workflow.changeStatus(c, CaseStatus.CLOSED, null, "CONFIRMED");
            return basis(c);
        });
        if (basis == null) {
            return;
        }
        DraftResult result = ai.draft(basis.input());
        QuarkusTransaction.requiringNew().run(() -> saveDraft(caseId, basis, result));
    }

    private DraftBasis basis(CaseFile c) {
        Playbook playbook = c.playbookVersion == null ? null : c.playbookVersion.playbook;
        PlaybookVersion base = null;
        if (playbook != null) {
            // Build on the newest draft if one is waiting, otherwise on the current published version.
            base = latestDraft(playbook.id).orElse(playbook.currentVersion != null ? playbook.currentVersion : c.playbookVersion);
        }
        List<UpdateNote> updates = new ArrayList<>();
        List<CaseEvent> events = CaseEvent.list("caseFile.id = ?1 and type = ?2 order by createdAt", c.id, CaseEventType.UPDATE_POSTED);
        for (CaseEvent e : events) {
            if (e.body != null) {
                updates.add(new UpdateNote(e.body, e.actor == null ? null : e.actor.kind.name()));
            }
        }
        List<CaseParticipant> participants = CaseParticipant.list("caseFile.id", c.id);
        int helped = (int) participants.stream().filter(p -> p.outcome == Outcome.HELPED).count();
        String lang = c.community.defaultLocale;
        DraftInput input = new DraftInput(
                playbook == null ? c.title.in(lang) : playbook.title.in(lang),
                base == null ? List.of() : base.steps,
                c.summary == null ? c.title.in("en") : c.summary.in("en"),
                updates, helped, participants.size(), c.number,
                c.community.name.values(), c.community.localeList(), c.community.defaultLocale);
        return new DraftBasis(playbook == null ? null : playbook.id, base == null ? null : base.id, input);
    }

    private void saveDraft(UUID caseId, DraftBasis basis, DraftResult result) {
        CaseFile c = CaseFile.findById(caseId);
        Playbook playbook = basis.playbookId() == null ? newPlaybook(c) : Playbook.findById(basis.playbookId());
        PlaybookVersion base = basis.baseVersionId() == null ? null : PlaybookVersion.findById(basis.baseVersionId());

        PlaybookVersion draft = latestDraft(playbook.id).orElse(null);
        if (draft == null) {
            draft = new PlaybookVersion();
            draft.playbook = playbook;
            draft.number = nextNumber(playbook.id);
            draft.effort = base == null ? null : base.effort;
        }
        draft.steps = renumber(result.steps());
        draft.changeNotes = result.changeNotes();
        draft.draftedBy = DraftedBy.AI;
        draft.status = PlaybookStatus.DRAFT;
        Set<UUID> sources = new LinkedHashSet<>(Arrays.asList(draft.sourceCaseIds));
        sources.add(caseId);
        draft.sourceCaseIds = sources.toArray(UUID[]::new);
        draft.persist();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("playbookId", playbook.id.toString());
        data.put("playbookVersion", draft.number);
        data.put("newPlaybook", basis.playbookId() == null);
        workflow.event(c, CaseEventType.PLAYBOOK_DRAFTED, null, null, Visibility.MODERATORS, null, data);
        LOG.infof("Drafted %s v%d from case %s", playbook.slug, draft.number, c.displayNumber());
    }

    /** A case solved without any playbook becomes the first draft of a new one. */
    private Playbook newPlaybook(CaseFile c) {
        Playbook p = new Playbook();
        p.originCommunity = c.community;
        p.slug = slugFor(c);
        p.title = c.title;
        p.problem = c.summary;
        p.categoryCodes = c.categoryCode == null ? new String[0] : new String[] {c.categoryCode};
        p.status = PlaybookStatus.DRAFT;
        p.shared = true;
        p.persist();
        return p;
    }

    private static String slugFor(CaseFile c) {
        String base = TextTools.normalize(c.title.in("en")).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.length() > 40) {
            int cut = base.lastIndexOf('-', 40);
            base = base.substring(0, cut > 10 ? cut : 40);
        }
        String slug = (base.isEmpty() ? "playbook" : base) + "-" + c.number;
        return Playbook.count("slug", slug) == 0 ? slug : slug + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    private static List<PlaybookStep> renumber(List<PlaybookStep> steps) {
        List<PlaybookStep> out = new ArrayList<>();
        int i = 1;
        for (PlaybookStep s : steps) {
            out.add(s.withPosition(i++, s.change()));
        }
        return out;
    }

    private static int nextNumber(UUID playbookId) {
        Integer max = PlaybookVersion.getEntityManager()
                .createQuery("select max(v.number) from PlaybookVersion v where v.playbook.id = :id", Integer.class)
                .setParameter("id", playbookId)
                .getSingleResult();
        return (max == null ? 0 : max) + 1;
    }

    public static Optional<PlaybookVersion> latestDraft(UUID playbookId) {
        return PlaybookVersion.find("playbook.id = ?1 and status = ?2 order by number desc", playbookId, PlaybookStatus.DRAFT)
                .firstResultOptional();
    }

    /**
     * Moderators of the playbook's home community can publish, and so can moderators of the communities
     * whose cases produced the draft (they know what actually happened).
     */
    public static boolean canPublish(PlaybookVersion v, Set<UUID> moderatedCommunities) {
        Playbook p = v.playbook;
        if (p.originCommunity != null && moderatedCommunities.contains(p.originCommunity.id)) {
            return true;
        }
        for (UUID caseId : v.sourceCaseIds) {
            CaseFile c = CaseFile.findById(caseId);
            if (c != null && moderatedCommunities.contains(c.community.id)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public PlaybookVersion publish(AppUser user, Set<UUID> moderatedCommunities, UUID playbookId, int number) {
        Playbook p = Playbook.<Playbook>findByIdOptional(playbookId).orElseThrow(() -> Problems.notFound("Playbook not found."));
        PlaybookVersion v = PlaybookVersion.<PlaybookVersion>find("playbook.id = ?1 and number = ?2", playbookId, number)
                .firstResultOptional()
                .orElseThrow(() -> Problems.notFound("Version " + number + " not found."));
        if (!canPublish(v, moderatedCommunities)) {
            throw Problems.forbidden("Only moderators of the playbook's community, or of the community whose case drafted it, can publish.");
        }
        if (v.status != PlaybookStatus.DRAFT) {
            throw Problems.conflict("Version " + number + " is " + v.status + "; only drafts can be published.");
        }
        if (v.steps == null || v.steps.isEmpty()) {
            throw Problems.badRequest("A playbook version needs at least one step.");
        }
        if (p.currentVersion != null && !p.currentVersion.id.equals(v.id)) {
            p.currentVersion.status = PlaybookStatus.ARCHIVED;
        }
        // Steps keep their NEW/CHANGED marks, so the playbook page can show what this version added.
        v.status = PlaybookStatus.PUBLISHED;
        v.publishedBy = user;
        v.publishedAt = Instant.now();
        p.currentVersion = v;
        p.status = PlaybookStatus.PUBLISHED;

        UUID id = p.id;
        afterCommit.async(() -> embeddings.refreshPlaybook(id));
        if (p.originCommunity != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("playbookId", id.toString());
            data.put("playbookVersion", v.number);
            LiveEvent event = new LiveEvent("PLAYBOOK_PUBLISHED", p.originCommunity.id, null, null, Visibility.PUBLIC,
                    Set.of(), Set.of(), data, Instant.now());
            afterCommit.sync(() -> live.publish(event));
        }
        return v;
    }
}

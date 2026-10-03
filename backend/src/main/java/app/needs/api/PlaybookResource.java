package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.CaseStatus;
import app.needs.model.Community;
import app.needs.model.Outcome;
import app.needs.model.Playbook;
import app.needs.model.PlaybookStatus;
import app.needs.model.PlaybookVersion;
import app.needs.service.PlaybookService;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/playbooks")
@Tag(name = "Playbooks and admin")
public class PlaybookResource {

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    @Inject
    CaseViews views;

    @Inject
    PlaybookService playbooks;

    @Inject
    EntityManager em;

    public record SourceCase(UUID id, String number, String community) {
    }

    public record VersionView(UUID id, int number, String status, String draftedBy, Instant createdAt, Instant publishedAt,
                              String publishedBy, TextDto changeNotes, List<CaseViews.StepView> steps,
                              CaseViews.EffortView effort, List<SourceCase> sourceCases) {
    }

    public record VersionSummary(int number, String status, String draftedBy, Instant createdAt, Instant publishedAt,
                                 TextDto changeNotes) {
    }

    /** How the playbook did: cases that used any version, how many closed, residents who said it helped. */
    public record Results(long cases, long closedCases, long residentsHelped, List<String> communities) {
    }

    public record PlaybookMe(boolean canPublish) {
    }

    public record PlaybookView(UUID id, String slug, TextDto title, TextDto problem, List<String> categoryCodes,
                               String status, boolean shared, CaseViews.CommunityRef origin, String lang,
                               VersionView current, VersionView draft, List<VersionSummary> versions, Results results,
                               PlaybookMe me) {
    }

    @GET
    @Path("/{ref}")
    @Operation(summary = "A playbook by id or slug: current version, the waiting draft, results and version history")
    public PlaybookView get(@RestPath String ref) {
        return view(find(ref));
    }

    @POST
    @Path("/{id}/versions/{number}/publish")
    @Transactional
    @Operation(summary = "Make the draft the current version; the previous one is archived")
    public PlaybookView publish(@RestPath String id, @RestPath int number) {
        AppUser user = current.require();
        Playbook p = find(id);
        playbooks.publish(user, moderated(), p.id, number);
        return view(p);
    }

    private static Playbook find(String ref) {
        Playbook p;
        try {
            p = Playbook.findById(UUID.fromString(ref));
        } catch (IllegalArgumentException notUuid) {
            p = Playbook.find("slug", ref).firstResult();
        }
        if (p == null) {
            throw Problems.notFound("Playbook not found: " + ref);
        }
        return p;
    }

    private Set<UUID> moderated() {
        return current.find().isPresent() ? current.audience().moderated() : Set.of();
    }

    private PlaybookView view(Playbook p) {
        Community home = p.originCommunity;
        String lang = home == null ? current.find().map(u -> u.locale).orElse("en") : locales.pick(home);
        List<PlaybookVersion> versions = PlaybookVersion.list("playbook.id = ?1 order by number desc", p.id);
        PlaybookVersion draft = versions.stream().filter(v -> v.status == PlaybookStatus.DRAFT)
                .findFirst().orElse(null);
        List<VersionSummary> history = versions.stream()
                .map(v -> new VersionSummary(v.number, v.status.name(), v.draftedBy.name(), v.createdAt, v.publishedAt,
                        TextDto.of(v.changeNotes, lang)))
                .toList();
        boolean canPublish = draft != null && PlaybookService.canPublish(draft, moderated());
        return new PlaybookView(p.id, p.slug, TextDto.of(p.title, lang), TextDto.of(p.problem, lang),
                Arrays.asList(p.categoryCodes), p.status.name(), p.shared,
                home == null ? null : views.communityRef(home, lang), lang,
                version(p.currentVersion, lang), version(draft, lang), history, results(p, versions, lang), new PlaybookMe(canPublish));
    }

    private VersionView version(PlaybookVersion v, String lang) {
        if (v == null) {
            return null;
        }
        List<SourceCase> sources = new ArrayList<>();
        for (UUID caseId : v.sourceCaseIds) {
            CaseFile c = CaseFile.findById(caseId);
            if (c != null) {
                sources.add(new SourceCase(c.id, c.displayNumber(), c.community.name.in(lang)));
            }
        }
        return new VersionView(v.id, v.number, v.status.name(), v.draftedBy.name(), v.createdAt, v.publishedAt,
                v.publishedBy == null ? null : v.publishedBy.displayName, TextDto.of(v.changeNotes, lang),
                v.steps.stream().map(s -> views.step(s, lang)).toList(), views.effort(v.effort, lang), sources);
    }

    /** Cases that used any version, plus the cases whose learnings went into one (a new playbook's first case). */
    private Results results(Playbook p, List<PlaybookVersion> versions, String lang) {
        Set<UUID> ids = new LinkedHashSet<>(em.createQuery(
                        "select c.id from CaseFile c where c.playbookVersion.playbook.id = :id", UUID.class)
                .setParameter("id", p.id)
                .getResultList());
        versions.forEach(v -> ids.addAll(Arrays.asList(v.sourceCaseIds)));
        if (ids.isEmpty()) {
            return new Results(0, 0, 0, List.of());
        }
        List<CaseFile> cases = CaseFile.list("id in ?1 order by createdAt", ids);
        long closed = cases.stream().filter(c -> c.status == CaseStatus.CLOSED).count();
        long helped = em.createQuery(
                        "select count(cp) from CaseParticipant cp where cp.caseFile.id in :ids and cp.outcome = :helped", Long.class)
                .setParameter("ids", ids)
                .setParameter("helped", Outcome.HELPED)
                .getSingleResult();
        List<String> communities = cases.stream().map(c -> c.community.name.in(lang)).distinct().toList();
        return new Results(cases.size(), closed, helped, communities);
    }
}

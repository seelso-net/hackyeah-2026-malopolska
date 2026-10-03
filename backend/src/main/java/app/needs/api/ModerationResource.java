package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.CaseStatus;
import app.needs.model.Community;
import app.needs.model.MatchSuggestion;
import app.needs.model.ReportKind;
import app.needs.model.Urgency;
import app.needs.service.CaseService;
import app.needs.service.MatchingService;
import app.needs.service.MatchingService.Approval;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import app.needs.support.Problems;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/api/moderation")
@Tag(name = "Moderators")
public class ModerationResource {

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    @Inject
    CaseViews views;

    @Inject
    MatchingService matching;

    @Inject
    EntityManager em;

    @Inject
    OptionalBody optional;

    /**
     * MATCH_READY: the AI proposes a playbook and doers. NO_PLAYBOOK: doers only, nothing proven fits yet.
     * PROCESSING: the AI is still reading or matching.
     */
    public record QueueItem(@JsonUnwrapped CaseViews.CaseCard card, String aiStatus, UUID suggestionId,
                            TextDto playbookTitle, int doerCount, boolean safetyConcern) {
    }

    public record QueueView(CaseViews.CommunityRef community, String lang, Map<String, Long> counts, List<QueueItem> items) {
    }

    @GET
    @Path("/queue")
    @Operation(summary = "Cases waiting for a decision, by urgency then support; filter by kind (NEED, IDEA) and urgency")
    public QueueView queue(@RestQuery String community, @RestQuery String kind, @RestQuery String urgency) {
        if (community == null || community.isBlank()) {
            throw Problems.badRequest("Add ?community=<slug>, e.g. ?community=riverside");
        }
        Community c = Community.bySlug(community).orElseThrow(() -> Problems.notFound("No community " + community));
        current.requireModerator(c.id);
        String lang = locales.pick(c);

        List<CaseStatus> waiting = List.of(CaseStatus.NEW, CaseStatus.TRIAGED, CaseStatus.SUGGESTED);
        List<CaseFile> cases = CaseFile.list("community.id = ?1 and status in ?2", c.id, waiting);
        ReportKind kindFilter = kind == null || kind.isBlank() ? null : ReportKind.valueOf(kind.trim().toUpperCase());
        Urgency urgencyFilter = urgency == null || urgency.isBlank() ? null : Urgency.parse(urgency, null);
        cases = cases.stream()
                .filter(cf -> kindFilter == null || cf.kind == kindFilter)
                .filter(cf -> urgencyFilter == null || cf.urgency == urgencyFilter)
                .sorted(Comparator.comparing((CaseFile cf) -> -cf.urgency.ordinal())
                        .thenComparing(cf -> -(cf.reportCount + cf.supporterCount))
                        .thenComparing(cf -> cf.createdAt))
                .toList();

        Set<UUID> unsafe = safetyConcerns(cases.stream().map(cf -> cf.id).toList());
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", 0L);
        counts.put("matchReady", 0L);
        counts.put("noPlaybook", 0L);
        counts.put("processing", 0L);
        List<QueueItem> items = cases.stream().map(cf -> {
            MatchSuggestion s = MatchSuggestion.pendingFor(cf.id).orElse(null);
            String aiStatus = s == null ? "PROCESSING" : s.playbookVersion == null ? "NO_PLAYBOOK" : "MATCH_READY";
            long doers = s == null ? 0 : em.createQuery(
                            "select count(mc) from MatchCandidate mc where mc.suggestion.id = :id and mc.selected = true", Long.class)
                    .setParameter("id", s.id)
                    .getSingleResult();
            counts.merge("total", 1L, Long::sum);
            counts.merge(switch (aiStatus) {
                case "MATCH_READY" -> "matchReady";
                case "NO_PLAYBOOK" -> "noPlaybook";
                default -> "processing";
            }, 1L, Long::sum);
            return new QueueItem(views.card(cf, lang, true), aiStatus, s == null ? null : s.id,
                    s == null || s.playbookVersion == null ? null : TextDto.of(s.playbookVersion.playbook.title, lang),
                    (int) doers, unsafe.contains(cf.id));
        }).toList();
        return new QueueView(views.communityRef(c, lang), lang, counts, items);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> safetyConcerns(List<UUID> caseIds) {
        if (caseIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = em.createNativeQuery("""
                        SELECT DISTINCT r.case_id FROM report r
                        WHERE r.case_id IN (:ids) AND (r.ai_result ->> 'safetyConcern')::boolean
                        """, UUID.class)
                .setParameter("ids", caseIds)
                .getResultList();
        return new HashSet<>(ids);
    }

    @GET
    @Path("/cases/{id}")
    @Operation(summary = "Full case for the moderator: every report, the timeline and the AI's pending suggestion")
    public CaseViews.ModerationDetail caseDetail(@RestPath UUID id) {
        CaseFile c = CaseService.find(id);
        current.requireModerator(c.community.id);
        return views.moderationDetail(c);
    }

    @POST
    @Path("/suggestions/{id}/approve")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Approval.class)))
    @Operation(summary = "Approve the match: optional playbookVersionId and actorIds override the AI's picks. "
            + "Creates the assignments and the checklist, and notifies the doers")
    public CaseViews.ModerationDetail approve(@RestPath UUID id, String json) {
        Approval body = optional.read(json, Approval.class);
        MatchSuggestion s = MatchSuggestion.<MatchSuggestion>findByIdOptional(id)
                .orElseThrow(() -> Problems.notFound("Suggestion not found."));
        current.requireModerator(s.caseFile.community.id);
        AppUser moderator = current.require();
        CaseFile c = matching.approve(moderator, id, body);
        return views.moderationDetail(c);
    }
}

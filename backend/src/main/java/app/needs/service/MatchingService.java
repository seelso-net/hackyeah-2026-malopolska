package app.needs.service;

import app.needs.ai.Ai;
import app.needs.ai.AiTypes.DoerOption;
import app.needs.ai.AiTypes.DoerPick;
import app.needs.ai.AiTypes.MatchDecision;
import app.needs.ai.AiTypes.MatchInput;
import app.needs.ai.AiTypes.PlaybookOption;
import app.needs.model.Actor;
import app.needs.model.AppUser;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseStatus;
import app.needs.model.CaseTask;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.model.MatchCandidate;
import app.needs.model.MatchSuggestion;
import app.needs.model.Playbook;
import app.needs.model.PlaybookStep;
import app.needs.model.PlaybookVersion;
import app.needs.model.SuggestionStatus;
import app.needs.model.Visibility;
import app.needs.support.Problems;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds a proven playbook and the doers for a case (vector search narrows, the AI picks and explains),
 * and turns a moderator's approval into assignments and a checklist.
 */
@ApplicationScoped
public class MatchingService {

    @Inject
    Ai ai;

    @Inject
    VectorSearch search;

    @Inject
    CaseWorkflow workflow;

    public record Approval(UUID playbookVersionId, List<UUID> actorIds) {
    }

    /** A new case: the AI already read it, so mark it triaged and look for a match. */
    public void triageAndSuggest(UUID caseId) {
        QuarkusTransaction.requiringNew().run(() -> {
            CaseFile c = CaseFile.findById(caseId);
            if (c != null && c.status == CaseStatus.NEW) {
                workflow.changeStatus(c, CaseStatus.TRIAGED, null, "AI");
            }
        });
        suggest(caseId);
    }

    /** TRIAGED -> SUGGESTED with a pending suggestion for the moderator. Does nothing if one is pending. */
    public void suggest(UUID caseId) {
        MatchInput input = QuarkusTransaction.requiringNew().call(() -> input(caseId));
        if (input == null) {
            return;
        }
        MatchDecision decision = ai.match(input);
        QuarkusTransaction.requiringNew().run(() -> save(caseId, input, decision));
    }

    private MatchInput input(UUID caseId) {
        CaseFile c = CaseFile.findById(caseId);
        if (c == null || c.status != CaseStatus.TRIAGED || c.embedding == null
                || MatchSuggestion.pendingFor(caseId).isPresent()) {
            return null;
        }
        Community community = c.community;
        CommunityConfig cfg = community.cfg();
        List<PlaybookOption> playbooks = new ArrayList<>();
        for (VectorSearch.PlaybookHit h : search.playbooks(community.id, c.embedding, c.categoryCode, 5)) {
            Playbook p = Playbook.findById(h.playbookId());
            playbooks.add(new PlaybookOption(p.id, h.versionId(), p.title.values(),
                    p.originCommunity == null ? Map.of() : p.originCommunity.name.values(), h.similarity(), h.categoryMatch()));
        }
        List<DoerOption> doers = new ArrayList<>();
        for (VectorSearch.ActorHit h : search.actors(community.id, c.embedding, c.categoryCode, c.lat, c.lng, 10)) {
            Actor a = Actor.findById(h.actorId());
            doers.add(new DoerOption(a.id, a.name, a.kind, a.description == null ? "" : a.description.in("en"),
                    h.similarity(), h.categoryMatch(), h.distanceMeters(), h.serviceRadiusMeters()));
        }
        String caseText = c.title.in("en") + ". " + (c.summary == null ? "" : c.summary.in("en"));
        Map<String, String> categoryLabels = cfg.category(c.categoryCode)
                .map(CommunityConfig.Category::labels).orElse(Map.of());
        return new MatchInput(c.id, caseText, c.categoryCode, categoryLabels, community.localeList(),
                community.defaultLocale, playbooks, doers, ai.minPlaybookSimilarity());
    }

    private void save(UUID caseId, MatchInput input, MatchDecision decision) {
        CaseFile c = CaseFile.findById(caseId);
        if (c == null || c.status != CaseStatus.TRIAGED || MatchSuggestion.pendingFor(caseId).isPresent()) {
            return;
        }
        MatchSuggestion s = new MatchSuggestion();
        s.caseFile = c;
        if (decision.playbookId() != null) {
            input.playbooks().stream()
                    .filter(p -> p.playbookId().equals(decision.playbookId()))
                    .findFirst()
                    .ifPresent(p -> s.playbookVersion = PlaybookVersion.findById(p.versionId()));
        }
        s.reason = decision.reason();
        s.model = ai.engineId();
        Map<String, Object> scores = new LinkedHashMap<>();
        input.playbooks().forEach(p -> scores.put("playbook:" + p.playbookId(), p.similarity()));
        s.scores = scores;
        s.persist();
        for (DoerPick pick : decision.doers()) {
            MatchCandidate mc = new MatchCandidate();
            mc.suggestion = s;
            mc.actor = Actor.findById(pick.actorId());
            mc.reason = pick.reason();
            mc.score = pick.score();
            mc.selected = pick.selected();
            mc.persist();
        }
        workflow.changeStatus(c, CaseStatus.SUGGESTED, null, "AI");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("suggestionId", s.id.toString());
        if (decision.playbookId() != null) {
            data.put("playbookId", decision.playbookId().toString());
        }
        workflow.event(c, CaseEventType.MATCH_SUGGESTED, null, null, Visibility.MODERATORS, null, data);
    }

    /** Roles are listed in order of who leads the step: an NGO step with volunteers goes to the NGO. */
    private static CaseAssignment ownerFor(PlaybookStep step, List<CaseAssignment> assignments) {
        for (String role : step.roles()) {
            for (CaseAssignment a : assignments) {
                if (a.actor.kind.name().equals(role)) {
                    return a;
                }
            }
        }
        return null;
    }

    /** The moderator's decision: assignments for the chosen doers and a checklist from the playbook steps. */
    @Transactional
    public CaseFile approve(AppUser moderator, UUID suggestionId, Approval in) {
        MatchSuggestion s = MatchSuggestion.<MatchSuggestion>findByIdOptional(suggestionId)
                .orElseThrow(() -> Problems.notFound("Suggestion not found."));
        if (s.status != SuggestionStatus.PENDING) {
            throw Problems.conflict("This suggestion was already decided.");
        }
        CaseFile c = s.caseFile;
        List<MatchCandidate> candidates = MatchCandidate.list("suggestion.id", s.id);
        Set<UUID> actorIds = new LinkedHashSet<>();
        if (in != null && in.actorIds() != null) {
            actorIds.addAll(in.actorIds());
        } else {
            candidates.stream().filter(mc -> mc.selected).forEach(mc -> actorIds.add(mc.actor.id));
        }
        if (actorIds.isEmpty()) {
            throw Problems.badRequest("Pick at least one doer.");
        }
        PlaybookVersion version = s.playbookVersion;
        if (in != null && in.playbookVersionId() != null) {
            version = PlaybookVersion.<PlaybookVersion>findByIdOptional(in.playbookVersionId())
                    .orElseThrow(() -> Problems.badRequest("Unknown playbook version."));
        }

        candidates.forEach(mc -> mc.selected = actorIds.contains(mc.actor.id));
        s.status = SuggestionStatus.APPROVED;
        s.decidedBy = moderator;
        s.decidedAt = Instant.now();
        c.playbookVersion = version;

        List<CaseAssignment> assignments = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID actorId : actorIds) {
            Actor actor = Actor.<Actor>findByIdOptional(actorId).orElseThrow(() -> Problems.badRequest("Unknown doer " + actorId));
            if (!actor.community.id.equals(c.community.id)) {
                throw Problems.badRequest(actor.name + " does not work in this community.");
            }
            CaseAssignment a = CaseAssignment.find("caseFile.id = ?1 and actor.id = ?2", c.id, actorId).firstResult();
            if (a == null) {
                a = new CaseAssignment();
                a.caseFile = c;
                a.actor = actor;
                a.assignedBy = moderator;
                a.persist();
            }
            assignments.add(a);
            names.add(actor.name);
        }
        if (version != null) {
            for (PlaybookStep step : version.steps) {
                CaseTask t = new CaseTask();
                t.caseFile = c;
                t.position = step.position();
                t.title = step.title();
                t.assignment = ownerFor(step, assignments);
                t.persist();
            }
        }
        workflow.changeStatus(c, CaseStatus.MATCHED, moderator, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("suggestionId", s.id.toString());
        data.put("actorNames", names);
        if (version != null) {
            data.put("playbookId", version.playbook.id.toString());
            data.put("playbookVersion", version.number);
        }
        workflow.event(c, CaseEventType.MATCH_APPROVED, moderator, null, Visibility.PUBLIC, null, data);
        return c;
    }
}

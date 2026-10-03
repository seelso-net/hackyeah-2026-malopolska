package app.needs.api;

import app.needs.model.AssignmentStatus;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseEvent;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.CaseTask;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.model.MatchCandidate;
import app.needs.model.MatchSuggestion;
import app.needs.model.MediaAsset;
import app.needs.model.Membership;
import app.needs.model.Outcome;
import app.needs.model.Playbook;
import app.needs.model.PlaybookEffort;
import app.needs.model.PlaybookStep;
import app.needs.model.PlaybookVersion;
import app.needs.model.Report;
import app.needs.model.Role;
import app.needs.support.CurrentUser;
import app.needs.support.Geo;
import app.needs.support.Locales;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns cases, assignments and suggestions into what the screens show, in the reader's language.
 * Codes (status, category, urgency) travel as codes plus a label; people's text travels as TextDto.
 */
@ApplicationScoped
public class CaseViews {

    @Inject
    Access access;

    @Inject
    Locales locales;

    @Inject
    CurrentUser current;

    public record CommunityRef(String slug, String name) {
    }

    public record CaseCard(UUID id, String number, String kind, TextDto title, String categoryCode, String categoryLabel,
                           String status, String statusLabel, String urgency, String areaLabel, Double lat, Double lng,
                           boolean exactLocation, int reportCount, int supporterCount, Instant createdAt, Instant updatedAt,
                           CommunityRef community) {
    }

    public record PlaybookRef(UUID id, String slug, UUID versionId, int version, TextDto title, String origin) {
    }

    public record Helper(UUID assignmentId, UUID actorId, String name, String kind, String status) {
    }

    public record TaskView(UUID id, int position, TextDto title, boolean done, Instant doneAt, String doneBy,
                           UUID assignmentId, String owner) {
    }

    public record MediaView(UUID id, String kind, String mimeType, String url) {
    }

    public record TimelineItem(UUID id, String type, Instant at, String visibility, String authorName, String actorName,
                               String actorKind, TextDto text, Map<String, Object> data, List<MediaView> media) {
    }

    public record Outcomes(long helped, long notHelped, long waiting) {
    }

    /** What the caller can do on this case; the UI shows buttons from it. */
    public record CaseMe(boolean signedIn, boolean moderator, boolean participant, String participantRole, String outcome,
                         boolean canAnswerOutcome, UUID assignmentId, String assignmentStatus, boolean canPostUpdate) {
    }

    public record CaseDetail(@JsonUnwrapped CaseCard card, TextDto summary, Instant resolvedAt, Instant closedAt,
                             PlaybookRef playbook, List<Helper> helpers, List<TaskView> tasks, Outcomes outcomes,
                             List<TimelineItem> timeline, CaseMe me) {
    }

    public record ReportItem(UUID id, String author, boolean anonymous, Instant createdAt, String kind, String inputMode,
                             TextDto text, Double lat, Double lng, String address, String categoryCode, String urgency,
                             boolean safetyConcern, List<MediaView> media) {
    }

    public record StepView(int position, TextDto title, TextDto description, List<String> roles, String change) {
    }

    public record EffortView(String coordinatorTime, String budget, String firstHelp) {
    }

    public record PlaybookPick(UUID id, String slug, UUID versionId, int version, TextDto title, String origin,
                               TextDto problem, List<StepView> steps, EffortView effort, Double similarity) {
    }

    public record Alternative(UUID playbookId, UUID versionId, TextDto title, String origin, double similarity) {
    }

    public record CandidateView(UUID actorId, String name, String kind, TextDto reason, Double score, boolean selected) {
    }

    public record SuggestionView(UUID id, String status, String engine, Instant createdAt, TextDto reason,
                                 PlaybookPick playbook, List<CandidateView> doers, List<Alternative> alternatives) {
    }

    public record ModerationDetail(@JsonUnwrapped CaseDetail detail, List<ReportItem> reports, SuggestionView suggestion) {
    }

    public record ActorRef(UUID id, String name, String kind) {
    }

    public record AssignmentView(UUID id, String status, Instant createdAt, Instant respondedAt, TextDto note,
                                 ActorRef actor, @JsonProperty("case") CaseCard caseCard, TextDto summary,
                                 PlaybookRef playbook, List<TaskView> tasks, int tasksDone, List<Helper> team,
                                 String assignedBy) {
    }

    // ---------------------------------------------------------------------------------------------

    public CommunityRef communityRef(Community community, String lang) {
        return new CommunityRef(community.slug, community.name.in(lang));
    }

    public static String statusLabel(CommunityConfig cfg, CaseStatus status, String lang) {
        String label = cfg.statusLabel(status, lang);
        return label == null ? status.name() : label;
    }

    public CaseCard card(CaseFile c, String lang, boolean exactLocation) {
        CommunityConfig cfg = c.community.cfg();
        Geo.Point p = exactLocation ? new Geo.Point(c.lat, c.lng) : Geo.round(c.lat, c.lng, cfg.rules().locationRounding());
        return new CaseCard(c.id, c.displayNumber(), c.kind.name(), TextDto.of(c.title, lang), c.categoryCode,
                cfg.categoryLabel(c.categoryCode, lang), c.status.name(), statusLabel(cfg, c.status, lang),
                c.urgency.name(), c.areaLabel, p.lat(), p.lng(), exactLocation, c.reportCount, c.supporterCount,
                c.createdAt, c.updatedAt, communityRef(c.community, lang));
    }

    public PlaybookRef playbookRef(PlaybookVersion v, String lang) {
        if (v == null) {
            return null;
        }
        Playbook p = v.playbook;
        return new PlaybookRef(p.id, p.slug, v.id, v.number, TextDto.of(p.title, lang),
                p.originCommunity == null ? null : p.originCommunity.name.in(lang));
    }

    public StepView step(PlaybookStep s, String lang) {
        return new StepView(s.position(), TextDto.of(s.title(), lang), TextDto.of(s.description(), lang), s.roles(), s.change());
    }

    public EffortView effort(PlaybookEffort e, String lang) {
        if (e == null) {
            return null;
        }
        return new EffortView(CommunityConfig.Labels.pick(e.coordinatorTime(), lang, null),
                CommunityConfig.Labels.pick(e.budget(), lang, null),
                CommunityConfig.Labels.pick(e.firstHelp(), lang, null));
    }

    public List<TaskView> tasks(CaseFile c, String lang) {
        List<CaseTask> tasks = CaseTask.list("caseFile.id = ?1 order by position", c.id);
        return tasks.stream()
                .map(t -> new TaskView(t.id, t.position, TextDto.of(t.title, lang), t.doneAt != null, t.doneAt,
                        t.doneBy == null ? null : t.doneBy.displayName,
                        t.assignment == null ? null : t.assignment.id,
                        t.assignment == null ? null : t.assignment.actor.name))
                .toList();
    }

    public List<Helper> helpers(CaseFile c, boolean includeAll) {
        List<CaseAssignment> list = CaseAssignment.list("caseFile.id = ?1 order by createdAt", c.id);
        return list.stream()
                .filter(a -> includeAll || a.status == AssignmentStatus.ACCEPTED || a.status == AssignmentStatus.DONE)
                .map(a -> new Helper(a.id, a.actor.id, a.actor.name, a.actor.kind.name(), a.status.name()))
                .toList();
    }

    public static MediaView media(MediaAsset m) {
        return new MediaView(m.id, m.kind.name(), m.mimeType, "/api/media/" + m.id);
    }

    // ---------------------------------------------------------------------------------------------

    public CaseDetail detail(CaseFile c) {
        String lang = locales.pick(c.community);
        boolean moderator = access.moderator(c);
        Optional<CaseParticipant> participation = access.participation(c);
        Optional<CaseAssignment> mine = access.myAssignment(c);
        boolean involved = moderator || participation.isPresent() || mine.isPresent();
        boolean staff = moderator || mine.isPresent();

        List<CaseParticipant> participants = CaseParticipant.list("caseFile.id", c.id);
        Outcomes outcomes = new Outcomes(
                participants.stream().filter(p -> p.outcome == Outcome.HELPED).count(),
                participants.stream().filter(p -> p.outcome == Outcome.NOT_HELPED).count(),
                participants.stream().filter(p -> p.outcome == null).count());

        UUID me = current.find().map(u -> u.id).orElse(null);
        Set<UUID> staffUsers = staffUserIds(c.community.id);
        List<CaseEvent> events = CaseEvent.list("caseFile.id = ?1 order by createdAt, id", c.id);
        List<CaseEvent> visible = events.stream().filter(e -> access.canSeeEvent(e, moderator, involved)).toList();
        Map<UUID, List<MediaView>> media = eventMedia(visible);
        List<TimelineItem> timeline = new ArrayList<>();
        for (CaseEvent e : visible) {
            String author = null;
            if (e.authorUser != null) {
                boolean named = moderator || staffUsers.contains(e.authorUser.id) || e.authorUser.id.equals(me);
                author = named ? e.authorUser.displayName : null;
            }
            timeline.add(new TimelineItem(e.id, e.type.name(), e.createdAt, e.visibility.name(), author,
                    e.actor == null ? null : e.actor.name, e.actor == null ? null : e.actor.kind.name(),
                    TextDto.of(e.body, lang), e.data, media.getOrDefault(e.id, List.of())));
        }

        CaseParticipant p = participation.orElse(null);
        boolean answerable = c.status == CaseStatus.RESOLVED || c.status == CaseStatus.CONFIRMED || c.status == CaseStatus.CLOSED;
        boolean acting = mine.filter(a -> a.status == AssignmentStatus.ACCEPTED || a.status == AssignmentStatus.DONE).isPresent();
        CaseMe caseMe = new CaseMe(me != null, moderator, p != null, p == null ? null : p.role.name(),
                p == null || p.outcome == null ? null : p.outcome.name(),
                p != null && p.outcome == null && answerable,
                mine.map(a -> a.id).orElse(null), mine.map(a -> a.status.name()).orElse(null),
                c.status.isOpen() && (moderator || acting));

        return new CaseDetail(card(c, lang, involved), TextDto.of(c.summary, lang), c.resolvedAt, c.closedAt,
                playbookRef(c.playbookVersion, lang), helpers(c, staff), tasks(c, lang), outcomes, timeline, caseMe);
    }

    public ModerationDetail moderationDetail(CaseFile c) {
        String lang = locales.pick(c.community);
        List<Report> reports = Report.list("caseFile.id = ?1 order by createdAt desc", c.id);
        Map<UUID, List<MediaView>> media = reportMedia(reports);
        List<ReportItem> items = reports.stream()
                .map(r -> new ReportItem(r.id,
                        r.anonymous || r.author == null ? null : r.author.displayName, r.anonymous, r.createdAt,
                        r.kind.name(), r.inputMode == null ? null : r.inputMode.name(), TextDto.of(r.body, lang),
                        r.lat, r.lng, r.addressLabel,
                        r.aiResult == null ? null : r.aiResult.categoryCode(),
                        r.aiResult == null || r.aiResult.urgency() == null ? null : r.aiResult.urgency().name(),
                        r.aiResult != null && r.aiResult.safetyConcern(),
                        media.getOrDefault(r.id, List.of())))
                .toList();
        SuggestionView suggestion = MatchSuggestion.pendingFor(c.id).map(s -> suggestion(s, lang)).orElse(null);
        return new ModerationDetail(detail(c), items, suggestion);
    }

    public SuggestionView suggestion(MatchSuggestion s, String lang) {
        PlaybookPick pick = null;
        Map<String, Object> scores = s.scores == null ? Map.of() : s.scores;
        if (s.playbookVersion != null) {
            PlaybookVersion v = s.playbookVersion;
            Playbook p = v.playbook;
            Object similarity = scores.get("playbook:" + p.id);
            pick = new PlaybookPick(p.id, p.slug, v.id, v.number, TextDto.of(p.title, lang),
                    p.originCommunity == null ? null : p.originCommunity.name.in(lang), TextDto.of(p.problem, lang),
                    v.steps.stream().map(st -> step(st, lang)).toList(), effort(v.effort, lang),
                    similarity instanceof Number n ? n.doubleValue() : null);
        }
        UUID chosen = pick == null ? null : pick.id();
        List<Alternative> alternatives = new ArrayList<>();
        scores.forEach((key, value) -> {
            if (!key.startsWith("playbook:") || !(value instanceof Number n)) {
                return;
            }
            UUID id = UUID.fromString(key.substring("playbook:".length()));
            if (id.equals(chosen)) {
                return;
            }
            Playbook p = Playbook.findById(id);
            if (p != null && p.currentVersion != null) {
                alternatives.add(new Alternative(p.id, p.currentVersion.id, TextDto.of(p.title, lang),
                        p.originCommunity == null ? null : p.originCommunity.name.in(lang), n.doubleValue()));
            }
        });
        alternatives.sort(Comparator.comparingDouble(Alternative::similarity).reversed());
        List<MatchCandidate> candidates = MatchCandidate.list("suggestion.id", s.id);
        List<CandidateView> doers = candidates.stream()
                .sorted(Comparator.comparing((MatchCandidate mc) -> !mc.selected)
                        .thenComparing(mc -> mc.score == null ? 0 : -mc.score))
                .map(mc -> new CandidateView(mc.actor.id, mc.actor.name, mc.actor.kind.name(), TextDto.of(mc.reason, lang),
                        mc.score, mc.selected))
                .toList();
        return new SuggestionView(s.id, s.status.name(), s.model, s.createdAt, TextDto.of(s.reason, lang), pick, doers,
                alternatives);
    }

    public AssignmentView assignment(CaseAssignment a) {
        CaseFile c = a.caseFile;
        String lang = locales.pick(c.community);
        List<TaskView> tasks = tasks(c, lang);
        int done = (int) tasks.stream().filter(TaskView::done).count();
        List<Helper> team = helpers(c, true).stream().filter(h -> !h.assignmentId().equals(a.id)).toList();
        return new AssignmentView(a.id, a.status.name(), a.createdAt, a.respondedAt, TextDto.of(a.note, lang),
                new ActorRef(a.actor.id, a.actor.name, a.actor.kind.name()), card(c, lang, true),
                TextDto.of(c.summary, lang), playbookRef(c.playbookVersion, lang), tasks, done, team,
                a.assignedBy == null ? null : a.assignedBy.displayName);
    }

    // ---------------------------------------------------------------------------------------------

    private static Set<UUID> staffUserIds(UUID communityId) {
        List<Membership> staff = Membership.list("community.id = ?1 and role in ?2", communityId,
                List.of(Role.MODERATOR, Role.ADMIN, Role.DOER));
        Set<UUID> ids = new HashSet<>();
        staff.forEach(m -> ids.add(m.user.id));
        return ids;
    }

    private static Map<UUID, List<MediaView>> eventMedia(List<CaseEvent> events) {
        List<UUID> ids = events.stream().filter(e -> e.type == CaseEventType.UPDATE_POSTED).map(e -> e.id).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MediaAsset> assets = MediaAsset.list("caseEvent.id in ?1 order by createdAt", ids);
        return assets.stream().collect(Collectors.groupingBy(m -> m.caseEvent.id,
                Collectors.mapping(CaseViews::media, Collectors.toList())));
    }

    private static Map<UUID, List<MediaView>> reportMedia(List<Report> reports) {
        List<UUID> ids = reports.stream().map(r -> r.id).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MediaAsset> assets = MediaAsset.list("report.id in ?1 order by createdAt", ids);
        return assets.stream().collect(Collectors.groupingBy(m -> m.report.id,
                Collectors.mapping(CaseViews::media, Collectors.toList())));
    }
}

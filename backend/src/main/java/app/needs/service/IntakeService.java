package app.needs.service;

import app.needs.ai.Ai;
import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.ReportAnalysis;
import app.needs.ai.TextTools;
import app.needs.model.AiResult;
import app.needs.model.AppUser;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.model.InputMode;
import app.needs.model.LocalizedText;
import app.needs.model.MediaAsset;
import app.needs.model.MediaKind;
import app.needs.model.ParticipantRole;
import app.needs.model.Report;
import app.needs.model.ReportKind;
import app.needs.model.ReportStatus;
import app.needs.model.Urgency;
import app.needs.model.Visibility;
import app.needs.service.VectorSearch.CaseHit;
import app.needs.support.AfterCommit;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import app.needs.support.MediaStorage;
import app.needs.support.MediaStorage.StoredFile;
import app.needs.support.Problems;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * From a resident's message to a case: store the report, let the AI read it, look for a similar
 * open case nearby, then join that case or open a new one when the resident confirms.
 */
@ApplicationScoped
public class IntakeService {

    private static final Logger LOG = Logger.getLogger(IntakeService.class);

    @Inject
    Ai ai;

    @Inject
    AfterCommit afterCommit;

    @Inject
    MediaStorage storage;

    @Inject
    LiveEvents live;

    @Inject
    VectorSearch search;

    @Inject
    CaseWorkflow workflow;

    @Inject
    MatchingService matching;

    @Inject
    TranslationService translations;

    @Inject
    EmbeddingService embeddings;

    @Inject
    VolunteerService volunteers;

    public record NewReport(ReportKind kind, String text, Double lat, Double lng, String address, boolean anonymous) {
    }

    public record SubmitReport(UUID caseId, String title, String summary, String categoryCode, Urgency urgency) {
    }

    public record SubmitResult(UUID caseId, String result) {
    }

    @Transactional
    public Report create(AppUser author, Community community, NewReport in, FileUpload audio, List<FileUpload> photos) {
        if (in.text() == null || in.text().isBlank()) {
            throw Problems.badRequest("Send the report text: typed, or transcribed on the phone (e.g. with the Web Speech API).");
        }
        String text = in.text().trim();
        Report r = new Report();
        r.community = community;
        r.author = author;
        r.kind = in.kind() == null ? ReportKind.NEED : in.kind();
        r.inputMode = audio != null ? InputMode.VOICE : InputMode.TEXT;
        r.body = LocalizedText.of(TextTools.detectLanguage(text), text);
        r.lat = in.lat();
        r.lng = in.lng();
        r.addressLabel = in.address();
        r.anonymous = in.anonymous();
        r.persist();
        if (audio != null) {
            attach(r, audio, MediaKind.AUDIO);
        }
        if (photos != null) {
            photos.forEach(p -> attach(r, p, MediaKind.PHOTO));
        }
        UUID id = r.id;
        afterCommit.async(() -> process(id));
        return r;
    }

    private void attach(Report r, FileUpload upload, MediaKind kind) {
        StoredFile f = storage.store(upload);
        MediaAsset m = new MediaAsset();
        m.community = r.community;
        m.report = r;
        m.kind = kind;
        m.storageKey = f.key();
        m.mimeType = f.mimeType();
        m.sizeBytes = f.sizeBytes();
        m.persist();
    }

    private record Snapshot(UUID communityId, UUID authorId, String text, ReportKind kind, CommunityConfig config,
                            List<String> locales, String defaultLocale, Double lat, Double lng, int mergeRadius) {
    }

    /** The AI pipeline for one report: structure, embed, look for a similar open case nearby. */
    public void process(UUID reportId) {
        Snapshot s = QuarkusTransaction.requiringNew().call(() -> {
            Report r = Report.findById(reportId);
            Community c = r.community;
            return new Snapshot(c.id, r.author == null ? null : r.author.id, r.body.original(), r.kind, c.cfg(),
                    c.localeList(), c.defaultLocale, r.lat, r.lng, c.cfg().rules().mergeRadius());
        });

        ReportAnalysis a = ai.analyze(new AnalysisInput(s.text(), s.kind(), s.config(), s.locales(), s.defaultLocale()));
        float[] embedding = null;
        try {
            embedding = ai.embed(a.title().joined() + "\n" + a.summary().joined() + "\n" + s.text());
        } catch (RuntimeException e) {
            LOG.warnf("Could not embed report %s: %s", reportId, e.getMessage());
        }

        CaseHit similar = null;
        if (embedding != null && s.kind() != ReportKind.OFFER) {
            float[] query = embedding;
            similar = QuarkusTransaction.requiringNew()
                    .call(() -> search.openCases(s.communityId(), query, s.lat(), s.lng(), 5))
                    .stream()
                    .filter(h -> h.similarity() >= ai.minCaseSimilarity())
                    .filter(h -> h.distanceMeters() == null || h.distanceMeters() <= s.mergeRadius())
                    .findFirst()
                    .orElse(null);
        }
        List<String> otherLocales = s.locales().stream().filter(l -> !l.equals(a.language())).toList();
        Map<String, String> bodyTranslations = ai.translate(s.text(), a.language(), otherLocales);

        CaseHit hit = similar;
        float[] vector = embedding;
        QuarkusTransaction.requiringNew().run(() -> {
            Report r = Report.findById(reportId);
            r.body = LocalizedText.of(a.language(), r.body.original()).withAll(bodyTranslations);
            r.aiResult = new AiResult(a.language(), a.categoryCode(), a.urgency(), a.safetyConcern(), a.title(), a.summary(),
                    hit == null ? null : hit.id(), hit == null ? null : hit.similarity(),
                    hit == null ? null : hit.distanceMeters(), ai.engineId());
            r.embedding = vector;
            r.status = ReportStatus.PROCESSED;
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryCode", a.categoryCode());
        if (hit != null) {
            data.put("similarCaseId", hit.id().toString());
        }
        if (s.authorId() != null) {
            live.publish(LiveEvent.toUser("REPORT_PROCESSED", s.communityId(), null, reportId, s.authorId(), data));
        }
        if (a.safetyConcern()) {
            live.publish(LiveEvent.toModerators("SAFETY_ALERT", s.communityId(), null, Map.of("reportId", reportId.toString())));
        }
    }

    /** The resident confirmed what the AI understood: join the similar case, or open a new one. */
    @Transactional
    public SubmitResult submit(AppUser user, UUID reportId, SubmitReport in) {
        Report r = Report.<Report>findByIdOptional(reportId).orElseThrow(() -> Problems.notFound("Report not found."));
        if (r.author == null || !r.author.id.equals(user.id)) {
            throw Problems.forbidden("Only the author can submit this report.");
        }
        if (r.status == ReportStatus.RECEIVED) {
            throw Problems.conflict("The AI is still reading this report; wait for REPORT_PROCESSED on /api/stream.");
        }
        if (r.status != ReportStatus.PROCESSED) {
            throw Problems.conflict("This report was already submitted.");
        }
        AiResult reading = r.aiResult;
        Community community = r.community;
        LocalizedText title = edited(in.title(), reading.title());
        LocalizedText summary = edited(in.summary(), reading.summary());
        boolean wasEdited = title != reading.title() || summary != reading.summary();
        String category = in.categoryCode() != null && community.cfg().category(in.categoryCode()).isPresent()
                ? in.categoryCode() : reading.categoryCode();
        Urgency urgency = in.urgency() != null ? in.urgency() : reading.urgency();

        if (r.kind == ReportKind.OFFER) {
            volunteers.offer(user, community, category, r.lat, r.lng, r.body);
            r.status = ReportStatus.SUBMITTED;
            return new SubmitResult(null, "OFFER_RECORDED");
        }

        CaseFile c;
        boolean created;
        if (in.caseId() != null) {
            c = CaseFile.<CaseFile>findByIdOptional(in.caseId()).orElseThrow(() -> Problems.notFound("Case not found."));
            if (!c.community.id.equals(community.id) || !c.status.isOpen()) {
                throw Problems.conflict("That case is closed or belongs to another community.");
            }
            c.reportCount++;
            if (urgency.ordinal() > c.urgency.ordinal()) {
                c.urgency = urgency;
            }
            created = false;
        } else {
            c = new CaseFile();
            c.community = community;
            c.number = workflow.nextCaseNumber(community.id);
            c.kind = r.kind;
            c.title = title;
            c.summary = summary;
            c.categoryCode = category;
            c.urgency = urgency;
            c.lat = r.lat;
            c.lng = r.lng;
            c.areaLabel = r.addressLabel;
            c.reportCount = 1;
            c.embedding = r.embedding;
            c.persist();
            created = true;
        }
        r.caseFile = c;
        r.status = ReportStatus.SUBMITTED;
        workflow.addParticipant(c, user, ParticipantRole.REPORTER);
        workflow.event(c, CaseEventType.REPORT_ADDED, user, null, Visibility.PARTICIPANTS, null,
                Map.of("reportId", r.id.toString(), "reportCount", c.reportCount));

        if (created) {
            UUID caseId = c.id;
            boolean needsEmbedding = c.embedding == null || wasEdited;
            afterCommit.async(() -> {
                if (wasEdited) {
                    translations.translateCase(caseId);
                }
                if (needsEmbedding) {
                    embeddings.refreshCase(caseId);
                }
                matching.triageAndSuggest(caseId);
            });
        } else {
            // The case vector includes residents' words, so the next similar report finds it more easily.
            UUID caseId = c.id;
            afterCommit.async(() -> embeddings.refreshCase(caseId));
        }
        return new SubmitResult(c.id, created ? "CREATED" : "JOINED");
    }

    /** Text the resident changed replaces the AI's; translations are regenerated from it. */
    private static LocalizedText edited(String text, LocalizedText aiText) {
        if (text == null || text.isBlank() || aiText.values().containsValue(text.trim())) {
            return aiText;
        }
        return LocalizedText.of(TextTools.detectLanguage(text), text.trim());
    }

}

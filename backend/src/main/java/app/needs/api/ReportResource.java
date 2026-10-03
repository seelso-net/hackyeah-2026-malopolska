package app.needs.api;

import app.needs.model.AiResult;
import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.model.MediaAsset;
import app.needs.model.Report;
import app.needs.model.ReportKind;
import app.needs.service.IntakeService;
import app.needs.service.IntakeService.NewReport;
import app.needs.service.IntakeService.SubmitReport;
import app.needs.service.IntakeService.SubmitResult;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/reports")
@Tag(name = "Residents")
public class ReportResource {

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    @Inject
    Access access;

    @Inject
    CaseViews views;

    @Inject
    IntakeService intake;

    @Inject
    OptionalBody optional;

    public record NewReportBody(String community, ReportKind kind, String text, Double lat, Double lng, String address,
                                boolean anonymous) {
    }

    public record Accepted(UUID reportId, String status, String next) {
    }

    public record AiReading(String language, String categoryCode, String categoryLabel, String urgency,
                            boolean safetyConcern, TextDto title, TextDto summary, String engine) {
    }

    public record SimilarCase(UUID id, String number, TextDto title, String status, String statusLabel, int reportCount,
                              int supporterCount, Double similarity, Integer distanceMeters) {
    }

    public record ReportView(UUID id, String status, String kind, String inputMode, Instant createdAt, TextDto text,
                             Double lat, Double lng, String address, boolean anonymous, AiReading ai,
                             SimilarCase similarCase, UUID caseId, List<CaseViews.MediaView> media) {
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Operation(summary = "New report: text (typed or transcribed), optional voice note and photos, location. "
            + "Returns 202; REPORT_PROCESSED arrives on /api/stream when the AI has read it")
    public Response create(@RestForm String community, @RestForm ReportKind kind, @RestForm String text,
                           @RestForm Double lat, @RestForm Double lng, @RestForm String address,
                           @RestForm boolean anonymous, @RestForm FileUpload audio,
                           @RestForm("photo") List<FileUpload> photos) {
        return accept(new NewReportBody(community, kind, text, lat, lng, address, anonymous), audio, photos);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "New report as JSON (no files)")
    public Response createJson(NewReportBody body) {
        if (body == null) {
            throw Problems.badRequest("Send {\"community\": \"riverside\", \"text\": \"...\"}");
        }
        return accept(body, null, List.of());
    }

    private Response accept(NewReportBody in, FileUpload audio, List<FileUpload> photos) {
        AppUser user = current.require();
        if (in.community() == null) {
            throw Problems.badRequest("community is required, e.g. riverside");
        }
        Community c = Community.bySlug(in.community()).orElseThrow(() -> Problems.notFound("No community " + in.community()));
        access.ensureResident(user, c);
        Report r = intake.create(user, c,
                new NewReport(in.kind(), in.text(), in.lat(), in.lng(), in.address(), in.anonymous()), audio, photos);
        return Response.accepted(new Accepted(r.id, r.status.name(), "/api/reports/" + r.id)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Review step: what the AI understood, and the closest open case nearby (join it or open a new one)")
    public ReportView get(@RestPath UUID id) {
        Report r = Report.<Report>findByIdOptional(id).orElseThrow(() -> Problems.notFound("Report not found."));
        AppUser user = current.require();
        boolean author = r.author != null && r.author.id.equals(user.id);
        if (!author && !current.isModerator(r.community.id)) {
            throw Problems.forbidden("Only the author and moderators can open a report.");
        }
        String lang = locales.pick(r.community);
        CommunityConfig cfg = r.community.cfg();
        AiResult a = r.aiResult;
        AiReading reading = null;
        SimilarCase similar = null;
        if (a != null) {
            reading = new AiReading(a.language(), a.categoryCode(), cfg.categoryLabel(a.categoryCode(), lang),
                    a.urgency() == null ? null : a.urgency().name(), a.safetyConcern(), TextDto.of(a.title(), lang),
                    TextDto.of(a.summary(), lang), a.engine());
            if (a.similarCaseId() != null) {
                CaseFile c = CaseFile.findById(a.similarCaseId());
                if (c != null) {
                    similar = new SimilarCase(c.id, c.displayNumber(), TextDto.of(c.title, lang), c.status.name(),
                            CaseViews.statusLabel(cfg, c.status, lang), c.reportCount, c.supporterCount,
                            a.similarity(), a.distanceMeters());
                }
            }
        }
        List<MediaAsset> media = MediaAsset.list("report.id = ?1 order by createdAt", r.id);
        return new ReportView(r.id, r.status.name(), r.kind.name(), r.inputMode == null ? null : r.inputMode.name(),
                r.createdAt, TextDto.of(r.body, lang), r.lat, r.lng, r.addressLabel, r.anonymous, reading, similar,
                r.caseFile == null ? null : r.caseFile.id, media.stream().map(CaseViews::media).toList());
    }

    @POST
    @Path("/{id}/submit")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SubmitReport.class)))
    @Operation(summary = "Confirm the review: {\"caseId\": ...} joins that case, no caseId opens a new one. "
            + "Edited title, summary, category or urgency replace the AI's")
    public SubmitResult submit(@RestPath UUID id, String json) {
        AppUser user = current.require();
        SubmitReport body = optional.read(json, SubmitReport.class);
        SubmitReport in = body == null ? new SubmitReport(null, null, null, null, null) : body;
        return intake.submit(user, id, in);
    }
}

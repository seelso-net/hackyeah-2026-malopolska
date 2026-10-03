package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.Outcome;
import app.needs.service.CaseService;
import app.needs.support.CurrentUser;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
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

@Path("/api/cases")
public class CaseResource {

    @Inject
    CurrentUser current;

    @Inject
    Access access;

    @Inject
    CaseViews views;

    @Inject
    CaseService cases;

    @Inject
    OptionalBody optional;

    public record OutcomeBody(Outcome outcome) {
    }

    public record HelpOfferBody(String note) {
    }

    public record HelpOffer(UUID caseId, UUID actorId, String actorName) {
    }

    public record UpdateBody(String text, boolean resolve) {
    }

    @GET
    @Path("/{id}")
    @Tag(name = "Residents")
    @Operation(summary = "Case status: timeline, who is helping, the checklist and the playbook used")
    public CaseViews.CaseDetail get(@RestPath UUID id) {
        return views.detail(access.requireSee(CaseService.find(id)));
    }

    @POST
    @Path("/{id}/support")
    @Transactional
    @Tag(name = "Residents")
    @Operation(summary = "\"Me too\": backs the case without writing a report; the caller follows it from now on")
    public CaseViews.CaseDetail support(@RestPath UUID id) {
        AppUser user = current.require();
        CaseFile c = access.requireSee(CaseService.find(id));
        access.ensureResident(user, c.community);
        return views.detail(cases.support(user, id));
    }

    @POST
    @Path("/{id}/help-offers")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Tag(name = "Residents")
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = HelpOfferBody.class)))
    @Operation(summary = "\"I can help\": the caller becomes a volunteer near the case; moderators see the offer "
            + "and can add them when they approve the match. Optional {\"note\": \"...\"}")
    public HelpOffer offerHelp(@RestPath UUID id, String json) {
        AppUser user = current.require();
        CaseFile c = access.requireSee(CaseService.find(id));
        access.ensureResident(user, c.community);
        HelpOfferBody body = optional.read(json, HelpOfferBody.class);
        var actor = cases.offerHelp(user, id, body == null ? null : body.note());
        return new HelpOffer(id, actor.id, actor.name);
    }

    @POST
    @Path("/{id}/outcome")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Tag(name = "Residents")
    @Operation(summary = "\"Did this help?\" HELPED confirms a resolved case (it closes and the AI drafts the next "
            + "playbook version); NOT_HELPED reopens it")
    public CaseViews.CaseDetail outcome(@RestPath UUID id, OutcomeBody body) {
        AppUser user = current.require();
        CaseFile c = cases.recordOutcome(user, id, body == null ? null : body.outcome());
        return views.detail(c);
    }

    @POST
    @Path("/{id}/updates")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Tag(name = "Doers")
    @Operation(summary = "Doer update for residents: text and photos; resolve=true marks the case resolved")
    public CaseViews.CaseDetail update(@RestPath UUID id, @RestForm String text, @RestForm boolean resolve,
                                       @RestForm("photo") List<FileUpload> photos) {
        return post(id, text, resolve, photos);
    }

    @POST
    @Path("/{id}/updates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Tag(name = "Doers")
    @Operation(summary = "Doer update as JSON (no photos)")
    public CaseViews.CaseDetail updateJson(@RestPath UUID id, UpdateBody body) {
        if (body == null) {
            throw Problems.badRequest("Send {\"text\": \"...\", \"resolve\": false}");
        }
        return post(id, body.text(), body.resolve(), List.of());
    }

    private CaseViews.CaseDetail post(UUID id, String text, boolean resolve, List<FileUpload> photos) {
        AppUser user = current.require();
        CaseFile c = CaseService.find(id);
        cases.postUpdate(user, id, current.actorIds(c.community.id), current.isModerator(c.community.id), text, resolve, photos);
        return views.detail(c);
    }
}

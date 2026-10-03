package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.AssignmentStatus;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseStatus;
import app.needs.model.CaseTask;
import app.needs.service.AssignmentService;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/** The doer inbox: assignments for the organisations (or volunteer profile) the caller acts for. */
@Path("/api")
@Tag(name = "Doers")
public class DoerResource {

    private static final List<AssignmentStatus> ACTIVE = List.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED);

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    @Inject
    CaseViews views;

    @Inject
    AssignmentService assignments;

    @Inject
    OptionalBody optional;

    public record NoteBody(String note) {
    }

    public record TaskPatch(Boolean done) {
    }

    @GET
    @Path("/doer/assignments")
    @Operation(summary = "New and active assignments (status=OFFERED,ACCEPTED by default; status=all for every one)")
    public List<CaseViews.AssignmentView> inbox(@RestQuery String status) {
        current.require();
        Set<UUID> actors = current.actorIds(null);
        if (actors.isEmpty()) {
            return List.of();
        }
        List<AssignmentStatus> statuses = status == null || status.isBlank() ? ACTIVE
                : "all".equalsIgnoreCase(status.trim()) ? Arrays.asList(AssignmentStatus.values())
                : Arrays.stream(status.split(",")).map(s -> AssignmentStatus.valueOf(s.trim().toUpperCase())).toList();
        boolean everything = status != null && "all".equalsIgnoreCase(status.trim());
        List<CaseAssignment> list = CaseAssignment.list("actor.id in ?1 and status in ?2", actors, statuses);
        return list.stream()
                .filter(a -> everything || stillRelevant(a))
                .sorted(Comparator.comparing((CaseAssignment a) -> a.status.ordinal())
                        .thenComparing(a -> -a.caseFile.urgency.ordinal())
                        .thenComparing(a -> a.createdAt, Comparator.reverseOrder()))
                .map(views::assignment)
                .toList();
    }

    /** An offer only matters while the case waits for doers; accepted work only while the case is open. */
    private static boolean stillRelevant(CaseAssignment a) {
        CaseStatus s = a.caseFile.status;
        return switch (a.status) {
            case OFFERED -> s == CaseStatus.MATCHED || s == CaseStatus.IN_PROGRESS;
            case ACCEPTED -> s.isOpen();
            default -> true;
        };
    }

    @POST
    @Path("/assignments/{id}/accept")
    @Transactional
    @Operation(summary = "Accept: the case moves to In progress and residents see who is helping")
    public CaseViews.AssignmentView accept(@RestPath UUID id) {
        AppUser user = current.require();
        return views.assignment(assignments.accept(user, id, current.actorIds(null)));
    }

    @POST
    @Path("/assignments/{id}/decline")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = NoteBody.class)))
    @Operation(summary = "Decline with an optional note {\"note\": \"...\"}; the moderator sees why")
    public CaseViews.AssignmentView decline(@RestPath UUID id, String body) {
        AppUser user = current.require();
        return views.assignment(assignments.decline(user, id, current.actorIds(null), note(body), false));
    }

    @POST
    @Path("/assignments/{id}/redirect")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = NoteBody.class)))
    @Operation(summary = "Redirect: someone else should do this; the note says who")
    public CaseViews.AssignmentView redirect(@RestPath UUID id, String body) {
        AppUser user = current.require();
        return views.assignment(assignments.decline(user, id, current.actorIds(null), note(body), true));
    }

    private String note(String body) {
        NoteBody parsed = optional.read(body, NoteBody.class);
        return parsed == null ? null : parsed.note();
    }

    @PATCH
    @Path("/tasks/{id}")
    @Transactional
    @Operation(summary = "Tick (or untick) a checklist step: {\"done\": true}")
    public CaseViews.TaskView task(@RestPath UUID id, TaskPatch body) {
        if (body == null || body.done() == null) {
            throw Problems.badRequest("Send {\"done\": true} or {\"done\": false}");
        }
        AppUser user = current.require();
        CaseTask t = CaseTask.<CaseTask>findByIdOptional(id).orElseThrow(() -> Problems.notFound("Task not found."));
        UUID communityId = t.caseFile.community.id;
        assignments.setTaskDone(user, id, body.done(), current.actorIds(communityId), current.isModerator(communityId));
        String lang = locales.pick(t.caseFile.community);
        return views.tasks(t.caseFile, lang).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }
}

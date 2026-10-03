package app.needs.service;

import app.needs.ai.TextTools;
import app.needs.model.AppUser;
import app.needs.model.AssignmentStatus;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseStatus;
import app.needs.model.CaseTask;
import app.needs.model.LocalizedText;
import app.needs.model.Visibility;
import app.needs.support.AfterCommit;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import app.needs.support.Problems;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The doer's side: accept or pass on an assignment, tick off checklist steps. */
@ApplicationScoped
public class AssignmentService {

    @Inject
    CaseWorkflow workflow;

    @Inject
    LiveEvents live;

    @Inject
    AfterCommit afterCommit;

    private static CaseAssignment find(UUID id, Set<UUID> userActorIds) {
        CaseAssignment a = CaseAssignment.<CaseAssignment>findByIdOptional(id)
                .orElseThrow(() -> Problems.notFound("Assignment not found."));
        if (!userActorIds.contains(a.actor.id)) {
            throw Problems.forbidden("This assignment belongs to another doer (" + a.actor.name + ").");
        }
        return a;
    }

    @Transactional
    public CaseAssignment accept(AppUser user, UUID id, Set<UUID> userActorIds) {
        CaseAssignment a = find(id, userActorIds);
        if (a.status == AssignmentStatus.ACCEPTED) {
            return a;
        }
        if (a.status != AssignmentStatus.OFFERED) {
            throw Problems.conflict("This assignment is already " + a.status + ".");
        }
        a.status = AssignmentStatus.ACCEPTED;
        a.respondedAt = Instant.now();
        CaseFile c = a.caseFile;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assignmentId", a.id.toString());
        data.put("actorName", a.actor.name);
        data.put("actorKind", a.actor.kind.name());
        workflow.event(c, CaseEventType.ASSIGNMENT_ACCEPTED, user, a.actor, Visibility.PUBLIC, null, data);
        if (c.status == CaseStatus.MATCHED) {
            workflow.changeStatus(c, CaseStatus.IN_PROGRESS, user, null);
        }
        return a;
    }

    /** Declines (we can't) or redirects (someone else should); the moderator sees why and picks another doer. */
    @Transactional
    public CaseAssignment decline(AppUser user, UUID id, Set<UUID> userActorIds, String note, boolean redirect) {
        CaseAssignment a = find(id, userActorIds);
        if (a.status != AssignmentStatus.OFFERED && a.status != AssignmentStatus.ACCEPTED) {
            throw Problems.conflict("This assignment is already " + a.status + ".");
        }
        a.status = redirect ? AssignmentStatus.REDIRECTED : AssignmentStatus.DECLINED;
        a.respondedAt = Instant.now();
        a.note = note == null || note.isBlank() ? null : LocalizedText.of(TextTools.detectLanguage(note), note.trim());
        // Steps this doer owned go back to the pool for whoever the moderator picks next.
        CaseTask.<CaseTask>list("assignment.id", a.id).forEach(t -> t.assignment = null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assignmentId", a.id.toString());
        data.put("actorName", a.actor.name);
        data.put("redirect", redirect);
        if (a.note != null) {
            data.put("note", a.note.original());
        }
        workflow.event(a.caseFile, CaseEventType.ASSIGNMENT_DECLINED, user, a.actor, Visibility.MODERATORS, null, data);
        return a;
    }

    @Transactional
    public CaseTask setTaskDone(AppUser user, UUID taskId, boolean done, Set<UUID> userActorIds, boolean moderator) {
        CaseTask t = CaseTask.<CaseTask>findByIdOptional(taskId).orElseThrow(() -> Problems.notFound("Task not found."));
        CaseFile c = t.caseFile;
        boolean helping = CaseAssignment.<CaseAssignment>list("caseFile.id = ?1 and status in ?2", c.id,
                        List.of(AssignmentStatus.ACCEPTED, AssignmentStatus.DONE))
                .stream()
                .anyMatch(a -> userActorIds.contains(a.actor.id));
        if (!helping && !moderator) {
            throw Problems.forbidden("Only doers who accepted this case, or moderators, can tick its steps.");
        }
        if (!c.status.isOpen()) {
            throw Problems.conflict("This case is closed.");
        }
        t.doneAt = done ? Instant.now() : null;
        t.doneBy = done ? user : null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", t.id.toString());
        data.put("done", done);
        LiveEvent event = new LiveEvent("TASK_UPDATED", c.community.id, c.id, null, Visibility.MODERATORS,
                Set.of(), workflow.assignedActorIds(c.id), data, Instant.now());
        afterCommit.sync(() -> live.publish(event));
        return t;
    }
}

package app.needs.service;

import app.needs.ai.TextTools;
import app.needs.model.Actor;
import app.needs.model.AppUser;
import app.needs.model.AssignmentStatus;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseEvent;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.LocalizedText;
import app.needs.model.MediaAsset;
import app.needs.model.MediaKind;
import app.needs.model.Outcome;
import app.needs.model.ParticipantRole;
import app.needs.model.Visibility;
import app.needs.support.AfterCommit;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import app.needs.support.MediaStorage;
import app.needs.support.MediaStorage.StoredFile;
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
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** What happens on a case after the match: doers post updates and resolve it, residents say whether it helped. */
@ApplicationScoped
public class CaseService {

    @Inject
    CaseWorkflow workflow;

    @Inject
    AfterCommit afterCommit;

    @Inject
    MediaStorage storage;

    @Inject
    PlaybookService playbooks;

    @Inject
    TranslationService translations;

    @Inject
    VolunteerService volunteers;

    @Inject
    LiveEvents live;

    /** "Me too": the resident backs the case without writing a report. Idempotent. */
    @Transactional
    public CaseFile support(AppUser user, UUID caseId) {
        CaseFile c = find(caseId);
        if (!c.status.isOpen()) {
            throw Problems.conflict("This case is closed.");
        }
        if (CaseParticipant.of(caseId, user.id).isEmpty()) {
            workflow.addParticipant(c, user, ParticipantRole.SUPPORTER);
            c.supporterCount++;
            LiveEvent event = new LiveEvent("CASE_SUPPORTED", c.community.id, c.id, null, Visibility.PUBLIC, Set.of(),
                    Set.of(), Map.of("supporterCount", c.supporterCount), Instant.now());
            afterCommit.sync(() -> live.publish(event));
        }
        return c;
    }

    /**
     * "I can help": the resident becomes a volunteer near the case, follows it, and moderators see the offer
     * (they can add the volunteer when they approve the match).
     */
    @Transactional
    public Actor offerHelp(AppUser user, UUID caseId, String note) {
        CaseFile c = find(caseId);
        if (!c.status.isOpen()) {
            throw Problems.conflict("This case is closed.");
        }
        LocalizedText description = note == null || note.isBlank()
                ? c.title : LocalizedText.of(TextTools.detectLanguage(note), note.trim());
        Actor a = volunteers.offer(user, c.community, c.categoryCode, c.lat, c.lng, description);
        workflow.addParticipant(c, user, ParticipantRole.FOLLOWER);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorId", a.id.toString());
        data.put("actorName", a.name);
        if (note != null && !note.isBlank()) {
            data.put("note", note.trim());
        }
        workflow.event(c, CaseEventType.HELP_OFFERED, user, a, Visibility.MODERATORS, null, data);
        return a;
    }

    public static CaseFile find(UUID caseId) {
        return CaseFile.<CaseFile>findByIdOptional(caseId).orElseThrow(() -> Problems.notFound("Case not found."));
    }

    /**
     * A doer's (or moderator's) update for residents. resolve=true marks the case resolved and the doer's part done;
     * residents are then asked whether it helped.
     */
    @Transactional
    public CaseEvent postUpdate(AppUser user, UUID caseId, Set<UUID> userActorIds, boolean moderator,
                                String text, boolean resolve, List<FileUpload> photos) {
        CaseFile c = find(caseId);
        CaseAssignment acting = CaseAssignment.<CaseAssignment>list("caseFile.id = ?1 and status in ?2", c.id,
                        List.of(AssignmentStatus.ACCEPTED, AssignmentStatus.DONE))
                .stream()
                .filter(a -> userActorIds.contains(a.actor.id))
                .findFirst()
                .orElse(null);
        if (acting == null && !moderator) {
            throw Problems.forbidden("Only doers who accepted this case, or moderators, can post updates.");
        }
        if (!c.status.isOpen()) {
            throw Problems.conflict("This case is closed.");
        }
        boolean hasText = text != null && !text.isBlank();
        boolean hasPhotos = photos != null && !photos.isEmpty();
        if (!hasText && !hasPhotos && !resolve) {
            throw Problems.badRequest("Send some text, a photo, or resolve=true.");
        }
        if (resolve && c.status != CaseStatus.MATCHED && c.status != CaseStatus.IN_PROGRESS) {
            throw Problems.conflict("Only a matched or in-progress case can be resolved (this one is " + c.status + ").");
        }

        LocalizedText body = hasText ? LocalizedText.of(TextTools.detectLanguage(text), text.trim()) : null;
        Map<String, Object> data = new LinkedHashMap<>();
        if (resolve) {
            data.put("resolves", true);
        }
        CaseEvent e = workflow.event(c, CaseEventType.UPDATE_POSTED, user, acting == null ? null : acting.actor,
                Visibility.PUBLIC, body, data);
        if (hasPhotos) {
            for (FileUpload photo : photos) {
                StoredFile f = storage.store(photo);
                MediaAsset m = new MediaAsset();
                m.community = c.community;
                m.caseEvent = e;
                m.kind = MediaKind.PHOTO;
                m.storageKey = f.key();
                m.mimeType = f.mimeType();
                m.sizeBytes = f.sizeBytes();
                m.persist();
            }
        }
        if (resolve) {
            if (acting != null) {
                acting.status = AssignmentStatus.DONE;
            }
            workflow.changeStatus(c, CaseStatus.RESOLVED, user, null);
        }
        if (body != null) {
            UUID eventId = e.id;
            afterCommit.async(() -> translations.translateEvent(eventId));
        }
        return e;
    }

    /**
     * "Did this help?" HELPED on a resolved case confirms it; the case then closes and the AI drafts the next
     * playbook version. NOT_HELPED on a resolved case sends it back to the doers.
     */
    @Transactional
    public CaseFile recordOutcome(AppUser user, UUID caseId, Outcome outcome) {
        if (outcome == null || outcome == Outcome.UNCONFIRMED) {
            throw Problems.badRequest("Send {\"outcome\": \"HELPED\"} or {\"outcome\": \"NOT_HELPED\"}.");
        }
        CaseFile c = find(caseId);
        CaseParticipant p = CaseParticipant.of(caseId, user.id)
                .orElseThrow(() -> Problems.forbidden("Only residents linked to this case can say whether it helped."));
        if (c.status != CaseStatus.RESOLVED && c.status != CaseStatus.CONFIRMED && c.status != CaseStatus.CLOSED) {
            throw Problems.conflict("You can answer once the doers mark the case as resolved (it is " + c.status + ").");
        }
        p.outcome = outcome;
        p.outcomeAt = Instant.now();
        workflow.event(c, CaseEventType.OUTCOME_RECORDED, user, null, Visibility.PARTICIPANTS, null,
                Map.of("outcome", outcome.name()));

        if (c.status == CaseStatus.RESOLVED && outcome == Outcome.HELPED) {
            workflow.changeStatus(c, CaseStatus.CONFIRMED, user, null);
            UUID id = c.id;
            afterCommit.async(() -> playbooks.closeAndDraft(id));
        } else if (c.status == CaseStatus.RESOLVED && outcome == Outcome.NOT_HELPED) {
            c.resolvedAt = null;
            CaseAssignment.<CaseAssignment>list("caseFile.id = ?1 and status = ?2", c.id, AssignmentStatus.DONE)
                    .forEach(a -> a.status = AssignmentStatus.ACCEPTED);
            workflow.changeStatus(c, CaseStatus.IN_PROGRESS, user, "REOPENED");
        }
        return c;
    }
}

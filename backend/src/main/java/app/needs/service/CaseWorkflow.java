package app.needs.service;

import app.needs.model.Actor;
import app.needs.model.AppUser;
import app.needs.model.AssignmentStatus;
import app.needs.model.CaseEvent;
import app.needs.model.CaseEventType;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.LocalizedText;
import app.needs.model.ParticipantRole;
import app.needs.model.Visibility;
import app.needs.support.AfterCommit;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only place that changes a case's status or writes its timeline, so every change
 * is recorded as a case_event and announced on the live stream after commit.
 * Call it inside a transaction.
 */
@ApplicationScoped
public class CaseWorkflow {

    @Inject
    EntityManager em;

    @Inject
    LiveEvents live;

    @Inject
    AfterCommit afterCommit;

    public void changeStatus(CaseFile c, CaseStatus to, AppUser by, String reason) {
        CaseStatus from = c.status;
        if (from == to) {
            return;
        }
        c.status = to;
        if (to == CaseStatus.RESOLVED) {
            c.resolvedAt = Instant.now();
        }
        if (to == CaseStatus.CLOSED) {
            c.closedAt = Instant.now();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from.name());
        data.put("to", to.name());
        if (reason != null) {
            data.put("reason", reason);
        }
        event(c, CaseEventType.STATUS_CHANGED, by, null, Visibility.PUBLIC, null, data);
    }

    public CaseEvent event(CaseFile c, CaseEventType type, AppUser by, Actor actor, Visibility visibility,
                           LocalizedText body, Map<String, Object> data) {
        CaseEvent e = new CaseEvent();
        e.caseFile = c;
        e.type = type;
        e.authorUser = by;
        e.actor = actor;
        e.visibility = visibility;
        e.body = body;
        e.data = data == null ? Map.of() : data;
        e.persist();

        Map<String, Object> liveData = new LinkedHashMap<>(e.data);
        liveData.put("eventId", e.id.toString());
        liveData.put("status", c.status.name());
        boolean moderatorsOnly = visibility == Visibility.MODERATORS;
        LiveEvent le = new LiveEvent(type.name(), c.community.id, c.id, null, visibility,
                moderatorsOnly ? Set.of() : participantIds(c.id),
                moderatorsOnly ? Set.of() : assignedActorIds(c.id),
                liveData, e.createdAt);
        afterCommit.sync(() -> live.publish(le));
        return e;
    }

    public CaseParticipant addParticipant(CaseFile c, AppUser user, ParticipantRole role) {
        return CaseParticipant.of(c.id, user.id).orElseGet(() -> {
            CaseParticipant p = new CaseParticipant();
            p.caseFile = c;
            p.user = user;
            p.role = role;
            p.persist();
            return p;
        });
    }

    public Set<UUID> participantIds(UUID caseId) {
        return new HashSet<>(em.createQuery(
                        "select p.user.id from CaseParticipant p where p.caseFile.id = :id", UUID.class)
                .setParameter("id", caseId)
                .getResultList());
    }

    public Set<UUID> assignedActorIds(UUID caseId) {
        return new HashSet<>(em.createQuery(
                        "select a.actor.id from CaseAssignment a where a.caseFile.id = :id and a.status in :statuses", UUID.class)
                .setParameter("id", caseId)
                .setParameter("statuses", List.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED, AssignmentStatus.DONE))
                .getResultList());
    }

    public int nextCaseNumber(UUID communityId) {
        Integer max = em.createQuery("select max(c.number) from CaseFile c where c.community.id = :id", Integer.class)
                .setParameter("id", communityId)
                .getSingleResult();
        return (max == null ? 400 : max) + 1;
    }
}

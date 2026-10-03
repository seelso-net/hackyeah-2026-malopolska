package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.AssignmentStatus;
import app.needs.model.CaseAssignment;
import app.needs.model.CaseEvent;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.Community;
import app.needs.model.Membership;
import app.needs.model.Role;
import app.needs.model.Visibility;
import app.needs.support.CurrentUser;
import app.needs.support.Problems;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may see and do what on a case. Moderators see everything in their community; residents linked to a
 * case and doers assigned to it see its participant-only events; everyone else sees public cases only.
 */
@ApplicationScoped
public class Access {

    private static final List<AssignmentStatus> ACTIVE =
            List.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED, AssignmentStatus.DONE);

    @Inject
    CurrentUser current;

    public boolean moderator(CaseFile c) {
        return current.find().isPresent() && current.isModerator(c.community.id);
    }

    public Optional<CaseParticipant> participation(CaseFile c) {
        return current.find().flatMap(u -> CaseParticipant.of(c.id, u.id));
    }

    public Set<UUID> actorIds(CaseFile c) {
        return current.find().isPresent() ? current.actorIds(c.community.id) : Set.of();
    }

    /** The caller's organisation's (or volunteer profile's) live assignment on this case, if any. */
    public Optional<CaseAssignment> myAssignment(CaseFile c) {
        Set<UUID> mine = actorIds(c);
        if (mine.isEmpty()) {
            return Optional.empty();
        }
        return CaseAssignment.<CaseAssignment>list("caseFile.id = ?1 and status in ?2", c.id, ACTIVE)
                .stream()
                .filter(a -> mine.contains(a.actor.id))
                .findFirst();
    }

    /** Moderators, linked residents and assigned doers. */
    public boolean involved(CaseFile c) {
        return moderator(c) || participation(c).isPresent() || myAssignment(c).isPresent();
    }

    public boolean canSee(CaseFile c) {
        if (c.visibility == Visibility.PUBLIC && canBrowse(c.community)) {
            return true;
        }
        return involved(c);
    }

    public CaseFile requireSee(CaseFile c) {
        if (!canSee(c)) {
            current.require();
            throw Problems.forbidden("This case is not visible to you.");
        }
        return c;
    }

    /** Public communities are open to anyone; private ones (e.g. a housing co-op) to members only. */
    public boolean canBrowse(Community community) {
        return community.cfg().rules().mapIsPublic() || (current.find().isPresent() && current.isMember(community.id));
    }

    public boolean canSeeEvent(CaseEvent e, boolean moderator, boolean involved) {
        return switch (e.visibility) {
            case PUBLIC -> true;
            case PARTICIPANTS -> involved;
            case MODERATORS -> moderator;
        };
    }

    /** Resident sign-up by doing: the first report in a community makes you a resident there. */
    public void ensureResident(AppUser user, Community community) {
        if (!current.isMember(community.id)) {
            Membership m = new Membership();
            m.user = user;
            m.community = community;
            m.role = Role.RESIDENT;
            m.persist();
        }
    }
}

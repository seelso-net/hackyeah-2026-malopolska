package app.needs.support;

import app.needs.model.AppUser;
import app.needs.model.Membership;
import app.needs.model.Role;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is calling. Demo login: the X-Demo-User header (or ?as= for EventSource, which cannot send headers)
 * holds an auth_subject such as "maria". Swap this class for quarkus-oidc before real residents use the app.
 */
@RequestScoped
public class CurrentUser {

    @Inject
    HttpServerRequest request;

    private AppUser user;
    private List<Membership> memberships;
    private boolean looked;

    public Optional<AppUser> find() {
        if (!looked) {
            looked = true;
            String subject = request.getHeader("X-Demo-User");
            if (subject == null || subject.isBlank()) {
                subject = request.getParam("as");
            }
            if (subject != null && !subject.isBlank()) {
                user = AppUser.bySubject(subject.trim()).orElse(null);
            }
        }
        return Optional.ofNullable(user);
    }

    public AppUser require() {
        return find().orElseThrow(() -> Problems.unauthorized(
                "Sign in: send the header X-Demo-User with a seeded user, e.g. maria, ewa, kasia, jan or piotr."));
    }

    public List<Membership> memberships() {
        if (memberships == null) {
            memberships = find().map(u -> Membership.<Membership>list("user.id", u.id)).orElse(List.of());
        }
        return memberships;
    }

    public boolean hasRole(UUID communityId, Role role) {
        return memberships().stream().anyMatch(m -> m.community.id.equals(communityId) && m.role == role);
    }

    public boolean isMember(UUID communityId) {
        return memberships().stream().anyMatch(m -> m.community.id.equals(communityId));
    }

    /** Admins can do everything moderators can. */
    public boolean isModerator(UUID communityId) {
        return hasRole(communityId, Role.MODERATOR) || hasRole(communityId, Role.ADMIN);
    }

    public void requireModerator(UUID communityId) {
        require();
        if (!isModerator(communityId)) {
            throw Problems.forbidden("Only moderators of this community can do that.");
        }
    }

    public void requireAdmin(UUID communityId) {
        require();
        if (!hasRole(communityId, Role.ADMIN)) {
            throw Problems.forbidden("Only admins of this community can do that.");
        }
    }

    /** Actors (organisations or the volunteer profile) this user acts for in a community. */
    public Set<UUID> actorIds(UUID communityId) {
        Set<UUID> ids = new HashSet<>();
        memberships().stream()
                .filter(m -> m.role == Role.DOER && m.actor != null && (communityId == null || m.community.id.equals(communityId)))
                .forEach(m -> ids.add(m.actor.id));
        return ids;
    }

    /** Everything the live stream needs to filter events, captured once per connection. */
    public Audience audience() {
        AppUser u = require();
        Set<UUID> communities = new HashSet<>();
        Set<UUID> moderated = new HashSet<>();
        memberships().forEach(m -> {
            communities.add(m.community.id);
            if (m.role == Role.MODERATOR || m.role == Role.ADMIN) {
                moderated.add(m.community.id);
            }
        });
        return new Audience(u.id, communities, moderated, actorIds(null));
    }

    public record Audience(UUID userId, Set<UUID> communities, Set<UUID> moderated, Set<UUID> actors) {
    }
}

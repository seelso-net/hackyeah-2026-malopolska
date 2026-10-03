package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.Membership;
import app.needs.support.CurrentUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/me")
@Tag(name = "Residents")
public class MeResource {

    @Inject
    CurrentUser current;

    public record MembershipView(String community, String communityName, String role, UUID actorId, String actorName) {
    }

    public record MeView(UUID id, String login, String displayName, String locale, List<MembershipView> memberships) {
    }

    @GET
    @Operation(summary = "The signed-in user and their roles in each community (demo login: X-Demo-User header)")
    public MeView me() {
        AppUser u = current.require();
        String lang = u.locale == null ? "en" : u.locale;
        List<Membership> memberships = current.memberships();
        return new MeView(u.id, u.authSubject, u.displayName, u.locale, memberships.stream()
                .map(m -> new MembershipView(m.community.slug, m.community.name.in(lang), m.role.name(),
                        m.actor == null ? null : m.actor.id, m.actor == null ? null : m.actor.name))
                .toList());
    }
}

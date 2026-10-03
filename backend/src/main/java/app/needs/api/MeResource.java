package app.needs.api;

import app.needs.model.AppUser;
import app.needs.model.CaseFile;
import app.needs.model.CaseParticipant;
import app.needs.model.CaseStatus;
import app.needs.model.Membership;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/me")
@Tag(name = "Residents")
public class MeResource {

    @Inject
    CurrentUser current;

    @Inject
    CaseViews views;

    @Inject
    Locales locales;

    public record MembershipView(String community, String communityName, String role, UUID actorId, String actorName) {
    }

    /** A case the caller reported, backs or follows, and where they stand on it. */
    public record MyCase(@JsonUnwrapped CaseViews.CaseCard card, String role, String outcome, boolean canAnswerOutcome,
                         Instant joinedAt) {
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

    @GET
    @Path("/cases")
    @Operation(summary = "Cases the caller reported, backs or follows, newest first")
    public List<MyCase> cases() {
        AppUser u = current.require();
        List<CaseParticipant> mine = CaseParticipant.list("user.id = ?1 order by joinedAt desc", u.id);
        return mine.stream().map(p -> {
            CaseFile c = p.caseFile;
            String lang = locales.pick(c.community);
            boolean answerable = c.status == CaseStatus.RESOLVED || c.status == CaseStatus.CONFIRMED;
            return new MyCase(views.card(c, lang, true), p.role.name(), p.outcome == null ? null : p.outcome.name(),
                    p.outcome == null && answerable, p.joinedAt);
        }).toList();
    }
}

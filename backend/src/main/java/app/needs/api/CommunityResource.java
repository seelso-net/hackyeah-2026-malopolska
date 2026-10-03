package app.needs.api;

import app.needs.model.CaseStatus;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.model.Membership;
import app.needs.support.CurrentUser;
import app.needs.support.Locales;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/communities")
@Tag(name = "Residents")
public class CommunityResource {

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    public record CommunityItem(String slug, String kind, String name) {
    }

    public record CategoryView(String code, String label) {
    }

    /** Every status in the fixed lifecycle, with this community's label; hidden ones borrow the previous label. */
    public record StatusView(String status, String label, boolean visible) {
    }

    public record RoleView(String role, String who) {
    }

    public record RulesView(int mergeRadiusMeters, int publicLocationRoundingMeters, int autoCloseDays, boolean publicMap) {
    }

    public record MyPlace(List<String> roles, List<CaseViews.ActorRef> actors) {
    }

    public record CommunityView(UUID id, String slug, String kind, String name, String lang, String defaultLocale,
                                List<String> locales, List<CategoryView> categories, List<StatusView> statuses,
                                List<RoleView> roles, RulesView rules, MyPlace me) {
    }

    @GET
    @Operation(summary = "Communities on this deployment (city districts, campuses, housing co-ops)")
    public List<CommunityItem> list() {
        String lang = current.find().map(u -> u.locale).orElse("en");
        List<Community> all = Community.list("order by slug");
        return all.stream().map(c -> new CommunityItem(c.slug, c.kind.name(), c.name.in(lang))).toList();
    }

    @GET
    @Path("/{slug}")
    @Operation(summary = "Configuration for the UI: categories, status labels and languages, in the reader's language")
    public CommunityView get(@RestPath String slug) {
        Community c = Community.bySlug(slug).orElseThrow(() -> Problems.notFound("No community " + slug));
        String lang = locales.pick(c);
        CommunityConfig cfg = c.cfg();
        List<CategoryView> categories = cfg.categories().stream()
                .map(cat -> new CategoryView(cat.code(), cat.label(lang)))
                .toList();
        List<StatusView> statuses = Arrays.stream(CaseStatus.values())
                .map(s -> new StatusView(s.name(), CaseViews.statusLabel(cfg, s, lang),
                        cfg.workflow().stream().anyMatch(w -> w.status() == s)))
                .toList();
        List<RoleView> roles = cfg.roles().stream()
                .map(r -> new RoleView(r.role().name(), CommunityConfig.Labels.pick(r.who(), lang, null)))
                .toList();
        CommunityConfig.Rules r = cfg.rules();
        MyPlace me = null;
        if (current.find().isPresent()) {
            List<Membership> mine = current.memberships().stream().filter(m -> m.community.id.equals(c.id)).toList();
            me = new MyPlace(mine.stream().map(m -> m.role.name()).distinct().toList(),
                    mine.stream().filter(m -> m.actor != null)
                            .map(m -> new CaseViews.ActorRef(m.actor.id, m.actor.name, m.actor.kind.name()))
                            .toList());
        }
        return new CommunityView(c.id, c.slug, c.kind.name(), c.name.in(lang), lang, c.defaultLocale, c.localeList(),
                categories, statuses, roles,
                new RulesView(r.mergeRadius(), r.locationRounding(), r.autoClose(), r.mapIsPublic()), me);
    }
}

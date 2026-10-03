package app.needs.api;

import app.needs.model.CaseFile;
import app.needs.model.CaseStatus;
import app.needs.model.Community;
import app.needs.model.Initiative;
import app.needs.model.Visibility;
import app.needs.support.CurrentUser;
import app.needs.support.Geo;
import app.needs.support.Locales;
import app.needs.support.Problems;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/api/map")
@Tag(name = "Residents")
public class MapResource {

    @Inject
    CurrentUser current;

    @Inject
    Locales locales;

    @Inject
    Access access;

    @Inject
    CaseViews views;

    public record MapInitiative(UUID id, TextDto title, TextDto description, TextDto schedule, List<String> categoryCodes,
                                Double lat, Double lng, String actorName) {
    }

    public record MapView(CaseViews.CommunityRef community, String lang, List<CaseViews.CaseCard> cases,
                          List<MapInitiative> initiatives) {
    }

    @GET
    @Operation(summary = "Home map: public cases with locations rounded to the community's grid, plus initiatives in view")
    public MapView map(@RestQuery String community, @RestQuery String bbox,
                       @RestQuery @DefaultValue("cases,initiatives") String layers) {
        if (community == null || community.isBlank()) {
            throw Problems.badRequest("Add ?community=<slug>, e.g. ?community=riverside");
        }
        Community c = Community.bySlug(community).orElseThrow(() -> Problems.notFound("No community " + community));
        if (!access.canBrowse(c)) {
            current.require();
            throw Problems.forbidden("This community's map is for members only.");
        }
        String lang = locales.pick(c);
        double[] box = Geo.parseBbox(bbox);
        Set<String> wanted = Arrays.stream(layers.split(",")).map(String::trim).collect(Collectors.toSet());
        boolean moderator = current.find().isPresent() && current.isModerator(c.id);

        List<CaseViews.CaseCard> cases = List.of();
        if (wanted.contains("cases")) {
            List<CaseFile> list = CaseFile.list("community.id = ?1 and status <> ?2 order by updatedAt desc",
                    c.id, CaseStatus.REJECTED);
            cases = list.stream()
                    .filter(cf -> cf.visibility == Visibility.PUBLIC || moderator)
                    .map(cf -> views.card(cf, lang, false))
                    .filter(card -> Geo.inside(box, card.lat(), card.lng()))
                    .toList();
        }
        List<MapInitiative> initiatives = List.of();
        if (wanted.contains("initiatives")) {
            List<Initiative> list = Initiative.list("community.id = ?1 and active = true", c.id);
            initiatives = list.stream()
                    .filter(i -> Geo.inside(box, i.lat, i.lng))
                    .map(i -> new MapInitiative(i.id, TextDto.of(i.title, lang), TextDto.of(i.description, lang),
                            TextDto.of(i.schedule, lang), Arrays.asList(i.categoryCodes), i.lat, i.lng,
                            i.actor == null ? null : i.actor.name))
                    .toList();
        }
        return new MapView(views.communityRef(c, lang), lang, cases, initiatives);
    }
}

package app.needs.api;

import app.needs.model.CaseStatus;
import app.needs.model.Community;
import app.needs.model.CommunityConfig;
import app.needs.support.CurrentUser;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import app.needs.support.AfterCommit;
import app.needs.support.Problems;
import app.needs.model.Visibility;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

/** One JSON document makes a deployment a city district, a campus or a housing co-op. */
@Path("/api/admin")
@Tag(name = "Playbooks and admin")
public class AdminResource {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_-]{0,39}");

    @Inject
    CurrentUser current;

    @Inject
    LiveEvents live;

    @Inject
    AfterCommit afterCommit;

    @GET
    @Path("/communities/{ref}/config")
    @Operation(summary = "Export the community's whole configuration: categories, workflow labels, roles and rules")
    public CommunityConfig get(@RestPath String ref) {
        Community c = find(ref);
        current.requireAdmin(c.id);
        return c.cfg();
    }

    @PUT
    @Path("/communities/{ref}/config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Import a configuration: the UI picks up new categories and status names immediately")
    public CommunityConfig put(@RestPath String ref, CommunityConfig config) {
        Community c = find(ref);
        current.requireAdmin(c.id);
        validate(config);
        c.config = config;
        LiveEvent event = new LiveEvent("CONFIG_CHANGED", c.id, null, null, Visibility.PUBLIC, Set.of(), Set.of(),
                Map.of("community", c.slug), Instant.now());
        afterCommit.sync(() -> live.publish(event));
        return config;
    }

    private static Community find(String ref) {
        Community c;
        try {
            c = Community.findById(UUID.fromString(ref));
        } catch (IllegalArgumentException notUuid) {
            c = Community.bySlug(ref).orElse(null);
        }
        if (c == null) {
            throw Problems.notFound("No community " + ref);
        }
        return c;
    }

    private static void validate(CommunityConfig config) {
        if (config == null) {
            throw Problems.badRequest("Send the configuration JSON (GET it first, edit, PUT it back).");
        }
        if (config.categories().isEmpty()) {
            throw Problems.badRequest("Add at least one category.");
        }
        Set<String> codes = new HashSet<>();
        for (CommunityConfig.Category cat : config.categories()) {
            if (cat.code() == null || !CODE.matcher(cat.code()).matches()) {
                throw Problems.badRequest("Category codes are short lowercase English words, e.g. seniors (got "
                        + cat.code() + ").");
            }
            if (!codes.add(cat.code())) {
                throw Problems.badRequest("Category " + cat.code() + " appears twice.");
            }
            if (cat.labels() == null || cat.labels().isEmpty()) {
                throw Problems.badRequest("Category " + cat.code() + " needs at least one label.");
            }
        }
        Set<CaseStatus> statuses = new HashSet<>();
        for (CommunityConfig.WorkflowState w : config.workflow()) {
            if (w.status() == null) {
                throw Problems.badRequest("Each workflow entry needs a status from the fixed lifecycle: NEW ... CLOSED.");
            }
            if (!statuses.add(w.status())) {
                throw Problems.badRequest("Status " + w.status() + " appears twice in the workflow.");
            }
        }
        CommunityConfig.Rules r = config.rules();
        if (r.mergeRadius() < 0 || r.locationRounding() < 0 || r.autoClose() < 1) {
            throw Problems.badRequest("Rules must be positive numbers.");
        }
    }
}

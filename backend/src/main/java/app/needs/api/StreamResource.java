package app.needs.api;

import app.needs.support.CurrentUser;
import app.needs.support.CurrentUser.Audience;
import app.needs.support.LiveEvents;
import app.needs.support.LiveEvents.LiveEvent;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/stream")
@Tag(name = "Residents")
public class StreamResource {

    @Inject
    CurrentUser current;

    @Inject
    LiveEvents live;

    /** What a browser receives: codes and ids only; the UI writes the sentence in its own language. */
    public record StreamEvent(String type, UUID communityId, UUID caseId, UUID reportId, Map<String, Object> data,
                              Instant at) {
        static StreamEvent of(LiveEvent e) {
            return new StreamEvent(e.type(), e.communityId(), e.caseId(), e.reportId(), e.data(), e.at());
        }
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking
    @Operation(summary = "Server-sent events for everything the caller may see. EventSource cannot send headers, "
            + "so pass the demo login as ?as=maria")
    public Multi<StreamEvent> stream() {
        Audience audience = current.audience();
        Multi<StreamEvent> events = live.stream()
                .filter(e -> e.visibleTo(audience))
                .map(StreamEvent::of);
        Multi<StreamEvent> heartbeat = Multi.createFrom().ticks().every(Duration.ofSeconds(20))
                .map(t -> new StreamEvent("HEARTBEAT", null, null, null, Map.of(), Instant.now()));
        Multi<StreamEvent> hello = Multi.createFrom().item(
                new StreamEvent("CONNECTED", null, null, null, Map.of("userId", audience.userId().toString()), Instant.now()));
        return Multi.createBy().concatenating().streams(hello, Multi.createBy().merging().streams(events, heartbeat));
    }
}

package app.needs.support;

import app.needs.model.Visibility;
import app.needs.support.CurrentUser.Audience;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-process hub behind GET /api/stream. One instance is enough for the demo; use Redis or Postgres NOTIFY to scale out. */
@ApplicationScoped
public class LiveEvents {

    private final BroadcastProcessor<LiveEvent> processor = BroadcastProcessor.create();

    public synchronized void publish(LiveEvent event) {
        processor.onNext(event);
    }

    public Multi<LiveEvent> stream() {
        return processor;
    }

    /**
     * One live event. userIds and actorIds name who is involved; visibility decides who else may see it.
     * data holds codes and ids only; the UI writes the sentence in the reader's language.
     */
    public record LiveEvent(String type, UUID communityId, UUID caseId, UUID reportId, Visibility visibility,
                            Set<UUID> userIds, Set<UUID> actorIds, Map<String, Object> data, Instant at) {

        public static LiveEvent toUser(String type, UUID communityId, UUID caseId, UUID reportId, UUID userId,
                                       Map<String, Object> data) {
            return new LiveEvent(type, communityId, caseId, reportId, null, Set.of(userId), Set.of(), data, Instant.now());
        }

        public static LiveEvent toModerators(String type, UUID communityId, UUID caseId, Map<String, Object> data) {
            return new LiveEvent(type, communityId, caseId, null, Visibility.MODERATORS, Set.of(), Set.of(), data, Instant.now());
        }

        public boolean visibleTo(Audience a) {
            if (userIds.contains(a.userId())) {
                return true;
            }
            if (a.moderated().contains(communityId)) {
                return true;
            }
            if (actorIds.stream().anyMatch(a.actors()::contains)) {
                return true;
            }
            return visibility == Visibility.PUBLIC && a.communities().contains(communityId);
        }
    }
}

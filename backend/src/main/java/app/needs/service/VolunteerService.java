package app.needs.service;

import app.needs.model.Actor;
import app.needs.model.ActorKind;
import app.needs.model.AppUser;
import app.needs.model.Community;
import app.needs.model.LocalizedText;
import app.needs.model.Membership;
import app.needs.model.Role;
import app.needs.support.AfterCommit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** "I can help": a resident becomes (or updates) a volunteer doer that the matcher can propose. */
@ApplicationScoped
public class VolunteerService {

    @Inject
    AfterCommit afterCommit;

    @Inject
    EmbeddingService embeddings;

    /** Call inside a transaction. The volunteer's vector is recomputed after commit. */
    public Actor offer(AppUser user, Community community, String category, Double lat, Double lng,
                       LocalizedText description) {
        Actor a = Actor.find("user.id = ?1 and community.id = ?2", user.id, community.id).firstResult();
        if (a == null) {
            a = new Actor();
            a.community = community;
            a.kind = ActorKind.VOLUNTEER;
            a.name = user.displayName;
            a.user = user;
            a.capabilities = category == null ? new String[0] : new String[] {category};
            a.serviceLat = lat;
            a.serviceLng = lng;
            a.serviceRadiusM = 1000;
            a.description = description;
            a.persist();
            // One doer role per community: an organisation's staff keep acting for their organisation.
            boolean isDoer = Membership.count("user.id = ?1 and community.id = ?2 and role = ?3",
                    user.id, community.id, Role.DOER) > 0;
            if (!isDoer) {
                Membership m = new Membership();
                m.user = user;
                m.community = community;
                m.role = Role.DOER;
                m.actor = a;
                m.persist();
            }
        } else {
            List<String> caps = new ArrayList<>(Arrays.asList(a.capabilities));
            if (category != null && !caps.contains(category)) {
                caps.add(category);
            }
            a.capabilities = caps.toArray(String[]::new);
            if (description != null) {
                a.description = description;
            }
            a.active = true;
            a.embedding = null;
        }
        UUID actorId = a.id;
        afterCommit.async(() -> embeddings.refreshActor(actorId));
        return a;
    }
}

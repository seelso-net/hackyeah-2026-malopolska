package app.needs.service;

import app.needs.ai.TextTools;
import app.needs.model.Embeddings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Similarity search with pgvector: cosine distance (<=>) in plain SQL, so it can be combined with
 * community, status and category filters. Backed by the HNSW indexes in V1__schema.sql.
 */
@ApplicationScoped
public class VectorSearch {

    @Inject
    EntityManager em;

    public record CaseHit(UUID id, double similarity, Integer distanceMeters) {
    }

    public record PlaybookHit(UUID playbookId, UUID versionId, double similarity, boolean categoryMatch) {
    }

    public record ActorHit(UUID actorId, double similarity, boolean categoryMatch, Integer distanceMeters, Integer serviceRadiusMeters) {
    }

    /** Open cases of a community, closest in meaning first, with their distance from the given point. */
    @SuppressWarnings("unchecked")
    public List<CaseHit> openCases(UUID communityId, float[] query, Double lat, Double lng, int limit) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT c.id, 1 - (c.embedding <=> CAST(:q AS vector)) AS similarity, c.lat, c.lng
                        FROM case_file c
                        WHERE c.community_id = :community
                          AND c.embedding IS NOT NULL
                          AND c.status NOT IN ('CLOSED', 'REJECTED')
                        ORDER BY c.embedding <=> CAST(:q AS vector)
                        LIMIT :limit
                        """)
                .setParameter("q", Embeddings.literal(query))
                .setParameter("community", communityId)
                .setParameter("limit", limit)
                .getResultList();
        List<CaseHit> hits = new ArrayList<>();
        for (Object[] r : rows) {
            hits.add(new CaseHit((UUID) r[0], num(r[1]),
                    TextTools.distanceMeters(lat, lng, (Double) r[2], (Double) r[3])));
        }
        return hits;
    }

    /** Published playbooks this community can use (its own or shared), closest in meaning first. */
    @SuppressWarnings("unchecked")
    public List<PlaybookHit> playbooks(UUID communityId, float[] query, String categoryCode, int limit) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT p.id, p.current_version_id, 1 - (p.embedding <=> CAST(:q AS vector)) AS similarity,
                               (CAST(:category AS text) = ANY (p.category_codes)) AS category_match
                        FROM playbook p
                        WHERE p.embedding IS NOT NULL
                          AND p.status = 'PUBLISHED'
                          AND p.current_version_id IS NOT NULL
                          AND (p.shared OR p.origin_community_id = :community)
                        ORDER BY p.embedding <=> CAST(:q AS vector)
                        LIMIT :limit
                        """)
                .setParameter("q", Embeddings.literal(query))
                .setParameter("category", categoryCode == null ? "" : categoryCode)
                .setParameter("community", communityId)
                .setParameter("limit", limit)
                .getResultList();
        List<PlaybookHit> hits = new ArrayList<>();
        for (Object[] r : rows) {
            hits.add(new PlaybookHit((UUID) r[0], (UUID) r[1], num(r[2]), Boolean.TRUE.equals(r[3])));
        }
        return hits;
    }

    /** Active doers of a community; those with the case's category first, then closest in meaning. */
    @SuppressWarnings("unchecked")
    public List<ActorHit> actors(UUID communityId, float[] query, String categoryCode, Double lat, Double lng, int limit) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT a.id, 1 - (a.embedding <=> CAST(:q AS vector)) AS similarity,
                               (CAST(:category AS text) = ANY (a.capabilities)) AS category_match,
                               a.service_lat, a.service_lng, a.service_radius_m
                        FROM actor a
                        WHERE a.community_id = :community
                          AND a.active
                          AND a.embedding IS NOT NULL
                        ORDER BY category_match DESC, a.embedding <=> CAST(:q AS vector)
                        LIMIT :limit
                        """)
                .setParameter("q", Embeddings.literal(query))
                .setParameter("category", categoryCode == null ? "" : categoryCode)
                .setParameter("community", communityId)
                .setParameter("limit", limit)
                .getResultList();
        List<ActorHit> hits = new ArrayList<>();
        for (Object[] r : rows) {
            hits.add(new ActorHit((UUID) r[0], num(r[1]), Boolean.TRUE.equals(r[2]),
                    TextTools.distanceMeters(lat, lng, (Double) r[3], (Double) r[4]),
                    r[5] == null ? null : ((Number) r[5]).intValue()));
        }
        return hits;
    }

    private static double num(Object o) {
        return o == null ? 0 : Math.round(((Number) o).doubleValue() * 1000) / 1000.0;
    }
}

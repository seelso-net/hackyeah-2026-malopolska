package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One doer the AI proposed. selected mirrors the moderator's checkboxes. */
@Entity
public class MatchCandidate extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public MatchSuggestion suggestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Actor actor;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText reason;

    public Double score;

    public boolean selected = true;

    public Instant createdAt = Instant.now();
}

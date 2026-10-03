package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only timeline entry. Feeds the case screen, the live stream and playbook drafts.
 * Only UPDATE_POSTED carries human text (body); other types carry codes in data and the UI writes the sentence.
 */
@Entity
public class CaseEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    public CaseEventType type;

    @ManyToOne(fetch = FetchType.LAZY)
    public AppUser authorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    public Actor actor;

    @Enumerated(EnumType.STRING)
    public Visibility visibility = Visibility.PUBLIC;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText body;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> data;

    public Instant createdAt = Instant.now();
}

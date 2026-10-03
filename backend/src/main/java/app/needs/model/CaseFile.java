package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One real problem or idea that reports join. Named case_file because "case" is reserved in SQL and HQL.
 */
@Entity
public class CaseFile extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Community community;

    /** Per-community number shown to people, e.g. R-0412. */
    public int number;

    /** NEED or IDEA. */
    @Enumerated(EnumType.STRING)
    public ReportKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText title;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText summary;

    /** A code from community.config, e.g. "seniors". */
    public String categoryCode;

    @Enumerated(EnumType.STRING)
    public Urgency urgency = Urgency.MEDIUM;

    @Enumerated(EnumType.STRING)
    public CaseStatus status = CaseStatus.NEW;

    @Enumerated(EnumType.STRING)
    public Visibility visibility = Visibility.PUBLIC;

    public Double lat;

    public Double lng;

    public String areaLabel;

    public int reportCount;

    public int supporterCount;

    /** The exact playbook version this case used. */
    @ManyToOne(fetch = FetchType.LAZY)
    public PlaybookVersion playbookVersion;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = Embeddings.DIMENSIONS)
    public float[] embedding;

    public Instant createdAt = Instant.now();

    public Instant updatedAt = Instant.now();

    public Instant resolvedAt;

    public Instant closedAt;

    /** Moderators, doers and the AI pipeline all update cases. */
    @Version
    public long version;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String displayNumber() {
        return "R-" + String.format("%04d", number);
    }
}

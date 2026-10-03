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
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** What one resident sent. The AI never rewrites the body; its reading goes into aiResult. */
@Entity
public class Report extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    public AppUser author;

    /** Set once the report joins or opens a case. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    public ReportKind kind;

    @Enumerated(EnumType.STRING)
    public InputMode inputMode;

    @Enumerated(EnumType.STRING)
    public ReportStatus status = ReportStatus.RECEIVED;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText body;

    public Double lat;

    public Double lng;

    public String addressLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    public AiResult aiResult;

    public boolean anonymous;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = Embeddings.DIMENSIONS)
    public float[] embedding;

    public Instant createdAt = Instant.now();
}

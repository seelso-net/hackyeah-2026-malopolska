package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A proven solution. Shared playbooks are what let one district's fix reach another. */
@Entity
public class Playbook extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    public Community originCommunity;

    public String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText title;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText problem;

    @JdbcTypeCode(SqlTypes.ARRAY)
    public String[] categoryCodes = new String[0];

    @Enumerated(EnumType.STRING)
    public PlaybookStatus status = PlaybookStatus.DRAFT;

    /** Visible to other communities. */
    public boolean shared = true;

    @ManyToOne(fetch = FetchType.LAZY)
    public PlaybookVersion currentVersion;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = Embeddings.DIMENSIONS)
    public float[] embedding;

    public Instant createdAt = Instant.now();
}

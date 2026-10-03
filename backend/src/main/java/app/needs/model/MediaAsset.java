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

/** A photo or voice file. The file lives in object storage (local disk in dev); the row keeps its key. */
@Entity
public class MediaAsset extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    public Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    public CaseEvent caseEvent;

    @Enumerated(EnumType.STRING)
    public MediaKind kind;

    public String storageKey;

    public String mimeType;

    public long sizeBytes;

    public Integer durationMs;

    public Instant createdAt = Instant.now();
}

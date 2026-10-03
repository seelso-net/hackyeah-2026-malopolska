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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One version of a playbook's steps. Cases point at the exact version they used. */
@Entity
public class PlaybookVersion extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Playbook playbook;

    public int number;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<PlaybookStep> steps = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public PlaybookEffort effort;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText changeNotes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    public UUID[] sourceCaseIds = new UUID[0];

    @Enumerated(EnumType.STRING)
    public DraftedBy draftedBy = DraftedBy.PERSON;

    @Enumerated(EnumType.STRING)
    public PlaybookStatus status = PlaybookStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    public AppUser publishedBy;

    public Instant publishedAt;

    public Instant createdAt = Instant.now();
}

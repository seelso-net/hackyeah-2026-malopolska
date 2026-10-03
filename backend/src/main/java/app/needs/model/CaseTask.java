package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A checklist step copied from the playbook when the match is approved. */
@Entity
public class CaseTask extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    /** The doer who owns the step; null when no assigned doer fits its roles. */
    @ManyToOne(fetch = FetchType.LAZY)
    public CaseAssignment assignment;

    public int position;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText title;

    public Instant doneAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by")
    public AppUser doneBy;

    public Instant createdAt = Instant.now();
}

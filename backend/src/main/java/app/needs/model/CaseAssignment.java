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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One doer's part in a case. The doer inbox lists these. */
@Entity
public class CaseAssignment extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Actor actor;

    @Enumerated(EnumType.STRING)
    public AssignmentStatus status = AssignmentStatus.OFFERED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    public AppUser assignedBy;

    public Instant respondedAt;

    /** Why a doer declined or redirected. */
    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText note;

    public Instant createdAt = Instant.now();
}

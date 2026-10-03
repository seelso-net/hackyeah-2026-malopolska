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
import java.util.Optional;
import java.util.UUID;

/** A resident linked to a case, and whether the solution helped them. */
@Entity
public class CaseParticipant extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public AppUser user;

    @Enumerated(EnumType.STRING)
    public ParticipantRole role;

    public boolean notify = true;

    @Enumerated(EnumType.STRING)
    public Outcome outcome;

    public Instant outcomeAt;

    public Instant joinedAt = Instant.now();

    public static Optional<CaseParticipant> of(UUID caseId, UUID userId) {
        return find("caseFile.id = ?1 and user.id = ?2", caseId, userId).firstResultOptional();
    }
}

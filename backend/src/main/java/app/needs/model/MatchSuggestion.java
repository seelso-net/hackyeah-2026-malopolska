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
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The AI's proposal for a case (a playbook and doers), and the moderator's decision. */
@Entity
public class MatchSuggestion extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    public CaseFile caseFile;

    /** Null when no playbook fits yet. */
    @ManyToOne(fetch = FetchType.LAZY)
    public PlaybookVersion playbookVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText reason;

    @Enumerated(EnumType.STRING)
    public SuggestionStatus status = SuggestionStatus.PENDING;

    /** Which engine produced it, e.g. "offline" or "llm". */
    public String model;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> scores;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    public AppUser decidedBy;

    public Instant decidedAt;

    public Instant createdAt = Instant.now();

    public static Optional<MatchSuggestion> pendingFor(UUID caseId) {
        return find("caseFile.id = ?1 and status = ?2 order by createdAt desc", caseId, SuggestionStatus.PENDING)
                .firstResultOptional();
    }
}

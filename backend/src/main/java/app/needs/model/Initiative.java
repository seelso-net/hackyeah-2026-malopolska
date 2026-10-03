package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An ongoing activity shown on the map next to needs. */
@Entity
public class Initiative extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    public Actor actor;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText title;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText description;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText schedule;

    @JdbcTypeCode(SqlTypes.ARRAY)
    public String[] categoryCodes = new String[0];

    public Double lat;

    public Double lng;

    public boolean active = true;

    public Instant createdAt = Instant.now();
}

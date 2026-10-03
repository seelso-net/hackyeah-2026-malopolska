package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
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

/** Anyone who can act on a case: an office, an NGO, a group or one volunteer. */
@Entity
public class Actor extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Community community;

    @Enumerated(EnumType.STRING)
    public ActorKind kind;

    public String name;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText description;

    /** Category codes and skills, e.g. {seniors, shopping}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    public String[] capabilities = new String[0];

    public Double serviceLat;

    public Double serviceLng;

    @Column(name = "service_radius_m")
    public Integer serviceRadiusM;

    /** Set only for individual volunteers. */
    @ManyToOne(fetch = FetchType.LAZY)
    public AppUser user;

    public String contactEmail;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = Embeddings.DIMENSIONS)
    public float[] embedding;

    public boolean active = true;

    public Instant createdAt = Instant.now();
}

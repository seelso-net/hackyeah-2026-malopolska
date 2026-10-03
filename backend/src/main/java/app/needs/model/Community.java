package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One city district, campus or housing co-op. Every other table except app_user belongs to one. */
@Entity
public class Community extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    public String slug;

    @Enumerated(EnumType.STRING)
    public CommunityKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    public LocalizedText name;

    public String defaultLocale;

    @JdbcTypeCode(SqlTypes.ARRAY)
    public String[] locales;

    @JdbcTypeCode(SqlTypes.JSON)
    public CommunityConfig config;

    public Instant createdAt = Instant.now();

    public static Optional<Community> bySlug(String slug) {
        return find("slug", slug).firstResultOptional();
    }

    public List<String> localeList() {
        return locales == null || locales.length == 0 ? List.of(defaultLocale) : Arrays.asList(locales);
    }

    public CommunityConfig cfg() {
        return config == null ? new CommunityConfig(null, null, null, null) : config;
    }
}

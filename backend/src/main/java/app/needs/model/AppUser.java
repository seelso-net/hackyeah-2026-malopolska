package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One person across all communities. Roles live in Membership. Named app_user because user is reserved in SQL. */
@Entity
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    /** OIDC subject; in demo mode the value of the X-Demo-User header, e.g. "maria". */
    public String authSubject;

    public String displayName;

    public String email;

    public String phone;

    /** Preferred UI language, e.g. "pl". */
    public String locale;

    public Instant createdAt = Instant.now();

    public static Optional<AppUser> bySubject(String subject) {
        return find("authSubject", subject).firstResultOptional();
    }
}

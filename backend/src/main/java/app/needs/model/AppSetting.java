package app.needs.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Small key-value store, e.g. which embedding model produced the stored vectors. */
@Entity
public class AppSetting extends PanacheEntityBase {

    @Id
    public String key;

    public String value;
}

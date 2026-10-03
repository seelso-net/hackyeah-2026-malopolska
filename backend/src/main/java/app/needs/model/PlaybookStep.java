package app.needs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One step of a playbook. Roles are actor kinds (INSTITUTION, NGO, VOLUNTEER...) that usually do the step.
 * change is null, NEW or CHANGED compared with the previous version.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaybookStep(int position, LocalizedText title, LocalizedText description, List<String> roles, String change) {

    public PlaybookStep {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public PlaybookStep withPosition(int newPosition, String newChange) {
        return new PlaybookStep(newPosition, title, description, roles, newChange);
    }
}

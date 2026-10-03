package app.needs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * What the AI understood from a report, kept on the report until the resident confirms it.
 * Codes (category, urgency) are English; title and summary are translated into the community's languages.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResult(
        String language,
        String categoryCode,
        Urgency urgency,
        boolean safetyConcern,
        LocalizedText title,
        LocalizedText summary,
        UUID similarCaseId,
        Double similarity,
        Integer distanceMeters,
        String engine) {
}

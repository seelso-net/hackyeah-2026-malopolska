package app.needs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything that makes one deployment a city district, a campus or a housing co-op.
 * Stored as jsonb in community.config and imported/exported as JSON by admins.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommunityConfig(List<Category> categories, List<WorkflowState> workflow, List<RoleInfo> roles, Rules rules) {

    public CommunityConfig {
        categories = categories == null ? List.of() : List.copyOf(categories);
        workflow = workflow == null ? List.of() : List.copyOf(workflow);
        roles = roles == null ? List.of() : List.copyOf(roles);
        rules = rules == null ? new Rules(null, null, null, null) : rules;
    }

    /** A category residents pick from; the code is English and stable, labels are translated. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(String code, Map<String, String> labels) {
        public String label(String lang) {
            return Labels.pick(labels, lang, code);
        }
    }

    /** A visible case status and how this community names it. Statuses missing from the list are hidden. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowState(CaseStatus status, Map<String, String> labels) {
    }

    /** Who holds each role in this community, shown on the setup screen. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleInfo(Role role, Map<String, String> who) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rules(Integer mergeRadiusMeters, Integer publicLocationRoundingMeters, Integer autoCloseDays, Boolean publicMap) {
        public int mergeRadius() {
            return mergeRadiusMeters == null ? 600 : mergeRadiusMeters;
        }

        public int locationRounding() {
            return publicLocationRoundingMeters == null ? 100 : publicLocationRoundingMeters;
        }

        public int autoClose() {
            return autoCloseDays == null ? 14 : autoCloseDays;
        }

        public boolean mapIsPublic() {
            return publicMap == null || publicMap;
        }
    }

    public Optional<Category> category(String code) {
        return categories.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    public String categoryLabel(String code, String lang) {
        return category(code).map(c -> c.label(lang)).orElse(code);
    }

    /** How this community names a status. A hidden status shows the label of the last visible status before it. */
    public String statusLabel(CaseStatus status, String lang) {
        CaseStatus[] all = CaseStatus.values();
        int from = status == CaseStatus.REJECTED ? status.ordinal() : Math.min(status.ordinal(), CaseStatus.CLOSED.ordinal());
        for (int i = from; i >= 0; i--) {
            CaseStatus candidate = all[i];
            Optional<WorkflowState> state = workflow.stream().filter(w -> w.status() == candidate).findFirst();
            if (state.isPresent()) {
                return Labels.pick(state.get().labels(), lang, candidate.name());
            }
            if (status == CaseStatus.REJECTED) {
                break;
            }
        }
        return null;
    }

    /** Small helper for picking a label in the reader's language. */
    public static final class Labels {
        private Labels() {
        }

        public static String pick(Map<String, String> labels, String lang, String fallback) {
            if (labels == null || labels.isEmpty()) {
                return fallback;
            }
            String label = labels.get(lang);
            if (label == null) {
                label = labels.get("en");
            }
            if (label == null) {
                label = labels.values().iterator().next();
            }
            return label;
        }
    }
}

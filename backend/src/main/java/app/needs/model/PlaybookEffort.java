package app.needs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** Rough effort of a playbook, as plain translated labels: coordinator time, budget, time to first help. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaybookEffort(Map<String, String> coordinatorTime, Map<String, String> budget, Map<String, String> firstHelp) {
}

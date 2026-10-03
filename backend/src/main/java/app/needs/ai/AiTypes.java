package app.needs.ai;

import app.needs.model.ActorKind;
import app.needs.model.CommunityConfig;
import app.needs.model.LocalizedText;
import app.needs.model.PlaybookStep;
import app.needs.model.ReportKind;
import app.needs.model.Urgency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inputs and outputs of the AI engine, independent of which engine (offline or LLM) runs. */
public final class AiTypes {

    private AiTypes() {
    }

    /** What a report is about, in English codes plus translated title and summary. */
    public record ReportAnalysis(String language, String categoryCode, Urgency urgency, boolean safetyConcern,
                                 LocalizedText title, LocalizedText summary) {
    }

    public record AnalysisInput(String text, ReportKind kind, CommunityConfig config, List<String> locales,
                                String defaultLocale) {
    }

    /** A playbook the vector search found for a case. */
    public record PlaybookOption(UUID playbookId, UUID versionId, Map<String, String> titles, Map<String, String> originNames,
                                 double similarity, boolean categoryMatch) {
    }

    /** A doer the search found for a case. */
    public record DoerOption(UUID actorId, String name, ActorKind kind, String description, double similarity,
                             boolean categoryMatch, Integer distanceMeters, Integer serviceRadiusMeters) {
    }

    public record MatchInput(UUID caseId, String caseText, String categoryCode, Map<String, String> categoryLabels,
                             List<String> locales, String defaultLocale, List<PlaybookOption> playbooks,
                             List<DoerOption> doers, double minPlaybookSimilarity) {
    }

    public record DoerPick(UUID actorId, LocalizedText reason, double score, boolean selected) {
    }

    public record MatchDecision(UUID playbookId, LocalizedText reason, List<DoerPick> doers) {
    }

    /** A doer's update on the case, used as evidence when drafting the next playbook version. */
    public record UpdateNote(LocalizedText text, String actorKind) {
    }

    public record DraftInput(String playbookTitle, List<PlaybookStep> currentSteps, String caseSummary,
                             List<UpdateNote> updates, int helped, int participants, int caseNumber,
                             Map<String, String> communityNames, List<String> locales, String defaultLocale) {
    }

    public record DraftResult(List<PlaybookStep> steps, LocalizedText changeNotes) {
    }
}

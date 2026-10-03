package app.needs.ai.llm;

import java.util.List;

/** Structured outputs the LLM returns as JSON. Field names are part of the prompt contract. */
public final class LlmRecords {

    private LlmRecords() {
    }

    public record TranslatedText(String lang, String text) {
    }

    public record ReportReading(String language, String categoryCode, String urgency, boolean safetyConcern,
                                List<TranslatedText> titles, List<TranslatedText> summaries) {
    }

    public record DoerChoice(String actorId, List<TranslatedText> reasons) {
    }

    public record MatchAdvice(String playbookId, List<TranslatedText> playbookReasons, List<DoerChoice> doers) {
    }

    public record DraftStep(List<TranslatedText> titles, List<TranslatedText> descriptions, List<String> roles, String change) {
    }

    public record PlaybookDraft(List<DraftStep> steps, List<TranslatedText> changeNotes) {
    }

    public record Translations(List<TranslatedText> translations) {
    }
}

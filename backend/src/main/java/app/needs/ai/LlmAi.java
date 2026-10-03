package app.needs.ai;

import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.DoerOption;
import app.needs.ai.AiTypes.DoerPick;
import app.needs.ai.AiTypes.DraftInput;
import app.needs.ai.AiTypes.DraftResult;
import app.needs.ai.AiTypes.MatchDecision;
import app.needs.ai.AiTypes.MatchInput;
import app.needs.ai.AiTypes.PlaybookOption;
import app.needs.ai.AiTypes.ReportAnalysis;
import app.needs.ai.llm.LlmRecords.DoerChoice;
import app.needs.ai.llm.LlmRecords.DraftStep;
import app.needs.ai.llm.LlmRecords.MatchAdvice;
import app.needs.ai.llm.LlmRecords.PlaybookDraft;
import app.needs.ai.llm.LlmRecords.ReportReading;
import app.needs.ai.llm.LlmRecords.TranslatedText;
import app.needs.ai.llm.LlmRecords.Translations;
import app.needs.ai.llm.MatchAdvisor;
import app.needs.ai.llm.PlaybookWriter;
import app.needs.ai.llm.ReportAnalyst;
import app.needs.ai.llm.TextTranslator;
import app.needs.model.ActorKind;
import app.needs.model.Embeddings;
import app.needs.model.LocalizedText;
import app.needs.model.PlaybookStep;
import app.needs.model.Urgency;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * LLM-backed engine through quarkus-langchain4j and any OpenAI-compatible API.
 * Models are looked up lazily, so offline mode never needs an API key.
 */
@ApplicationScoped
public class LlmAi implements AiEngine {

    private static final Set<String> ROLES = Arrays.stream(ActorKind.values()).map(Enum::name).collect(Collectors.toSet());

    @Inject
    Instance<ReportAnalyst> analyst;

    @Inject
    Instance<MatchAdvisor> advisor;

    @Inject
    Instance<PlaybookWriter> writer;

    @Inject
    Instance<TextTranslator> translator;

    /** Injected directly: quarkus-langchain4j only creates the model bean for a plain injection point. */
    @Inject
    EmbeddingModel embeddingModel;

    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url", defaultValue = "https://api.openai.com/v1/")
    String baseUrl;

    @ConfigProperty(name = "quarkus.langchain4j.openai.embedding-model.model-name")
    Optional<String> embeddingModelName;

    @Override
    public String id() {
        return "llm";
    }

    @Override
    public String embedderId() {
        return "llm|" + baseUrl + "|" + embeddingModelName.orElse("default");
    }

    @Override
    public ReportAnalysis analyze(AnalysisInput in) {
        String categories = in.config().categories().stream()
                .map(c -> c.code() + " = " + c.label("en"))
                .collect(Collectors.joining("; "));
        ReportReading r = analyst.get().read(in.text(), in.kind().name(), categories, String.join(", ", in.locales()));
        String lang = r.language() != null && r.language().trim().length() >= 2
                ? lang(r.language())
                : TextTools.detectLanguage(in.text());
        Urgency urgency = Urgency.parse(r.urgency(), Urgency.MEDIUM);
        return new ReportAnalysis(lang, r.categoryCode(), urgency, r.safetyConcern() || urgency == Urgency.EMERGENCY,
                text(r.titles(), LocalizedText.of(lang, TextTools.title(in.text()))),
                text(r.summaries(), LocalizedText.of(lang, TextTools.summary(in.text()))));
    }

    @Override
    public float[] embed(String text) {
        return Embeddings.fit(embeddingModel.embed(text).content().vector());
    }

    @Override
    public Map<String, String> translate(String text, String sourceLang, List<String> targetLangs) {
        Translations t = translator.get().translate(text, sourceLang, String.join(", ", targetLangs));
        Map<String, String> out = new LinkedHashMap<>();
        if (t != null && t.translations() != null) {
            for (TranslatedText tt : t.translations()) {
                if (tt != null && tt.lang() != null && tt.text() != null && !tt.text().isBlank()
                        && targetLangs.contains(lang(tt.lang()))) {
                    out.put(lang(tt.lang()), tt.text().trim());
                }
            }
        }
        return out;
    }

    @Override
    public MatchDecision match(MatchInput in) {
        String playbooks = in.playbooks().isEmpty() ? "(none)" : in.playbooks().stream()
                .map(p -> "- id=%s | %s | proven in %s | similarity %.2f | same category: %s".formatted(
                        p.playbookId(), OfflineAi.label(p.titles(), "en", ""), OfflineAi.label(p.originNames(), "en", ""),
                        p.similarity(), p.categoryMatch() ? "yes" : "no"))
                .collect(Collectors.joining("\n"));
        String doers = in.doers().isEmpty() ? "(none)" : in.doers().stream()
                .map(d -> "- id=%s | %s | %s | %s | %s | same category: %s".formatted(
                        d.actorId(), d.name(), d.kind(), d.description(),
                        d.distanceMeters() == null ? "distance unknown" : d.distanceMeters() + " m away",
                        d.categoryMatch() ? "yes" : "no"))
                .collect(Collectors.joining("\n"));
        MatchAdvice advice = advisor.get().advise(in.caseText(), playbooks, doers, String.join(", ", in.locales()));

        Set<UUID> playbookIds = new HashSet<>();
        in.playbooks().forEach(p -> playbookIds.add(p.playbookId()));
        UUID playbookId = uuid(advice.playbookId());
        if (playbookId != null && !playbookIds.contains(playbookId)) {
            playbookId = null;
        }
        PlaybookOption chosen = null;
        for (PlaybookOption p : in.playbooks()) {
            if (p.playbookId().equals(playbookId)) {
                chosen = p;
            }
        }
        PlaybookOption finalChosen = chosen;
        LocalizedText reason = playbookId == null
                ? OfflineAi.localized(OfflineAi.NO_PLAYBOOK_REASON, in.locales(), lang -> new Object[0])
                : text(advice.playbookReasons(), OfflineAi.localized(OfflineAi.PLAYBOOK_REASON, in.locales(),
                        lang -> new Object[] {OfflineAi.label(finalChosen.originNames(), lang, "")}));

        Map<UUID, DoerOption> options = new LinkedHashMap<>();
        in.doers().forEach(d -> options.put(d.actorId(), d));
        List<DoerPick> picks = new ArrayList<>();
        Set<UUID> picked = new HashSet<>();
        if (advice.doers() != null) {
            for (DoerChoice choice : advice.doers()) {
                UUID id = uuid(choice.actorId());
                if (id != null && options.containsKey(id) && picked.add(id)) {
                    DoerOption d = options.get(id);
                    picks.add(new DoerPick(id, text(choice.reasons(), LocalizedText.of("en", d.name())),
                            Math.round(d.similarity() * 100) / 100.0, true));
                }
            }
        }
        in.doers().stream()
                .filter(d -> !picked.contains(d.actorId()) && d.categoryMatch())
                .limit(2)
                .forEach(d -> picks.add(new DoerPick(d.actorId(), OfflineAi.localized(OfflineAi.EXTRA_REASON, in.locales(),
                        lang -> new Object[0]), Math.round(d.similarity() * 100) / 100.0, false)));
        return new MatchDecision(playbookId, reason, picks);
    }

    @Override
    public DraftResult draft(DraftInput in) {
        String steps = in.currentSteps().isEmpty() ? "(none yet)" : in.currentSteps().stream()
                .map(s -> "%d. %s: %s [roles: %s]".formatted(s.position(), s.title().in("en"),
                        s.description() == null ? "" : s.description().in("en"), String.join(", ", s.roles())))
                .collect(Collectors.joining("\n"));
        String updates = in.updates().isEmpty() ? "(no updates)" : in.updates().stream()
                .map(u -> "- " + u.text().in("en") + " (by " + (u.actorKind() == null ? "a doer" : u.actorKind()) + ")")
                .collect(Collectors.joining("\n"));
        String outcome = "%d of %d residents said it helped.".formatted(in.helped(), in.participants());
        PlaybookDraft draft = writer.get().draft(in.playbookTitle(), steps, in.caseSummary(), updates, outcome,
                String.join(", ", in.locales()));

        List<PlaybookStep> out = new ArrayList<>();
        int position = 1;
        if (draft.steps() != null) {
            for (DraftStep s : draft.steps()) {
                List<String> roles = s.roles() == null ? List.of() : s.roles().stream()
                        .map(r -> r.trim().toUpperCase(Locale.ROOT))
                        .filter(ROLES::contains)
                        .toList();
                String change = s.change() == null ? null : s.change().trim().toUpperCase(Locale.ROOT);
                if (change != null && !change.equals("NEW") && !change.equals("CHANGED")) {
                    change = null;
                }
                out.add(new PlaybookStep(position, text(s.titles(), LocalizedText.of("en", "Step " + position)),
                        text(s.descriptions(), LocalizedText.of("en", "")), roles.isEmpty() ? List.of("NGO") : roles, change));
                position++;
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("The model returned no steps");
        }
        LocalizedText notes = text(draft.changeNotes(), LocalizedText.of("en", "Drafted from case R-%04d.".formatted(in.caseNumber())));
        return new DraftResult(out, notes);
    }

    /** The model's per-language entries as generated text, or the fallback when it returned none. */
    static LocalizedText text(List<TranslatedText> items, LocalizedText fallback) {
        Map<String, String> values = new LinkedHashMap<>();
        if (items != null) {
            for (TranslatedText t : items) {
                if (t != null && t.lang() != null && t.text() != null && !t.text().isBlank()) {
                    values.putIfAbsent(lang(t.lang()), t.text().trim());
                }
            }
        }
        return values.isEmpty() ? fallback : LocalizedText.generated(values);
    }

    private static String lang(String code) {
        String c = code.trim().toLowerCase(Locale.ROOT);
        return c.length() > 2 ? c.substring(0, 2) : c;
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package app.needs.ai;

import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.DraftInput;
import app.needs.ai.AiTypes.DraftResult;
import app.needs.ai.AiTypes.MatchDecision;
import app.needs.ai.AiTypes.MatchInput;
import app.needs.ai.AiTypes.ReportAnalysis;
import app.needs.model.Embeddings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The one entry point for AI. Picks the engine from app.ai.mode and falls back to the offline engine
 * whenever an LLM call fails, so a flaky network never breaks the demo. Embeddings never fall back:
 * mixing two vector spaces would make similarity meaningless.
 */
@ApplicationScoped
public class Ai {

    private static final Logger LOG = Logger.getLogger(Ai.class);

    @ConfigProperty(name = "app.ai.mode", defaultValue = "offline")
    String mode;

    @ConfigProperty(name = "app.matching.offline.min-case-similarity", defaultValue = "0.2")
    double offlineCaseSimilarity;

    @ConfigProperty(name = "app.matching.offline.min-playbook-similarity", defaultValue = "0.2")
    double offlinePlaybookSimilarity;

    @ConfigProperty(name = "app.matching.llm.min-case-similarity", defaultValue = "0.45")
    double llmCaseSimilarity;

    @ConfigProperty(name = "app.matching.llm.min-playbook-similarity", defaultValue = "0.35")
    double llmPlaybookSimilarity;

    @Inject
    OfflineAi offline;

    @Inject
    LlmAi llm;

    public boolean llmMode() {
        return "llm".equalsIgnoreCase(mode.trim());
    }

    public String engineId() {
        return llmMode() ? llm.id() : offline.id();
    }

    public String embedderId() {
        return llmMode() ? llm.embedderId() : offline.embedderId();
    }

    public double minCaseSimilarity() {
        return llmMode() ? llmCaseSimilarity : offlineCaseSimilarity;
    }

    public double minPlaybookSimilarity() {
        return llmMode() ? llmPlaybookSimilarity : offlinePlaybookSimilarity;
    }

    public ReportAnalysis analyze(AnalysisInput in) {
        ReportAnalysis rules = offline.analyze(in);
        if (!llmMode()) {
            return rules;
        }
        ReportAnalysis r = withFallback("analyze", () -> llm.analyze(in), () -> rules);
        boolean knownCategory = r.categoryCode() != null && in.config().category(r.categoryCode()).isPresent();
        return knownCategory ? r : new ReportAnalysis(r.language(), rules.categoryCode(), r.urgency(), r.safetyConcern(),
                r.title(), r.summary());
    }

    public float[] embed(String text) {
        return Embeddings.fit(llmMode() ? llm.embed(text) : offline.embed(text));
    }

    public Map<String, String> translate(String text, String sourceLang, List<String> targetLangs) {
        if (!llmMode() || targetLangs.isEmpty() || text == null || text.isBlank()) {
            return Map.of();
        }
        return withFallback("translate", () -> llm.translate(text, sourceLang, targetLangs), Map::of);
    }

    public MatchDecision match(MatchInput in) {
        return llmMode() ? withFallback("match", () -> llm.match(in), () -> offline.match(in)) : offline.match(in);
    }

    public DraftResult draft(DraftInput in) {
        return llmMode() ? withFallback("draft", () -> llm.draft(in), () -> offline.draft(in)) : offline.draft(in);
    }

    private <T> T withFallback(String step, Supplier<T> primary, Supplier<T> fallback) {
        try {
            return primary.get();
        } catch (RuntimeException e) {
            LOG.warnf("LLM %s failed, using the offline engine instead: %s", step, e.getMessage());
            return fallback.get();
        }
    }
}

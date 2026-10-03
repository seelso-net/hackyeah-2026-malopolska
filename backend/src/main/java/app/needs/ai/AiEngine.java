package app.needs.ai;

import app.needs.ai.AiTypes.AnalysisInput;
import app.needs.ai.AiTypes.DraftInput;
import app.needs.ai.AiTypes.DraftResult;
import app.needs.ai.AiTypes.MatchDecision;
import app.needs.ai.AiTypes.MatchInput;
import app.needs.ai.AiTypes.ReportAnalysis;
import java.util.List;
import java.util.Map;

/** Everything the platform asks of AI. OfflineAi needs no keys; LlmAi uses an OpenAI-compatible API. */
public interface AiEngine {

    /** Short id stored next to suggestions, e.g. "offline" or "llm". */
    String id();

    /** Identifies the embedding model; when it changes, every stored vector is recomputed. */
    String embedderId();

    ReportAnalysis analyze(AnalysisInput input);

    float[] embed(String text);

    /** Translations of text into each target language (source language excluded). Empty when unavailable. */
    Map<String, String> translate(String text, String sourceLang, List<String> targetLangs);

    MatchDecision match(MatchInput input);

    DraftResult draft(DraftInput input);
}

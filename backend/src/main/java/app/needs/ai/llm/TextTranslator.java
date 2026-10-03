package app.needs.ai.llm;

import app.needs.ai.llm.LlmRecords.Translations;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Translates people's text for readers in other languages. The original is always kept. */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface TextTranslator {

    @SystemMessage("""
            Translate the text into each requested language. Keep the meaning, tone, names, numbers and dates.
            Return one entry per requested language with lang set to its ISO 639-1 code. Answer with JSON only.
            """)
    @UserMessage("""
            Source language: {source}
            Requested languages: {languages}
            Text: {text}
            """)
    Translations translate(@V("text") String text, @V("source") String source, @V("languages") String languages);
}

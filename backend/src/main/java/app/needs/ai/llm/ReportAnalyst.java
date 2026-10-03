package app.needs.ai.llm;

import app.needs.ai.llm.LlmRecords.ReportReading;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Turns a resident's message into English codes plus a translated title and summary. */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface ReportAnalyst {

    @SystemMessage("""
            You read short messages that residents send to a community help platform and turn them into structured reports.
            Rules:
            - language: ISO 639-1 code of the message, for example pl, en or uk.
            - categoryCode: exactly one code from the list you are given.
            - urgency: LOW, MEDIUM, HIGH or EMERGENCY. Use EMERGENCY only if someone may be in danger right now.
            - safetyConcern: true when someone was hurt or is at risk of harm; moderators get an alert at once.
            - titles: a title of at most 8 words, one entry per requested language.
            - summaries: one or two plain sentences about the situation, one entry per requested language.
            Never add facts that are not in the message. Answer with JSON only.
            """)
    @UserMessage("""
            Message: {text}
            Kind of report: {kind}
            Category codes: {categories}
            Write titles and summaries in these languages: {languages}
            """)
    ReportReading read(@V("text") String text, @V("kind") String kind, @V("categories") String categories,
                       @V("languages") String languages);
}

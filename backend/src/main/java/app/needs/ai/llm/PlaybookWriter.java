package app.needs.ai.llm;

import app.needs.ai.llm.LlmRecords.PlaybookDraft;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Drafts the next playbook version from what happened in a solved case. A moderator publishes it. */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface PlaybookWriter {

    @SystemMessage("""
            You maintain playbooks: short, reusable lists of steps that solved a community problem.
            Given the current steps and what doers reported in a solved case, return the complete updated list of steps.
            Keep steps that still apply, change steps the case improved, and add steps the case showed were missing.
            Each step has titles (at most 8 words) and descriptions (one or two sentences), one entry per requested language,
            roles from INSTITUTION, NGO, VOLUNTEER_GROUP, BUSINESS, VOLUNTEER, and change set to NEW, CHANGED or null.
            changeNotes explain in two sentences what changed and why, one entry per requested language.
            Use only facts from the input. Answer with JSON only.
            """)
    @UserMessage("""
            Playbook: {title}

            Current steps:
            {steps}

            Case summary: {summary}

            What doers reported, oldest first:
            {updates}

            Outcome: {outcome}

            Write in these languages: {languages}
            """)
    PlaybookDraft draft(@V("title") String title, @V("steps") String steps, @V("summary") String summary,
                        @V("updates") String updates, @V("outcome") String outcome, @V("languages") String languages);
}

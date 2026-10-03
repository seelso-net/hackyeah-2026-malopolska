package app.needs.ai.llm;

import app.needs.ai.llm.LlmRecords.MatchAdvice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Picks one playbook and the doers for a case, only from the candidates found by vector search. */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface MatchAdvisor {

    @SystemMessage("""
            You help a community moderator match a case to a proven playbook and to the people who should act.
            Rules:
            - playbookId: the id of the single best playbook candidate, or null if none really fits.
            - doers: two to five candidates who together can carry out the playbook; prefer one organisation
              that coordinates, one institution with the right powers, and nearby volunteers.
            - Use only ids from the candidate lists.
            - Each reason is one short sentence a moderator can check, one entry per requested language.
            Answer with JSON only.
            """)
    @UserMessage("""
            Case: {caseText}

            Playbook candidates:
            {playbooks}

            Doer candidates:
            {doers}

            Write reasons in these languages: {languages}
            """)
    MatchAdvice advise(@V("caseText") String caseText, @V("playbooks") String playbooks, @V("doers") String doers,
                       @V("languages") String languages);
}

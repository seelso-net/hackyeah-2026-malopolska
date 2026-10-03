package app.needs.service;

import app.needs.ai.Ai;
import app.needs.model.CaseEvent;
import app.needs.model.CaseFile;
import app.needs.model.LocalizedText;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds missing translations to people's text (LLM mode only). The original stays untouched;
 * readers get a translation flagged as machine-translated, with "Show original" in the UI.
 */
@ApplicationScoped
public class TranslationService {

    @Inject
    Ai ai;

    public void translateCase(UUID caseId) {
        record Texts(LocalizedText title, LocalizedText summary, List<String> locales) {
        }
        Texts t = QuarkusTransaction.requiringNew().call(() -> {
            CaseFile c = CaseFile.findById(caseId);
            return new Texts(c.title, c.summary, c.community.localeList());
        });
        LocalizedText title = complete(t.title(), t.locales());
        LocalizedText summary = complete(t.summary(), t.locales());
        QuarkusTransaction.requiringNew().run(() -> {
            CaseFile c = CaseFile.findById(caseId);
            c.title = title;
            c.summary = summary;
        });
    }

    public void translateEvent(UUID eventId) {
        record Body(LocalizedText body, List<String> locales) {
        }
        Body b = QuarkusTransaction.requiringNew().call(() -> {
            CaseEvent e = CaseEvent.findById(eventId);
            return new Body(e.body, e.caseFile.community.localeList());
        });
        LocalizedText translated = complete(b.body(), b.locales());
        QuarkusTransaction.requiringNew().run(() -> CaseEvent.<CaseEvent>findById(eventId).body = translated);
    }

    /** The same text with every missing community language filled in, when the engine can translate. */
    public LocalizedText complete(LocalizedText text, List<String> locales) {
        if (text == null) {
            return null;
        }
        List<String> missing = locales.stream().filter(l -> !text.has(l)).toList();
        if (missing.isEmpty()) {
            return text;
        }
        Map<String, String> translations = ai.translate(text.original(), text.originalLanguage(), missing);
        return text.withAll(translations);
    }
}

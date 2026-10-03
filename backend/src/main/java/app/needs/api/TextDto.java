package app.needs.api;

import app.needs.model.LocalizedText;

/**
 * Translatable text as the API serves it: one string in the reader's language, the language it is in,
 * whether a machine translated it from someone's words, and those words when it did (for "Show original").
 * Text the AI wrote in each language directly is never flagged as translated.
 */
public record TextDto(String text, String lang, boolean machineTranslated, String original) {

    public static TextDto of(LocalizedText t, String lang) {
        if (t == null) {
            return null;
        }
        if (lang != null && t.has(lang)) {
            boolean translated = !t.generated() && !lang.equals(t.source());
            return new TextDto(t.in(lang), lang, translated, translated ? t.original() : null);
        }
        return new TextDto(t.original(), t.originalLanguage(), false, null);
    }
}

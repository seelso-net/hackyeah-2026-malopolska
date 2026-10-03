package app.needs.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Text kept in the language it was written in plus every translation made so far.
 * Stored as jsonb: {"source": "pl", "values": {"pl": "...", "en": "..."}}.
 * Text the AI wrote in every language at once has source "*": no version is a translation of another.
 */
public record LocalizedText(String source, Map<String, String> values) {

    public static final String GENERATED = "*";

    public LocalizedText {
        values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        if (source == null && !values.isEmpty()) {
            source = values.keySet().iterator().next();
        }
    }

    /** A person's text in the language they wrote it in. */
    public static LocalizedText of(String lang, String text) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(lang, text);
        return new LocalizedText(lang, v);
    }

    /** Text the AI (or a template) wrote directly in each language. */
    public static LocalizedText generated(Map<String, String> values) {
        return new LocalizedText(GENERATED, values);
    }

    public boolean generated() {
        return GENERATED.equals(source);
    }

    /** Text in the requested language, or the original when that translation does not exist yet. */
    public String in(String lang) {
        String t = lang == null ? null : values.get(lang);
        return t != null ? t : original();
    }

    /** The language of original(): the source language, or English (else the first) for generated text. */
    public String originalLanguage() {
        if (!generated() && values.containsKey(source)) {
            return source;
        }
        if (values.containsKey("en")) {
            return "en";
        }
        return values.isEmpty() ? source : values.keySet().iterator().next();
    }

    /** The text as it was first written. */
    public String original() {
        String t = values.get(originalLanguage());
        if (t != null) {
            return t;
        }
        return values.isEmpty() ? "" : values.values().iterator().next();
    }

    public boolean has(String lang) {
        return values.containsKey(lang);
    }

    public LocalizedText with(String lang, String text) {
        Map<String, String> v = new LinkedHashMap<>(values);
        if (text != null && !text.isBlank()) {
            v.put(lang, text);
        }
        return new LocalizedText(source, v);
    }

    public LocalizedText withAll(Map<String, String> translations) {
        Map<String, String> v = new LinkedHashMap<>(values);
        translations.forEach((lang, text) -> {
            if (text != null && !text.isBlank() && !lang.equals(source)) {
                v.put(lang, text);
            }
        });
        return new LocalizedText(source, v);
    }

    /** Every language version joined, used for embeddings and search. */
    public String joined() {
        return String.join("\n", values.values());
    }
}

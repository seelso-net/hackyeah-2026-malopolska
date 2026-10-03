package app.needs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.needs.model.LocalizedText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** "Machine translated · Show original" must appear on people's words only, and only when translated. */
class TextDtoTest {

    @Test
    void aPersonsWordsInTheirOwnLanguage() {
        TextDto t = TextDto.of(LocalizedText.of("pl", "Dzień dobry"), "pl");
        assertEquals("Dzień dobry", t.text());
        assertFalse(t.machineTranslated());
        assertNull(t.original());
    }

    @Test
    void aTranslationKeepsTheOriginal() {
        LocalizedText text = LocalizedText.of("pl", "Dzień dobry").with("en", "Good morning");
        TextDto t = TextDto.of(text, "en");
        assertEquals("Good morning", t.text());
        assertEquals("en", t.lang());
        assertTrue(t.machineTranslated());
        assertEquals("Dzień dobry", t.original());
    }

    @Test
    void aMissingTranslationFallsBackToTheOriginal() {
        TextDto t = TextDto.of(LocalizedText.of("pl", "Dzień dobry"), "uk");
        assertEquals("Dzień dobry", t.text());
        assertEquals("pl", t.lang());
        assertFalse(t.machineTranslated());
    }

    @Test
    void textTheAiWroteInEveryLanguageIsNotFlagged() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("pl", "Sprawdzone w Starym Mieście.");
        values.put("en", "Proven in Old Town.");
        LocalizedText generated = LocalizedText.generated(values);
        TextDto pl = TextDto.of(generated, "pl");
        assertEquals("Sprawdzone w Starym Mieście.", pl.text());
        assertFalse(pl.machineTranslated());
        TextDto uk = TextDto.of(generated, "uk");
        assertEquals("Proven in Old Town.", uk.text(), "English is the fallback for generated text");
        assertEquals("en", uk.lang());
    }

    @Test
    void translationsNeverReplaceTheSource() {
        LocalizedText text = LocalizedText.of("pl", "Dzień dobry").withAll(Map.of("pl", "zmienione", "en", "Good morning"));
        assertEquals("Dzień dobry", text.original());
        assertEquals("Good morning", text.in("en"));
    }
}

package app.needs.ai;

import app.needs.model.Embeddings;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Language-agnostic text helpers used by the offline engine and by the API. */
public final class TextTools {

    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");

    private static final Set<String> STOPWORDS = Set.of(
            // en
            "the", "and", "for", "with", "has", "have", "are", "was", "this", "that", "there", "from", "not", "our",
            "you", "can", "any", "more", "she", "her", "his", "him", "they", "them", "been", "into", "about", "who",
            "but", "all", "would", "could", "will",
            // pl (normalized, without diacritics)
            "nie", "sie", "jest", "ale", "jak", "dla", "przy", "ich", "juz", "tez", "mam", "ten", "tej", "tego", "czy",
            "oraz", "jako", "sa", "nas", "mnie", "bardzo", "tylko", "kto",
            // uk (normalized)
            "не", "та", "на", "без", "мені", "може", "можу", "сама", "сам", "вже", "дуже", "або");

    private TextTools() {
    }

    /** Lower case, no diacritics (ł becomes l), words separated by single spaces. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        s = COMBINING.matcher(Normalizer.normalize(s, Normalizer.Form.NFKD)).replaceAll("");
        return NON_WORD.matcher(s).replaceAll(" ").trim();
    }

    public static List<String> tokens(String normalized) {
        List<String> out = new ArrayList<>();
        for (String t : normalized.split(" ")) {
            if (t.length() >= 3 && !STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Rough ISO 639-1 guess for pl / uk / en, good enough until the LLM or the resident corrects it. */
    public static String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        long cyrillic = text.codePoints()
                .filter(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.CYRILLIC)
                .count();
        if (cyrillic > 3) {
            return "uk";
        }
        String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]+", " ") + " ";
        int pl = 0;
        for (char c : lower.toCharArray()) {
            if ("ąćęłńóśźż".indexOf(c) >= 0) {
                pl += 2;
            }
        }
        for (String w : List.of(" nie ", " jest ", " na ", " w ", " z ", " do ", " mam ", " mieszkam ", " sie ", " jak ")) {
            pl += count(lower, w);
        }
        int en = 0;
        for (String w : List.of(" the ", " and ", " is ", " to ", " of ", " my ", " on ", " in ", " a ", " with ")) {
            en += count(lower, w);
        }
        return pl > en ? "pl" : "en";
    }

    /** A short title: the first sentence, at most eight words. */
    public static String title(String text) {
        String first = sentences(text).stream().findFirst().orElse("").replaceAll("^[\"“”„'«»]+|[\"“”„'«»]+$", "");
        String[] words = first.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, 8); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(words[i]);
        }
        String t = sb.toString().replaceAll("[.,;:!?…]+$", "");
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** One or two sentences, at most 280 characters. */
    public static String summary(String text) {
        List<String> s = sentences(text);
        String joined = s.isEmpty() ? "" : (s.size() == 1 ? s.get(0) : s.get(0) + " " + s.get(1));
        joined = joined.replaceAll("^[\"“”„'«»]+|[\"“”„'«»]+$", "").trim();
        return joined.length() <= 280 ? joined : joined.substring(0, 277).trim() + "…";
    }

    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String s : SENTENCE_END.split(text.trim())) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /**
     * Offline embedding: hashed words, two word stems and character trigrams, so "piętrze" and "pięter" or
     * "lift" and "lifts" land close. It only matches words, not meaning across languages; LLM mode does that.
     */
    public static float[] hashEmbedding(String text) {
        float[] v = new float[Embeddings.DIMENSIONS];
        for (String token : tokens(normalize(text))) {
            add(v, token, 1.0f);
            if (token.length() > 5) {
                add(v, "~" + token.substring(0, 5), 0.8f);
            }
            // Polish and Ukrainian change word endings a lot: windy/windą, bloku/blokach
            if (token.length() >= 5) {
                add(v, "^" + token.substring(0, 4), 0.6f);
            }
            for (int i = 0; i + 3 <= token.length(); i++) {
                add(v, "#" + token.substring(i, i + 3), 0.25f);
            }
        }
        Embeddings.normalize(v);
        return v;
    }

    private static void add(float[] v, String feature, float weight) {
        int h = fnv1a(feature);
        int index = Math.floorMod(h, v.length);
        float sign = ((h >>> 20) & 1) == 0 ? 1f : -1f;
        v[index] += sign * weight;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 16777619;
        }
        return h;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + 1);
        }
        return n;
    }

    /** Distance in metres between two points (equirectangular approximation, fine within a city). */
    public static Integer distanceMeters(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double x = Math.toRadians(lng2 - lng1) * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double y = Math.toRadians(lat2 - lat1);
        return (int) Math.round(Math.sqrt(x * x + y * y) * 6_371_000);
    }
}

package app.needs.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.needs.model.Embeddings;
import org.junit.jupiter.api.Test;

class TextToolsTest {

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    @Test
    void sameProblemInOtherWordsScoresAboveUnrelatedProblems() {
        float[] maria = TextTools.hashEmbedding(
                "Moja sąsiadka z czwartego piętra ma 84 lata i od miesiąca nie wychodzi z domu, bo w bloku nie ma windy.");
        float[] stairs = TextTools.hashEmbedding(
                "Mieszkam na czwartym piętrze, w bloku nie ma windy. Od tygodni nie wychodzę z domu.");
        float[] lamp = TextTools.hashEmbedding("Latarnia na ścieżce nad rzeką nie świeci od dwóch tygodni.");
        double related = cosine(maria, stairs);
        double unrelated = cosine(maria, lamp);
        assertTrue(related > 0.3, "related: " + related);
        assertTrue(unrelated < 0.1, "unrelated: " + unrelated);
    }

    @Test
    void vectorsHaveTheDatabaseLength() {
        assertEquals(Embeddings.DIMENSIONS, TextTools.hashEmbedding("test").length);
        assertEquals(Embeddings.DIMENSIONS, Embeddings.fit(new float[1536]).length);
        assertEquals(Embeddings.DIMENSIONS, Embeddings.fit(new float[768]).length);
    }

    @Test
    void titlesAreTheFirstSentenceCappedAtEightWords() {
        assertEquals("Wywieście listę chętnych na drzwiach każdej klatki",
                TextTools.title("Wywieście listę chętnych na drzwiach każdej klatki. Zgłosiło się 9 sąsiadów."));
        assertEquals("One two three four five six seven eight",
                TextTools.title("one two three four five six seven eight nine ten"));
    }

    @Test
    void distancesAreInMetres() {
        int d = TextTools.distanceMeters(52.2496, 21.0410, 52.2499, 21.0415);
        assertTrue(d > 40 && d < 60, "distance " + d);
    }
}

package app.needs.model;

/** Vector length used by every embedding column. Must match vector(1024) in V1__schema.sql. */
public final class Embeddings {
    public static final int DIMENSIONS = 1024;

    private Embeddings() {
    }

    /** pgvector literal, e.g. [0.1,0.2], for native queries. */
    public static String literal(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * Fits any model's output to DIMENSIONS: longer vectors are truncated and re-normalised
     * (fine for OpenAI text-embedding-3 models), shorter ones are padded with zeros (cosine stays the same).
     */
    public static float[] fit(float[] v) {
        if (v.length == DIMENSIONS) {
            return v;
        }
        float[] out = new float[DIMENSIONS];
        System.arraycopy(v, 0, out, 0, Math.min(v.length, DIMENSIONS));
        if (v.length > DIMENSIONS) {
            normalize(out);
        }
        return out;
    }

    public static void normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }
}

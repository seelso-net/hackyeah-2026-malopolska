package app.needs.model;

public enum Urgency {
    LOW, MEDIUM, HIGH, EMERGENCY;

    public static Urgency parse(String value, Urgency fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Urgency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

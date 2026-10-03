package app.needs.support;

/** Public maps show locations snapped to a grid, so a pin never points at one resident's door. */
public final class Geo {

    private static final double METERS_PER_DEGREE = 111_320.0;

    private Geo() {
    }

    public record Point(Double lat, Double lng) {
    }

    public static Point round(Double lat, Double lng, int meters) {
        if (lat == null || lng == null || meters <= 0) {
            return new Point(lat, lng);
        }
        double latStep = meters / METERS_PER_DEGREE;
        double rLat = Math.round(lat / latStep) * latStep;
        // The longitude step depends on the snapped row, so every point in a cell gets the same pin.
        double lngStep = meters / (METERS_PER_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(rLat))));
        double rLng = Math.round(lng / lngStep) * lngStep;
        return new Point(Math.round(rLat * 1e5) / 1e5, Math.round(rLng * 1e5) / 1e5);
    }

    /** bbox as "minLng,minLat,maxLng,maxLat" (the GeoJSON order); null when absent. */
    public static double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw Problems.badRequest("bbox must be minLng,minLat,maxLng,maxLat");
        }
        double[] b = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                b[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            throw Problems.badRequest("bbox must be four numbers: minLng,minLat,maxLng,maxLat");
        }
        return b;
    }

    public static boolean inside(double[] bbox, Double lat, Double lng) {
        if (bbox == null) {
            return true;
        }
        if (lat == null || lng == null) {
            return false;
        }
        return lng >= bbox[0] && lat >= bbox[1] && lng <= bbox[2] && lat <= bbox[3];
    }
}

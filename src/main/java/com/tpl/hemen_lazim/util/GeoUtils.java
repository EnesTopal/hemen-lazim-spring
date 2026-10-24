package com.tpl.hemen_lazim.util;

/** Konum hesapları; mesafeler metre cinsinden. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6371000d;
    // Gerçek değerden (~111.2 km) biraz küçük: kutu her zaman daireyi içine alacak kadar geniş olur
    private static final double METERS_PER_DEGREE = 111_000d;

    private GeoUtils() {}

    // Haversine (metre)
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    // Yaklaşık bounding-box (derece): {minLat, maxLat, minLng, maxLng}
    public static double[] boundingBox(double lat, double lng, double radiusMeters) {
        double dLat = radiusMeters / METERS_PER_DEGREE;
        double dLng = radiusMeters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - dLat, lat + dLat, lng - dLng, lng + dLng};
    }
}

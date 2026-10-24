package com.tpl.hemen_lazim.util;

/** Testlerde bir noktadan belirli yön ve mesafedeki noktayı üretir (GeoUtils ile aynı küre). */
public final class GeoTestSupport {

    private static final double EARTH_RADIUS_M = 6371000d;

    private GeoTestSupport() {}

    /** {lat, lng}: başlangıçtan bearingDeg yönünde distanceMeters uzaktaki nokta. */
    public static double[] destination(double lat, double lng, double bearingDeg, double distanceMeters) {
        double angular = distanceMeters / EARTH_RADIUS_M;
        double bearing = Math.toRadians(bearingDeg);
        double lat1 = Math.toRadians(lat);
        double lng1 = Math.toRadians(lng);
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular)
                + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing));
        double lng2 = lng1 + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
                Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
        return new double[]{Math.toDegrees(lat2), Math.toDegrees(lng2)};
    }
}

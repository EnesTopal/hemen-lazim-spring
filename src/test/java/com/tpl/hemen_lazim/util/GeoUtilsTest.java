package com.tpl.hemen_lazim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    private static final double LAT = 41.0082;
    private static final double LNG = 28.9784;

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195, GeoUtils.haversineMeters(41, 29, 42, 29), 1);
    }

    @Test
    void haversineMatchesTheDistanceAPointWasPlacedAt() {
        double[] p = GeoTestSupport.destination(LAT, LNG, 63, 7_000);

        assertEquals(7_000, GeoUtils.haversineMeters(LAT, LNG, p[0], p[1]), 0.01);
    }

    @Test
    void boundingBoxContainsEveryPointOnTheCircle() {
        double radius = 12_000;
        double[] box = GeoUtils.boundingBox(LAT, LNG, radius);

        for (int bearing = 0; bearing < 360; bearing += 15) {
            double[] p = GeoTestSupport.destination(LAT, LNG, bearing, radius);
            assertTrue(p[0] >= box[0] && p[0] <= box[1], "latitude outside box at bearing " + bearing);
            assertTrue(p[1] >= box[2] && p[1] <= box[3], "longitude outside box at bearing " + bearing);
        }
    }
}

package com.dispatch.common.geo;

/**
 * Minimal geohash encoder used to divide a city into ~uniform grid zones.
 * Precision 5 yields cells of roughly 5km x 5km, used by the surge engine.
 */
public final class GeoHash {

    private static final char[] BASE32 =
            "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    private GeoHash() {
    }

    public static String encode(double lat, double lng, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};

        StringBuilder geohash = new StringBuilder();
        boolean even = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            if (even) {
                double mid = (lngRange[0] + lngRange[1]) / 2;
                if (lng >= mid) {
                    ch |= (1 << (4 - bit));
                    lngRange[0] = mid;
                } else {
                    lngRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2;
                if (lat >= mid) {
                    ch |= (1 << (4 - bit));
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }

            even = !even;
            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return geohash.toString();
    }
}

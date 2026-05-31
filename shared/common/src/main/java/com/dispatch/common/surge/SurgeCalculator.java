package com.dispatch.common.surge;

/**
 * Maps a zone's demand/supply ratio to a surge multiplier.
 *
 * <pre>
 *   ratio &lt; 1.5    -&gt; 1.0x  (no surge)
 *   1.5 .. 2.0     -&gt; 1.5x
 *   2.0 .. 2.5     -&gt; 2.0x
 *   2.5 .. 3.0     -&gt; 2.5x
 *   ratio &gt; 3.0    -&gt; 3.0x  (capped)
 * </pre>
 */
public class SurgeCalculator {

    private final double maxMultiplier;

    public SurgeCalculator() {
        this(3.0);
    }

    public SurgeCalculator(double maxMultiplier) {
        this.maxMultiplier = maxMultiplier;
    }

    public double demandSupplyRatio(long pendingRequests, long availableDrivers) {
        if (availableDrivers <= 0) {
            return pendingRequests > 0 ? Double.MAX_VALUE : 0.0;
        }
        return (double) pendingRequests / (double) availableDrivers;
    }

    public double multiplierForRatio(double ratio) {
        double multiplier;
        if (ratio < 1.5) {
            multiplier = 1.0;
        } else if (ratio < 2.0) {
            multiplier = 1.5;
        } else if (ratio < 2.5) {
            multiplier = 2.0;
        } else if (ratio <= 3.0) {
            multiplier = 2.5;
        } else {
            multiplier = 3.0;
        }
        return Math.min(multiplier, maxMultiplier);
    }

    public double multiplier(long pendingRequests, long availableDrivers) {
        return multiplierForRatio(demandSupplyRatio(pendingRequests, availableDrivers));
    }
}

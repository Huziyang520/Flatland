package com.huziyang.flatland.world;

/**
 * Flatness weight field. Pure function of (seed, x, z): the biome remap, the terrain shave
 * the height queries all read this same value, so they can never disagree about where it is flat.
 */
public final class FlatBand {
    private static final double PRIMARY_SCALE = 384.0D;
    private static final double DETAIL_SCALE = 192.0D;
    private static final double HALF_BAND = 0.18D;

    private static volatile FlatBand instance;

    private final long seed;

    private FlatBand(long seed) {
        this.seed = seed;
    }

    public static void seed(long seed) {
        FlatBand current = instance;
        if (current == null || current.seed != seed) {
            instance = new FlatBand(seed);
        }
    }

    public static float weight(int x, int z) {
        FlatBand band = instance;
        return band == null ? 0.0F : band.compute(x, z);
    }

    private float compute(int x, int z) {
        double raw = 0.6667D * octave(x / PRIMARY_SCALE, z / PRIMARY_SCALE, 0x9E3779B97F4A7C15L)
                + 0.3333D * octave(x / DETAIL_SCALE, z / DETAIL_SCALE, 0xC2B2AE3D27D4EB4FL);
        double t = (raw + HALF_BAND) / (2.0D * HALF_BAND);
        if (t <= 0.0D) {
            return 0.0F;
        }
        if (t >= 1.0D) {
            return 1.0F;
        }
        t = t * t * (3.0D - 2.0D * t);
        return (float) t;
    }

    private double octave(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;
        double a = hash(x0, z0, salt);
        double b = hash(x0 + 1, z0, salt);
        double c = hash(x0, z0 + 1, salt);
        double d = hash(x0 + 1, z0 + 1, salt);
        double u = tx * tx * (3.0D - 2.0D * tx);
        double v = tz * tz * (3.0D - 2.0D * tz);
        return (a * (1.0D - u) + b * u) * (1.0D - v) + (c * (1.0D - u) + d * u) * v;
    }

    private double hash(int x, int z, long salt) {
        long h = seed ^ salt;
        h += x * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h += z * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 27)) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        return ((h & 0xFFFFFFL) / 8388608.0D) - 1.0D;
    }
}

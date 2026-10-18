package com.davidpe.cosmicaces.domain.scenery;

/** Immutable world and noise parameters for reproducible terrain generation. */
public record TerrainConfig(
        long seed,
        int version,
        int worldWidth,
        int worldHeight,
        int cellSize,
        double forestRegionSize,
        double rockyRegionSize,
        double forestThreshold,
        double rockyThreshold
) {
    public TerrainConfig {
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        if (worldWidth <= 0 || worldHeight <= 0) throw new IllegalArgumentException("world dimensions must be positive");
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        if (!positiveFinite(forestRegionSize) || !positiveFinite(rockyRegionSize)) {
            throw new IllegalArgumentException("region sizes must be finite and positive");
        }
        if (!threshold(forestThreshold) || !threshold(rockyThreshold)) {
            throw new IllegalArgumentException("noise thresholds must be finite and within [-1, 1]");
        }
        long columns = ((long) worldWidth + cellSize - 1) / cellSize;
        long rows = ((long) worldHeight + cellSize - 1) / cellSize;
        if (columns * rows > Integer.MAX_VALUE) throw new IllegalArgumentException("terrain grid is too large");
    }

    public static TerrainConfig defaults(long seed, int worldWidth, int worldHeight) {
        return new TerrainConfig(seed, 1, worldWidth, worldHeight, 64, 1400.0, 1100.0, 0.12, 0.58);
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static boolean threshold(double value) {
        return Double.isFinite(value) && value >= -1 && value <= 1;
    }
}

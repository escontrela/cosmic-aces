package com.davidpe.cosmicaces.domain.scenery;

import java.util.Objects;

/** Builds a deterministic coarse terrain map; safe to call outside the render loop. */
public final class TerrainGenerator {
    private static final long FOREST_CHANNEL = 0x4F1BBCDCBFA54001L;
    private static final long ROCK_CHANNEL = 0x6A09E667F3BCC909L;

    public TerrainMap generate(TerrainConfig config) {
        Objects.requireNonNull(config, "config");
        int columns = (int) (((long) config.worldWidth() + config.cellSize() - 1) / config.cellSize());
        int rows = (int) (((long) config.worldHeight() + config.cellSize() - 1) / config.cellSize());
        TerrainType[] terrain = new TerrainType[columns * rows];
        float[] density = new float[terrain.length];
        long forestSeed = mixSeed(config.seed() ^ FOREST_CHANNEL ^ config.version());
        long rockSeed = mixSeed(config.seed() ^ ROCK_CHANNEL ^ config.version());
        for (int row = 0; row < rows; row++) {
            double y = Math.min((row + 0.5) * config.cellSize(), config.worldHeight() - 0.5);
            for (int column = 0; column < columns; column++) {
                double x = Math.min((column + 0.5) * config.cellSize(), config.worldWidth() - 0.5);
                int index = row * columns + column;
                float forestNoise = OpenSimplex2S.noise2(forestSeed, x / config.forestRegionSize(), y / config.forestRegionSize());
                float rockyNoise = OpenSimplex2S.noise2(rockSeed, x / config.rockyRegionSize(), y / config.rockyRegionSize());
                if (rockyNoise >= config.rockyThreshold()) {
                    terrain[index] = TerrainType.ROCKY;
                } else if (forestNoise >= config.forestThreshold()) {
                    terrain[index] = TerrainType.FOREST;
                    density[index] = clamp((forestNoise - (float) config.forestThreshold())
                            / (1.0f - (float) config.forestThreshold()));
                } else {
                    terrain[index] = TerrainType.MEADOW;
                }
            }
        }
        return new TerrainMap(config, terrain, density);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static long mixSeed(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

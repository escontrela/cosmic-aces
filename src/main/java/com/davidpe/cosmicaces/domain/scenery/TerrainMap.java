package com.davidpe.cosmicaces.domain.scenery;

import java.util.Objects;

/** Immutable grid of terrain regions sampled in global world coordinates. */
public final class TerrainMap {
    private final TerrainConfig config;
    private final int columns;
    private final int rows;
    private final TerrainType[] terrain;
    private final float[] forestDensity;

    TerrainMap(TerrainConfig config, TerrainType[] terrain, float[] forestDensity) {
        this.config = Objects.requireNonNull(config);
        this.columns = ceilDiv(config.worldWidth(), config.cellSize());
        this.rows = ceilDiv(config.worldHeight(), config.cellSize());
        if (terrain.length != columns * rows || forestDensity.length != terrain.length) {
            throw new IllegalArgumentException("terrain data does not match map dimensions");
        }
        this.terrain = terrain.clone();
        this.forestDensity = forestDensity.clone();
    }

    public TerrainConfig config() { return config; }
    public int columns() { return columns; }
    public int rows() { return rows; }
    public TerrainType terrainAtCell(int column, int row) {
        checkCell(column, row);
        return terrain[row * columns + column];
    }

    /** Returns the forest placement density in [0, 1], or zero outside forest regions. */
    public float forestDensityAtCell(int column, int row) {
        checkCell(column, row);
        return forestDensity[row * columns + column];
    }

    public TerrainType terrainAtWorld(double x, double y) {
        checkWorld(x, y);
        return terrainAtCell((int) (x / config.cellSize()), (int) (y / config.cellSize()));
    }

    public float forestDensityAtWorld(double x, double y) {
        checkWorld(x, y);
        return forestDensityAtCell((int) (x / config.cellSize()), (int) (y / config.cellSize()));
    }

    private void checkCell(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("cell outside terrain map");
        }
    }

    private void checkWorld(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0
                || x >= config.worldWidth() || y >= config.worldHeight()) {
            throw new IndexOutOfBoundsException("world position outside terrain map");
        }
    }

    private static int ceilDiv(int value, int divisor) {
        return (int) (((long) value + divisor - 1) / divisor);
    }
}

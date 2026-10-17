package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Selects repeated ground tiles intersecting a conservative visible world rectangle. */
public final class TerrainGroundPlanner {
    public record Tile(int column, int row, double x, double y, int width, int height,
                       int sourceX, int sourceY) {}

    private final int worldWidth;
    private final int worldHeight;
    private final int columns;
    private final int rows;

    public TerrainGroundPlanner(int worldWidth, int worldHeight) {
        if (worldWidth <= 0 || worldHeight <= 0) throw new IllegalArgumentException("world dimensions must be positive");
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        int size = TerrainAtlasCatalog.GRASS_TILE_SIZE;
        columns = ceilDiv(worldWidth, size);
        rows = ceilDiv(worldHeight, size);
    }

    public List<Tile> plan(WorldRect visibleArea) {
        Objects.requireNonNull(visibleArea, "visibleArea");
        double left = Math.max(0, visibleArea.x());
        double bottom = Math.max(0, visibleArea.y());
        double right = Math.min(worldWidth, visibleArea.right());
        double top = Math.min(worldHeight, visibleArea.top());
        if (left >= right || bottom >= top) return List.of();

        int size = TerrainAtlasCatalog.GRASS_TILE_SIZE;
        int firstColumn = (int) Math.floor(left / size);
        int lastColumn = Math.min(columns - 1, (int) Math.floor(Math.nextDown(right) / size));
        int firstRow = (int) Math.floor(bottom / size);
        int lastRow = Math.min(rows - 1, (int) Math.floor(Math.nextDown(top) / size));
        int partialWidth = worldWidth % size;
        int partialHeight = worldHeight % size;
        List<Tile> selected = new ArrayList<>();
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                boolean rightEdge = column == columns - 1 && partialWidth != 0;
                boolean topEdge = row == rows - 1 && partialHeight != 0;
                int width = rightEdge ? partialWidth : size;
                int height = topEdge ? partialHeight : size;
                selected.add(new Tile(column, row, column * (double) size, row * (double) size,
                        width, height, 0, topEdge ? size - partialHeight : 0));
            }
        }
        return List.copyOf(selected);
    }

    private static int ceilDiv(int value, int divisor) {
        return (int) (((long) value + divisor - 1) / divisor);
    }
}

package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainGroundPlannerTest {
    @Test
    void wholePhaseOneMapUsesRepeatedTilesAndCropsTheFinalWorldRow() {
        TerrainGroundPlanner planner = new TerrainGroundPlanner(8192, 12000);
        List<TerrainGroundPlanner.Tile> tiles = planner.plan(new WorldRect(0, 0, 8192, 12000));

        assertEquals(16 * 24, tiles.size());
        assertTrue(tiles.stream().allMatch(tile -> tile.width() == 512));
        List<TerrainGroundPlanner.Tile> finalRow = tiles.stream().filter(tile -> tile.row() == 23).toList();
        assertEquals(16, finalRow.size());
        assertTrue(finalRow.stream().allMatch(tile -> tile.height() == 224 && tile.sourceY() == 288));
        assertEquals(0, finalRow.getFirst().column());
        assertEquals(11776, finalRow.getFirst().y());
    }

    @Test
    void worldWithPartialColumnsAndRowsUsesCorrectBottomLeftSourceCrop() {
        TerrainGroundPlanner planner = new TerrainGroundPlanner(1003, 777);
        List<TerrainGroundPlanner.Tile> tiles = planner.plan(new WorldRect(0, 0, 1003, 777));

        assertEquals(4, tiles.size());
        TerrainGroundPlanner.Tile corner = tiles.stream()
                .filter(tile -> tile.column() == 1 && tile.row() == 1).findFirst().orElseThrow();
        assertEquals(491, corner.width());
        assertEquals(265, corner.height());
        assertEquals(0, corner.sourceX());
        assertEquals(247, corner.sourceY());
    }

    @Test
    void exactEdgesDoNotIncludeTheTileBeyondThemAndOutsideWorldViewsAreEmpty() {
        TerrainGroundPlanner planner = new TerrainGroundPlanner(2048, 2048);

        assertEquals(List.of(new TerrainGroundPlanner.Tile(1, 1, 512, 512, 512, 512, 0, 0)),
                planner.plan(new WorldRect(512, 512, 512, 512)));
        assertTrue(planner.plan(new WorldRect(2100, 100, 100, 100)).isEmpty());
        assertTrue(planner.plan(new WorldRect(-900, -900, 400, 400)).isEmpty());
    }

    @Test
    void conservativeRotatedAndZoomedWorldBoundsSelectEveryIntersectingTile() {
        TerrainGroundPlanner planner = new TerrainGroundPlanner(8192, 12000);
        WorldRect[] conservativeCameraBounds = {
                new WorldRect(1000, 1000, 800, 600),
                new WorldRect(850, 850, 1100, 900),
                new WorldRect(700, 700, 1400, 1200)
        };
        for (WorldRect bounds : conservativeCameraBounds) {
            List<TerrainGroundPlanner.Tile> tiles = planner.plan(bounds);
            assertTrue(tiles.size() >= 4);
            for (TerrainGroundPlanner.Tile tile : tiles) {
                assertTrue(new WorldRect(tile.x(), tile.y(), tile.width(), tile.height()).intersects(bounds));
            }
        }
    }
}

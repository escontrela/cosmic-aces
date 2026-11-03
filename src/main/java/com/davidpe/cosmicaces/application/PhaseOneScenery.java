package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.TerrainConfig;
import com.davidpe.cosmicaces.domain.scenery.TerrainGenerator;
import com.davidpe.cosmicaces.domain.scenery.TerrainMap;
import com.davidpe.cosmicaces.domain.scenery.TreePatternGenerator;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import java.util.ArrayList;
import java.util.List;

/** Immutable procedural landscape prepared once for a PhaseOne run and retained by its controller. */
public final class PhaseOneScenery {
    public static final long DEFAULT_SEED = 0x434F534D49434143L;
    /** The operations base spans three 1024x768 reference viewports, beginning at the south edge. */
    public static final double OPERATIONS_BASE_WIDTH = 3072.0;
    public static final double OPERATIONS_BASE_HEIGHT = 2304.0;
    public static final String ENEMY_BASE_ASSET_ID = "enemy-operations-base-01";
    /** Astra starts just inside the south runway threshold, with the nose pointing north. */
    public static final double RUNWAY_SPAWN_INSET = 96.0;

    /**
     * Size of one cell in the retired 8x16 PNG matrix. The five approved village milestones keep
     * their original 1:1 pixel size; only their longitudinal position is rescaled.
     */
    public static final double LEGACY_MAP_TILE_WIDTH = 1024.0;
    public static final double LEGACY_MAP_TILE_HEIGHT = 768.0;
    /** Height of the original PhaseOne map, used to distribute the milestones proportionally. */
    public static final double LEGACY_MAP_HEIGHT = 12000.0;
    /** Keeps procedural trees, bushes, puddles and rocks from covering a village patch. */
    public static final double VILLAGE_RESERVATION_MARGIN = 48.0;

    /**
     * Approved village milestones as {@code {legacyRow, legacyColumn}}. The whole PNG cell is drawn
     * at 1:1 and the order is south to north, which is also the original matrix order.
     */
    private static final int[][] LEGACY_VILLAGE_CELLS = {
            {2, 1}, {2, 2}, {3, 1}, {3, 2}, {16, 2}
    };

    private final TerrainConfig config;
    private final TerrainMap terrainMap;
    private final SceneryField sceneryField;
    private final SceneryPlacement operationsBasePlacement;
    private final SceneryPlacement enemyBasePlacement;
    private final List<SceneryPlacement> villagePlacements;

    public PhaseOneScenery(long seed, int worldWidth, int worldHeight) {
        if (worldWidth <= 0 || worldHeight <= 0) {
            throw new IllegalArgumentException("world dimensions must be positive");
        }
        config = TerrainConfig.defaults(seed, worldWidth, worldHeight);
        terrainMap = new TerrainGenerator().generate(config);
        double baseX = (worldWidth - OPERATIONS_BASE_WIDTH) / 2.0;
        WorldRect baseBounds = new WorldRect(baseX, 0, OPERATIONS_BASE_WIDTH, OPERATIONS_BASE_HEIGHT);
        operationsBasePlacement = new SceneryPlacement("operations-base", "operations-base-01",
                SceneryPlacement.Anchor.BOTTOM_CENTER, baseBounds.centerX(), baseBounds.y(),
                baseBounds, 100, 48);
        WorldRect enemyBounds = new WorldRect(baseX, worldHeight - OPERATIONS_BASE_HEIGHT,
                OPERATIONS_BASE_WIDTH, OPERATIONS_BASE_HEIGHT);
        enemyBasePlacement = new SceneryPlacement("enemy-operations-base", ENEMY_BASE_ASSET_ID,
                SceneryPlacement.Anchor.BOTTOM_CENTER, enemyBounds.centerX(), enemyBounds.y(),
                enemyBounds, 100, 48);
        villagePlacements = buildVillagePlacements(worldWidth, worldHeight);
        List<SceneryPlacement> reservations = new ArrayList<>(villagePlacements.size() + 2);
        reservations.add(operationsBasePlacement);
        reservations.add(enemyBasePlacement);
        reservations.addAll(villagePlacements);
        sceneryField = new TreePatternGenerator().generate(terrainMap, reservations);
    }

    public TerrainConfig config() { return config; }
    public TerrainMap terrainMap() { return terrainMap; }
    public SceneryField sceneryField() { return sceneryField; }
    public SceneryPlacement operationsBasePlacement() { return operationsBasePlacement; }
    public SceneryPlacement enemyBasePlacement() { return enemyBasePlacement; }

    /** Five approved village PNG cells placed 1:1 along the extended advance axis. */
    public List<SceneryPlacement> villagePlacements() { return villagePlacements; }

    public double operationsBaseSpawnCenterX() { return operationsBasePlacement.anchorX(); }
    public double operationsBaseSpawnCenterY() {
        return operationsBasePlacement.bounds().y() + RUNWAY_SPAWN_INSET;
    }

    /**
     * Places each approved village cell at its legacy column and at its legacy row scaled by the
     * ratio between the extended world and the original 12000-unit map. The tile keeps its 1024x768
     * art size, so only the distance between milestones grows; nothing is stretched.
     */
    private static List<SceneryPlacement> buildVillagePlacements(int worldWidth, int worldHeight) {
        double verticalScale = worldHeight / LEGACY_MAP_HEIGHT;
        List<SceneryPlacement> placements = new ArrayList<>(LEGACY_VILLAGE_CELLS.length);
        for (int[] cell : LEGACY_VILLAGE_CELLS) {
            int row = cell[0];
            int column = cell[1];
            double x = (column - 1) * LEGACY_MAP_TILE_WIDTH;
            double y = (row - 1) * LEGACY_MAP_TILE_HEIGHT * verticalScale;
            WorldRect bounds = new WorldRect(x, y, LEGACY_MAP_TILE_WIDTH, LEGACY_MAP_TILE_HEIGHT);
            if (bounds.right() > worldWidth || bounds.top() > worldHeight) {
                throw new IllegalArgumentException(
                        "village milestone row " + row + " column " + column
                                + " exceeds the PhaseOne world");
            }
            placements.add(new SceneryPlacement(
                    "legacy-village-row-%02d-col-%02d".formatted(row, column),
                    "map-row-%02d-col-%02d".formatted(row, column),
                    SceneryPlacement.Anchor.BOTTOM_LEFT, bounds.x(), bounds.y(), bounds, 50,
                    VILLAGE_RESERVATION_MARGIN));
        }
        return List.copyOf(placements);
    }
}

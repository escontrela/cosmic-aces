package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.TerrainConfig;
import com.davidpe.cosmicaces.domain.scenery.TerrainGenerator;
import com.davidpe.cosmicaces.domain.scenery.TerrainMap;
import com.davidpe.cosmicaces.domain.scenery.TreePatternGenerator;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
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
    private final TerrainConfig config;
    private final TerrainMap terrainMap;
    private final SceneryField sceneryField;
    private final SceneryPlacement operationsBasePlacement;
    private final SceneryPlacement enemyBasePlacement;

    public PhaseOneScenery(long seed, int worldWidth, int worldHeight) {
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
        sceneryField = new TreePatternGenerator().generate(
                terrainMap, List.of(operationsBasePlacement, enemyBasePlacement));
    }

    public TerrainConfig config() { return config; }
    public TerrainMap terrainMap() { return terrainMap; }
    public SceneryField sceneryField() { return sceneryField; }
    public SceneryPlacement operationsBasePlacement() { return operationsBasePlacement; }
    public SceneryPlacement enemyBasePlacement() { return enemyBasePlacement; }
    public double operationsBaseSpawnCenterX() { return operationsBasePlacement.anchorX(); }
    public double operationsBaseSpawnCenterY() {
        return operationsBasePlacement.bounds().y() + RUNWAY_SPAWN_INSET;
    }
}

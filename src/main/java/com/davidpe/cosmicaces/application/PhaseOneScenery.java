package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.TerrainConfig;
import com.davidpe.cosmicaces.domain.scenery.TerrainGenerator;
import com.davidpe.cosmicaces.domain.scenery.TerrainMap;
import com.davidpe.cosmicaces.domain.scenery.TreePatternGenerator;

/** Immutable procedural landscape prepared once for a PhaseOne run and retained by its controller. */
public final class PhaseOneScenery {
    public static final long DEFAULT_SEED = 0x434F534D49434143L;
    private final TerrainConfig config;
    private final TerrainMap terrainMap;
    private final SceneryField sceneryField;

    public PhaseOneScenery(long seed, int worldWidth, int worldHeight) {
        config = TerrainConfig.defaults(seed, worldWidth, worldHeight);
        terrainMap = new TerrainGenerator().generate(config);
        sceneryField = new TreePatternGenerator().generate(terrainMap);
    }

    public TerrainConfig config() { return config; }
    public TerrainMap terrainMap() { return terrainMap; }
    public SceneryField sceneryField() { return sceneryField; }
}

package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import java.util.List;
import java.util.Map;

/** Resource ids and dimensions for the generated, single-page terrain atlas. */
public final class TerrainAtlasCatalog {
    public static final String ATLAS_PATH = "assets/images/terrain/terrain.atlas";
    public static final String GRASS_ID = "grass-02";
    public static final int PAGE_WIDTH = 1040;
    public static final int PAGE_HEIGHT = 1300;
    public static final int PAGE_COUNT = 1;
    public static final int GRASS_TILE_SIZE = 512;
    private static final Map<String, Region> REGIONS = Map.ofEntries(
            Map.entry(GRASS_ID, new Region(GRASS_ID, 512, 512, "assets/images/terrain/v2/grass-02.png")),
            Map.entry("tree-01", new Region("tree-01", 256, 256, "assets/images/terrain/tree-01.png")),
            Map.entry("tree-02", new Region("tree-02", 256, 256, "assets/images/terrain/v2/tree-02.png")),
            Map.entry("bush-01", new Region("bush-01", 256, 256, "assets/images/terrain/bush-01.png")),
            Map.entry("bush-02", new Region("bush-02", 256, 256, "assets/images/terrain/v2/bush-02.png")),
            Map.entry("puddle-01", new Region("puddle-01", 256, 256, "assets/images/terrain/puddle-01.png")),
            Map.entry("puddle-02", new Region("puddle-02", 256, 256, "assets/images/terrain/puddle-02.png")),
            Map.entry("puddle-03", new Region("puddle-03", 256, 256, "assets/images/terrain/puddle-03.png")),
            Map.entry("puddle-04", new Region("puddle-04", 256, 256, "assets/images/terrain/v2/puddle-04.png")),
            Map.entry("rock-cluster-01", new Region("rock-cluster-01", 256, 256,
                    "assets/images/terrain/rock-cluster-01.png")),
            Map.entry("rock-cluster-02", new Region("rock-cluster-02", 256, 256,
                    "assets/images/terrain/v2/rock-cluster-02.png"))
    );

    private TerrainAtlasCatalog() {}

    public static List<Region> regions() { return List.copyOf(REGIONS.values()); }

    public static Region region(String id) {
        Region region = REGIONS.get(id);
        if (region == null) throw new IllegalArgumentException("unknown terrain atlas region: " + id);
        return region;
    }

    public record Region(String id, int width, int height, String sourcePath) {}
}

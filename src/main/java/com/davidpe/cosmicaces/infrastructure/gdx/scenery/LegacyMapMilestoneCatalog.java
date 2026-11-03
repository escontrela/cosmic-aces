package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import java.util.List;
import java.util.Map;

/**
 * Metadata for the five approved village cells of the retired 8x16 PNG matrix. Each cell is loaded
 * on its own as an opaque 1:1 map patch, so the whole legacy mosaic is never part of the active
 * PhaseOne renderer.
 */
public final class LegacyMapMilestoneCatalog {
    public static final int TILE_WIDTH = 1024;
    public static final int TILE_HEIGHT = 768;

    private static final Map<String, Asset> ASSETS = Map.of(
            "map-row-02-col-01", asset("map-row-02-col-01", "row-02-col-01"),
            "map-row-02-col-02", asset("map-row-02-col-02", "row-02-col-02"),
            "map-row-03-col-01", asset("map-row-03-col-01", "row-03-col-01"),
            "map-row-03-col-02", asset("map-row-03-col-02", "row-03-col-02"),
            "map-row-16-col-02", asset("map-row-16-col-02", "row-16-col-02")
    );

    private LegacyMapMilestoneCatalog() {}

    /** The metadata for an approved village cell. */
    public static Asset asset(String assetId) {
        Asset asset = ASSETS.get(assetId);
        if (asset == null) throw new IllegalArgumentException("unknown legacy map milestone: " + assetId);
        return asset;
    }

    /** Every approved village cell, in the south-to-north order used by the application. */
    public static List<Asset> assets() {
        return List.of(
                ASSETS.get("map-row-02-col-01"), ASSETS.get("map-row-02-col-02"),
                ASSETS.get("map-row-03-col-01"), ASSETS.get("map-row-03-col-02"),
                ASSETS.get("map-row-16-col-02"));
    }

    private static Asset asset(String id, String fileName) {
        return new Asset(id, "assets/images/map-phase-one/" + fileName + ".png",
                TILE_WIDTH, TILE_HEIGHT);
    }

    /** A single opaque village PNG and the world size it must keep. */
    public record Asset(String id, String internalPath, int width, int height) {}
}

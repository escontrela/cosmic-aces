package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import java.util.List;
import java.util.Map;

/**
 * Metadata for the five approved village cells of the retired 8x16 PNG matrix. Each cell is loaded
 * on its own as an opaque 1:1 map patch, so the whole legacy mosaic is never part of the active
 * PhaseOne renderer. The visible name is stable presentation metadata keyed by asset id, so a
 * minimap label never depends on the incidental position of a list.
 */
public final class LegacyMapMilestoneCatalog {
    public static final int TILE_WIDTH = 1024;
    public static final int TILE_HEIGHT = 768;

    private static final Map<String, Asset> ASSETS = Map.of(
            "map-row-02-col-01", asset("map-row-02-col-01", "row-02-col-01", "Noia"),
            "map-row-02-col-02", asset("map-row-02-col-02", "row-02-col-02", "Muros"),
            "map-row-03-col-01", asset("map-row-03-col-01", "row-03-col-01", "Betanzos"),
            "map-row-03-col-02", asset("map-row-03-col-02", "row-03-col-02", "Fisterra"),
            "map-row-16-col-02", asset("map-row-16-col-02", "row-16-col-02", "Ribadeo")
    );

    private LegacyMapMilestoneCatalog() {}

    /** The metadata for an approved village cell. */
    public static Asset asset(String assetId) {
        Asset asset = ASSETS.get(assetId);
        if (asset == null) throw new IllegalArgumentException("unknown legacy map milestone: " + assetId);
        return asset;
    }

    /** The approved visible name for an asset id, validated against the catalogue. */
    public static String nameFor(String assetId) {
        return asset(assetId).name();
    }

    /** Every approved village cell, in the south-to-north order used by the application. */
    public static List<Asset> assets() {
        return List.of(
                ASSETS.get("map-row-02-col-01"), ASSETS.get("map-row-02-col-02"),
                ASSETS.get("map-row-03-col-01"), ASSETS.get("map-row-03-col-02"),
                ASSETS.get("map-row-16-col-02"));
    }

    private static Asset asset(String id, String fileName, String name) {
        return new Asset(id, "assets/images/map-phase-one/" + fileName + ".png",
                TILE_WIDTH, TILE_HEIGHT, name);
    }

    /** A single opaque village PNG, the world size it must keep and its approved visible name. */
    public record Asset(String id, String internalPath, int width, int height, String name) {
        public Asset {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("legacy map milestone name is required");
            }
        }
    }
}

package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LegacyMapMilestoneCatalogTest {
    private static final List<String> EXPECTED_IDS = List.of(
            "map-row-02-col-01", "map-row-02-col-02",
            "map-row-03-col-01", "map-row-03-col-02", "map-row-16-col-02");
    private static final List<String> EXPECTED_NAMES = List.of(
            "Noia", "Muros", "Betanzos", "Fisterra", "Ribadeo");

    @Test
    void listsTheFiveApprovedVillageCellsInSouthToNorthOrder() {
        assertEquals(EXPECTED_IDS, LegacyMapMilestoneCatalog.assets().stream()
                .map(LegacyMapMilestoneCatalog.Asset::id).toList());
    }

    @Test
    void everyVillageKeepsItsApprovedVisibleName() {
        assertEquals(EXPECTED_NAMES, LegacyMapMilestoneCatalog.assets().stream()
                .map(LegacyMapMilestoneCatalog.Asset::name).toList());
        for (int index = 0; index < EXPECTED_IDS.size(); index++) {
            assertEquals(EXPECTED_NAMES.get(index),
                    LegacyMapMilestoneCatalog.nameFor(EXPECTED_IDS.get(index)));
        }
    }

    @Test
    void unknownMilestonesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMapMilestoneCatalog.asset("map-row-01-col-01"));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMapMilestoneCatalog.nameFor("map-row-01-col-01"));
    }

    @Test
    void everyVillageSourceIsPresentAtItsExactOneToOneSize() throws IOException {
        for (LegacyMapMilestoneCatalog.Asset asset : LegacyMapMilestoneCatalog.assets()) {
            assertEquals(1024, asset.width());
            assertEquals(768, asset.height());
            BufferedImage image = read(asset.internalPath());
            assertEquals(asset.width(), image.getWidth(), asset.id());
            assertEquals(asset.height(), image.getHeight(), asset.id());
            assertFalse(image.getColorModel().hasAlpha(), asset.id() + " is an opaque map patch");
        }
    }

    private static BufferedImage read(String internalPath) throws IOException {
        try (InputStream stream = LegacyMapMilestoneCatalogTest.class
                .getResourceAsStream("/" + internalPath)) {
            assertNotNull(stream, "missing resource " + internalPath);
            return ImageIO.read(stream);
        }
    }
}

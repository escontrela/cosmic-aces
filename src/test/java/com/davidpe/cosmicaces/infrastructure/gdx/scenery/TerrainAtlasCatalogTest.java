package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import org.junit.jupiter.api.Test;

class TerrainAtlasCatalogTest {
    private static final Set<String> EXPECTED_IDS = Set.of(
            "grass-02", "tree-01", "tree-02", "bush-01", "bush-02",
            "puddle-01", "puddle-02", "puddle-03", "puddle-04", "rock-cluster-01", "rock-cluster-02",
            "operations-base-01", "enemy-operations-base-01"
    );

    @Test
    void atlasContainsOnePageAndEveryExpectedResourceAtItsPackedDimensions() throws IOException {
        assertEquals(1, TerrainAtlasCatalog.PAGE_COUNT);
        assertEquals(2560, TerrainAtlasCatalog.PAGE_WIDTH);
        assertEquals(2200, TerrainAtlasCatalog.PAGE_HEIGHT);
        assertEquals(EXPECTED_IDS, TerrainAtlasCatalog.regions().stream()
                .map(TerrainAtlasCatalog.Region::id).collect(java.util.stream.Collectors.toSet()));

        BufferedImage page = read("/assets/images/terrain/terrain-atlas.png");
        assertEquals(TerrainAtlasCatalog.PAGE_WIDTH, page.getWidth());
        assertEquals(TerrainAtlasCatalog.PAGE_HEIGHT, page.getHeight());
        FileHandle atlasFile = new FileHandle("src/main/resources/" + TerrainAtlasCatalog.ATLAS_PATH);
        TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        assertEquals(TerrainAtlasCatalog.PAGE_COUNT, data.getPages().size);
        assertEquals((float) TerrainAtlasCatalog.PAGE_WIDTH, data.getPages().first().width, 0.0f);
        assertEquals((float) TerrainAtlasCatalog.PAGE_HEIGHT, data.getPages().first().height, 0.0f);
        assertEquals("terrain-atlas.png", data.getPages().first().textureFile.name());
        var regions = data.getRegions();
        Set<String> manifestIds = new HashSet<>();
        for (var region : regions) {
            String id = region.name;
            manifestIds.add(id);
            TerrainAtlasCatalog.Region expected = TerrainAtlasCatalog.region(id);
            assertEquals(expected.width(), region.width);
            assertEquals(expected.height(), region.height);
        }
        assertEquals(EXPECTED_IDS, manifestIds);
    }

    @Test
    void darkGrassHasLowMeanOppositeEdgeDifferenceForSeamlessRepeating() throws IOException {
        BufferedImage atlas = read("/assets/images/terrain/terrain-atlas.png");
        assertGrassEdges(atlas, 2, 2);
        BufferedImage grass = read("/assets/images/terrain/v2/grass-02.png");
        assertTrue(grass.getWidth() >= TerrainAtlasCatalog.GRASS_TILE_SIZE);
    }

    private static void assertGrassEdges(BufferedImage atlas, int regionX, int regionY) {
        long difference = 0;
        long channels = 0;
        for (int y = 0; y < TerrainAtlasCatalog.GRASS_TILE_SIZE; y++) {
            int left = atlas.getRGB(regionX, regionY + y);
            int right = atlas.getRGB(regionX + TerrainAtlasCatalog.GRASS_TILE_SIZE - 1, regionY + y);
            difference += channelDifference(left, right);
            channels += 3;
        }
        for (int pixelX = 0; pixelX < TerrainAtlasCatalog.GRASS_TILE_SIZE; pixelX++) {
            int top = atlas.getRGB(regionX + pixelX, regionY);
            int bottom = atlas.getRGB(regionX + pixelX, regionY + TerrainAtlasCatalog.GRASS_TILE_SIZE - 1);
            difference += channelDifference(top, bottom);
            channels += 3;
        }
        assertTrue(difference / (double) channels < 1.0,
                "packed grass edges should be continuous after the documented border blend");
    }

    @Test
    void allDecorationSourcesHaveActualTransparentAndVisiblePixels() throws IOException {
        for (TerrainAtlasCatalog.Region region : TerrainAtlasCatalog.regions()) {
            if (region.id().startsWith("grass-")) continue;
            BufferedImage image = read("/" + region.sourcePath());
            assertTrue(image.getWidth() >= region.width());
            assertTrue(image.getHeight() >= region.height());
            assertTrue(image.getColorModel().hasAlpha(), region.id() + " needs an alpha channel");
            boolean transparent = false;
            boolean visible = false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    transparent |= alpha == 0;
                    visible |= alpha > 0;
                }
            }
            assertTrue(transparent, region.id() + " needs fully transparent background pixels");
            assertTrue(visible, region.id() + " needs visible sprite pixels");
        }
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream stream = TerrainAtlasCatalogTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return ImageIO.read(stream);
        }
    }

    private static int channelDifference(int left, int right) {
        return Math.abs(((left >>> 16) & 255) - ((right >>> 16) & 255))
                + Math.abs(((left >>> 8) & 255) - ((right >>> 8) & 255))
                + Math.abs((left & 255) - (right & 255));
    }
}

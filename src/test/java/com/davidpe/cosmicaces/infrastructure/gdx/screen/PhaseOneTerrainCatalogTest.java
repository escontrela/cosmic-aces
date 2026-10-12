package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCatalog.Tile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhaseOneTerrainCatalogTest {

  private static final Pattern PATH_PATTERN =
      Pattern.compile("assets/images/map-phase-one/row-\\d{2}-col-\\d{2}\\.png");

  @Test
  void catalogHasOneHundredTwentyEightUniqueTiles() {
    List<Tile> tiles = PhaseOneTerrainCatalog.tiles();
    assertEquals(128, tiles.size());
    assertEquals(PhaseOneTerrainCatalog.TILE_COUNT, tiles.size());
    assertEquals(PhaseOneTerrainCatalog.COLUMNS * PhaseOneTerrainCatalog.ROWS, tiles.size());

    Set<String> paths = new HashSet<>();
    Set<String> rects = new HashSet<>();
    Set<String> coordinates = new HashSet<>();
    for (Tile tile : tiles) {
      assertTrue(PATH_PATTERN.matcher(tile.path()).matches(), "unexpected path: " + tile.path());
      assertEquals(PhaseOneTerrainCatalog.pathFor(tile.row(), tile.column()), tile.path());
      assertTrue(paths.add(tile.path()), "duplicate path: " + tile.path());
      assertTrue(
          coordinates.add(tile.row() + ":" + tile.column()),
          "duplicate coordinate: " + tile.row() + ":" + tile.column());
      assertTrue(
          rects.add(
              tile.worldX() + "," + tile.worldY() + "," + tile.worldWidth() + ","
                  + tile.worldHeight()),
          "duplicate world rectangle: " + tile);
    }
    assertEquals(128, paths.size());
    assertEquals(128, coordinates.size());
    assertEquals(128, rects.size());
  }

  @Test
  void rectsAreContiguousAndCoverTheWholeWorld() {
    for (int row = 1; row <= PhaseOneTerrainCatalog.ROWS; row++) {
      float expectedWidth = 0f;
      for (int column = 1; column <= PhaseOneTerrainCatalog.COLUMNS; column++) {
        Tile tile = PhaseOneTerrainCatalog.tile(row, column);
        assertEquals((column - 1) * 1024f, tile.worldX(), "worldX at " + row + "," + column);
        assertEquals((row - 1) * 768f, tile.worldY(), "worldY at " + row + "," + column);
        assertEquals(1024f, tile.worldWidth());
        expectedWidth += tile.worldWidth();
      }
      assertEquals(PhaseOneTerrainCatalog.WORLD_WIDTH, expectedWidth);
    }

    for (int column = 1; column <= PhaseOneTerrainCatalog.COLUMNS; column++) {
      float expectedHeight = 0f;
      for (int row = 1; row <= PhaseOneTerrainCatalog.ROWS; row++) {
        expectedHeight += PhaseOneTerrainCatalog.tile(row, column).worldHeight();
      }
      assertEquals(PhaseOneTerrainCatalog.WORLD_HEIGHT, expectedHeight, "coverage at column " + column);
    }
  }

  @Test
  void catalogWorldMatchesThePhaseOneController() {
    assertEquals(PhaseOneGameController.WORLD.width(), PhaseOneTerrainCatalog.WORLD_WIDTH);
    assertEquals(PhaseOneGameController.WORLD.height(), PhaseOneTerrainCatalog.WORLD_HEIGHT);
  }

  @Test
  void firstAndLastRowUseTheExpectedWorldHeightsAndSourceCrop() {
    assertEquals(288f, PhaseOneTerrainCatalog.WORLD_HEIGHT_OVERFLOW);
    assertEquals(480f, PhaseOneTerrainCatalog.LAST_ROW_VISIBLE_HEIGHT);

    Tile first = PhaseOneTerrainCatalog.tile(1, 1);
    assertEquals(0f, first.worldY());
    assertEquals(768f, first.worldHeight());
    assertEquals(0, first.sourceY());
    assertEquals(768, first.sourceHeight());

    Tile penultimate = PhaseOneTerrainCatalog.tile(PhaseOneTerrainCatalog.ROWS - 1, 1);
    assertEquals(15f * 768f - 768f, penultimate.worldY());
    assertEquals(768f, penultimate.worldHeight());
    assertEquals(0, penultimate.sourceY());

    Tile last = PhaseOneTerrainCatalog.tile(PhaseOneTerrainCatalog.ROWS, PhaseOneTerrainCatalog.COLUMNS);
    assertEquals(15f * 768f, last.worldY());
    assertEquals(480f, last.worldHeight());
    assertEquals(288, last.sourceY());
    assertEquals(480, last.sourceHeight());
    assertEquals(7168f, last.worldX());
    assertEquals(1024f, last.worldWidth());
  }

  @Test
  void sourceCropMapsOnePixelPerWorldUnitWithoutStretching() {
    for (Tile tile : PhaseOneTerrainCatalog.tiles()) {
      assertEquals(0, tile.sourceX(), "sourceX must start at the PNG left edge: " + tile);
      assertEquals(1024, tile.sourceWidth());
      assertEquals(tile.worldWidth(), tile.sourceWidth(), "width must not scale: " + tile);
      assertEquals(tile.worldHeight(), tile.sourceHeight(), "height must not scale: " + tile);
      assertTrue(tile.sourceY() >= 0, "sourceY must be inside the PNG: " + tile);
      assertTrue(tile.sourceY() + tile.sourceHeight() <= 768, "crop must fit the PNG: " + tile);
      if (tile.row() < PhaseOneTerrainCatalog.ROWS) {
        assertEquals(0, tile.sourceY());
        assertEquals(768, tile.sourceHeight());
      }
    }
  }

  @Test
  void pointOnAnInteriorBorderResolvesToTheNextTile() {
    assertPoint(0f, 0f, 1, 1);
    assertPoint(1023.99f, 767.99f, 1, 1);
    assertPoint(1024f, 100f, 1, 2);
    assertPoint(100f, 768f, 2, 1);
    assertPoint(7168f, 11520f, 16, 8);
    assertPoint(8191.99f, 11999.99f, 16, 8);
  }

  @Test
  void pointsOutsideTheWorldHaveNoTile() {
    assertTrue(PhaseOneTerrainCatalog.tileAt(-0.01f, 0f).isEmpty());
    assertTrue(PhaseOneTerrainCatalog.tileAt(0f, -0.01f).isEmpty());
    assertTrue(PhaseOneTerrainCatalog.tileAt(PhaseOneTerrainCatalog.WORLD_WIDTH, 0f).isEmpty());
    assertTrue(PhaseOneTerrainCatalog.tileAt(0f, PhaseOneTerrainCatalog.WORLD_HEIGHT).isEmpty());
    assertTrue(
        PhaseOneTerrainCatalog.tileAt(
                PhaseOneTerrainCatalog.WORLD_WIDTH, PhaseOneTerrainCatalog.WORLD_HEIGHT)
            .isEmpty());
  }

  @Test
  void rangeQueryReturnsIntersectingTilesIncludingTouchingBorders() {
    assertIndices(PhaseOneTerrainCatalog.tilesIn(0.1f, 0.1f, 1023.9f, 767.9f), 1, 1);

    Tile expected = PhaseOneTerrainCatalog.tileAt(2000f, 2000f).orElseThrow();
    assertEquals(3, expected.row());
    assertEquals(2, expected.column());
    assertIndices(PhaseOneTerrainCatalog.tilesIn(2000f, 2000f, 2000f, 2000f), 3, 2);

    List<Tile> topBand = PhaseOneTerrainCatalog.tilesIn(0f, 11900f, 8192f, 12000f);
    assertEquals(8, topBand.size(), "the top band must hold the last row's eight tiles");
    for (Tile tile : topBand) {
      assertEquals(16, tile.row());
    }

    List<Tile> sharedCorner = PhaseOneTerrainCatalog.tilesIn(1024f, 768f, 1024f, 768f);
    assertEquals(4, sharedCorner.size(), "shared borders count as intersection");

    assertIndices(PhaseOneTerrainCatalog.tilesIn(1023.9f, 767.9f, 0.1f, 0.1f), 1, 1);
  }

  @Test
  void rangeQueryFullyOutsideTheWorldIsEmpty() {
    assertTrue(PhaseOneTerrainCatalog.tilesIn(8192f, 0f, 9000f, 100f).isEmpty());
    assertTrue(PhaseOneTerrainCatalog.tilesIn(0f, 12000f, 100f, 13000f).isEmpty());
    assertTrue(PhaseOneTerrainCatalog.tilesIn(-5000f, -5000f, -100f, -100f).isEmpty());
  }

  @Test
  void indicesOutsideTheMatrixAreRejected() {
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.tile(0, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.tile(17, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.tile(1, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.tile(1, 9));
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.pathFor(0, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> PhaseOneTerrainCatalog.pathFor(16, 9));
  }

  @Test
  void orientationDeclaresTheImageTopAsNorth() {
    assertFalse(PhaseOneTerrainCatalog.requiresVerticalFlip());
  }

  @Test
  void bundledTilesExistWithTheDeclaredDimensions() throws IOException {
    for (Tile tile : PhaseOneTerrainCatalog.tiles()) {
      assertNotNull(
          getClass().getResource("/" + tile.path()),
          "terrain PNG must be bundled with the game: " + tile.path());
    }

    assertEquals(1024, readImage(PhaseOneTerrainCatalog.tile(1, 1)).getWidth());
    assertEquals(768, readImage(PhaseOneTerrainCatalog.tile(1, 1)).getHeight());
    assertEquals(1024, readImage(PhaseOneTerrainCatalog.tile(16, 8)).getWidth());
    assertEquals(768, readImage(PhaseOneTerrainCatalog.tile(16, 8)).getHeight());
  }

  private static BufferedImage readImage(Tile tile) throws IOException {
    var resource = PhaseOneTerrainCatalogTest.class.getResource("/" + tile.path());
    assertNotNull(resource, "terrain PNG must be bundled with the game: " + tile.path());
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "terrain PNG must be a readable image: " + tile.path());
    return image;
  }

  private static void assertPoint(float x, float y, int row, int column) {
    Tile tile = PhaseOneTerrainCatalog.tileAt(x, y).orElseThrow();
    assertEquals(row, tile.row(), "row at " + x + "," + y);
    assertEquals(column, tile.column(), "column at " + x + "," + y);
  }

  private static void assertIndices(List<Tile> tiles, int row, int column) {
    assertEquals(1, tiles.size(), "expected exactly one tile " + row + "," + column);
    assertEquals(row, tiles.get(0).row());
    assertEquals(column, tiles.get(0).column());
  }
}

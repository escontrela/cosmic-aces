package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

class MinimapProjectionTest {
  private static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);

  @Test void projectsWorldCornersAndCenterIntoTheMapRectangle() {
    MinimapProjection projection = MinimapProjection.fit(WORLD, 1024f, 768f);
    MinimapProjection.Rectangle map = projection.map();

    assertPoint(projection.project(0f, 0f), map.x(), map.y());
    assertPoint(projection.project(WORLD.width(), WORLD.height()),
        map.x() + map.width(), map.y() + map.height());
    assertPoint(projection.project(WORLD.width() / 2f, WORLD.height() / 2f),
        map.x() + map.width() / 2f, map.y() + map.height() / 2f);
  }

  @Test void preservesWorldAspectRatioAndFitsInsideTheLowerRightViewport() {
    MinimapProjection projection = MinimapProjection.fit(WORLD, 1024f, 768f);
    MinimapProjection.Rectangle panel = projection.panel();
    MinimapProjection.Rectangle map = projection.map();

    assertEquals(panel, map, "one panel rectangle gives the map a single border");
    assertEquals(WORLD.width() / WORLD.height(), map.width() / map.height(), 0.00001f);
    assertTrue(panel.x() >= 0f);
    assertTrue(panel.x() + panel.width() <= 1024f);
    assertEquals(FlightConsoleLayout.HEIGHT, panel.y(), 0.0001f,
        "the bottom HUD reading stays below the panel");
    assertTrue(panel.y() + panel.height() <= 768f);
  }

  @Test void anchorsPanelToTheRightEdgeAcrossWideAndSixteenTenViewports() {
    for (float[] size : new float[][] {{1920f, 1080f}, {1440f, 900f}}) {
      MinimapProjection.Rectangle panel =
          MinimapProjection.fit(WORLD, size[0], size[1]).panel();
      assertEquals(size[0] - 24f, panel.x() + panel.width(), 0.001f);
      assertTrue(panel.y() >= MinimapProjection.BOTTOM_MARGIN);
      assertTrue(panel.y() + panel.height() <= size[1]);
      assertTrue(panel.y() + panel.height() >= size[1] - 8f,
          "the taller COS-129 panel reaches almost the top edge");
    }
  }

  @Test void keepsMarkersInsideTheMapAtWorldEdgesAndClampsOutOfBoundsCoordinates() {
    MinimapProjection projection = MinimapProjection.fit(WORLD, 1024f, 768f);
    MinimapProjection.Rectangle map = projection.map();
    float margin = 13f;

    MinimapProjection.Point lower = projection.projectMarker(-10f, -20f, margin);
    MinimapProjection.Point upper = projection.projectMarker(
        WORLD.width() + 10f, WORLD.height() + 20f, margin);

    assertPoint(lower, map.x() + margin, map.y() + margin);
    assertPoint(upper, map.x() + map.width() - margin, map.y() + map.height() - margin);
  }

  @Test void mapsAstraAndRaiderHeadingConventionsToTheirWorldDirections() {
    assertPoint(MinimapProjection.astraDirection(0f), 0f, 1f);
    assertPoint(MinimapProjection.astraDirection(90f), 1f, 0f);
    assertPoint(MinimapProjection.raiderDirection(0f), 0f, -1f);
    assertPoint(MinimapProjection.raiderDirection(90f), 1f, 0f);
  }

  @Test void extendedLongWorldKeepsBothExtremesAndTheAspectRatio() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    MinimapProjection projection = MinimapProjection.fit(extended, 1024f, 768f);
    MinimapProjection.Rectangle map = projection.map();

    assertEquals(extended.width() / extended.height(), map.width() / map.height(), 0.00001f);
    assertPoint(projection.project(extended.width() / 2f, 0f),
        map.x() + map.width() / 2f, map.y());
    assertPoint(projection.project(extended.width() / 2f, extended.height()),
        map.x() + map.width() / 2f, map.y() + map.height());
  }

  @Test void usesAlmostAllUsableHeightAtEightHundredBySixHundred() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    MinimapProjection projection = MinimapProjection.fit(extended, 800f, 600f);
    MinimapProjection.Rectangle panel = projection.panel();
    MinimapProjection.Rectangle map = projection.map();
    float usableHeight = 600f - MinimapProjection.BOTTOM_MARGIN;

    assertTrue(map.height() >= 0.85f * usableHeight,
        "the map covers at least 85% of the band above the pilot console");
    assertEquals(extended.width() / extended.height(), map.width() / map.height(), 0.00001f);
    assertEquals(MinimapProjection.BOTTOM_MARGIN, panel.y(), 0.0001f,
        "the panel starts exactly above the fixed pilot console");
    assertEquals(FlightConsoleLayout.HEIGHT, panel.y(), 0.0001f,
        "the console owns the shared bottom band");
    assertTrue(panel.y() + panel.height() <= 600f);
    assertTrue(panel.x() >= 0f);
    assertTrue(panel.x() + panel.width() <= 800f);
  }

  @Test void recalculatesTheAlmostFullHeightPanelAcrossViewportSizesAndAspectRatios() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    float[][] viewports = {{800f, 600f}, {1200f, 700f}, {1440f, 900f}, {1920f, 1080f}};
    float previousHeight = 0f;

    for (float[] size : viewports) {
      MinimapProjection projection = MinimapProjection.fit(extended, size[0], size[1]);
      MinimapProjection.Rectangle panel = projection.panel();
      MinimapProjection.Rectangle map = projection.map();
      float usableHeight = size[1] - MinimapProjection.BOTTOM_MARGIN;

      assertTrue(map.height() >= 0.85f * usableHeight,
          "the map fills at least 85% of the band above the console at "
              + size[0] + "x" + size[1]);
      assertTrue(map.height() > previousHeight, "the map grows with the viewport height");
      assertEquals(extended.width() / extended.height(), map.width() / map.height(), 0.00001f);
      assertEquals(size[0] - 24f, panel.x() + panel.width(), 0.001f,
          "the panel stays anchored to the right edge");
      assertTrue(panel.y() >= MinimapProjection.BOTTOM_MARGIN);
      assertTrue(panel.y() + panel.height() <= size[1]);
      previousHeight = map.height();
    }
  }

  @Test void mapPanelStartsExactlyAboveTheConsolePanelAtEverySupportedViewport() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    float[][] viewports = {{800f, 600f}, {1024f, 768f}, {1440f, 900f}, {1920f, 1080f}};

    for (float[] size : viewports) {
      MinimapProjection.Rectangle mapPanel =
          MinimapProjection.fit(extended, size[0], size[1]).panel();
      FlightConsoleLayout.Rectangle consolePanel =
          FlightConsoleLayout.fit(size[0], size[1]).panel();

      assertEquals(consolePanel.y() + consolePanel.height(), mapPanel.y(), 0.0001f,
          "the map starts on the console top edge without overlapping it at "
              + size[0] + "x" + size[1]);
      assertTrue(mapPanel.y() >= consolePanel.y() + consolePanel.height());
      assertTrue(mapPanel.x() >= 0f && mapPanel.x() + mapPanel.width() <= size[0]);
      assertTrue(mapPanel.y() + mapPanel.height() <= size[1]);
    }
  }

  @Test void visualScaleGrowsWithTheTallerMapAndIsCapped() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    MinimapProjection compact = MinimapProjection.fit(extended, 800f, 400f);
    MinimapProjection nominal = MinimapProjection.fit(extended, 800f, 600f);
    MinimapProjection fullHd = MinimapProjection.fit(extended, 1920f, 1080f);

    assertEquals(1f, compact.visualScale(), 0.0001f,
        "the previous 360-unit map keeps the original marker size");
    assertTrue(nominal.visualScale() > 1f, "the taller map enlarges its markers");
    assertTrue(nominal.visualScale() <= 1.6f);
    assertEquals(1.6f, fullHd.visualScale(), 0.0001f, "the scale stops at the cap");
  }

  @Test void rejectsNonFinitePositionsAndHeadings() {
    MinimapProjection projection = MinimapProjection.fit(WORLD, 1024f, 768f);
    assertThrows(IllegalArgumentException.class,
        () -> projection.project(Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> MinimapProjection.astraDirection(Float.POSITIVE_INFINITY));
  }

  private static void assertPoint(MinimapProjection.Point actual, float x, float y) {
    assertEquals(x, actual.x(), 0.0001f);
    assertEquals(y, actual.y(), 0.0001f);
  }
}

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

    assertEquals(WORLD.width() / WORLD.height(), map.width() / map.height(), 0.00001f);
    assertTrue(panel.x() >= 0f);
    assertTrue(panel.x() + panel.width() <= 1024f);
    assertTrue(panel.y() > 50f, "the bottom HUD reading stays below the panel");
    assertTrue(panel.y() + panel.height() <= 768f);
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

package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import org.junit.jupiter.api.Test;

/**
 * Pure geometry tests for the off-screen Vesper indicator (COS-39). The placement is derived from
 * the same camera frame as {@link CameraVisibility}, so these tests exercise the HUD point and
 * direction without a native window: cardinal and diagonal edges, rotated cameras, zoom and half-box
 * hiding, inactive/degenerate inputs, the minimap reservation and the virtual (letterboxed)
 * viewport.
 */
class OffscreenEnemyIndicatorTest {

  private static final float VIEW_WIDTH = 800f;
  private static final float VIEW_HEIGHT = 600f;
  private static final float CENTER_X = VIEW_WIDTH / 2f;
  private static final float CENTER_Y = VIEW_HEIGHT / 2f;
  private static final float LEFT = OffscreenEnemyIndicator.EDGE_MARGIN;
  private static final float RIGHT = VIEW_WIDTH - OffscreenEnemyIndicator.EDGE_MARGIN;
  private static final float TOP = VIEW_HEIGHT - OffscreenEnemyIndicator.EDGE_MARGIN;
  private static final float BOTTOM = MinimapProjection.BOTTOM_MARGIN;
  private static final float DIAGONAL = (float) Math.sqrt(0.5);
  private static final float EPS = 0.01f;

  private static OffscreenEnemyIndicator.Placement compute(float enemyX, float enemyY,
      float halfBox, float upX, float upY, float zoom) {
    return OffscreenEnemyIndicator.compute(true, enemyX, enemyY, halfBox,
        0f, 0f, upX, upY, VIEW_WIDTH, VIEW_HEIGHT, zoom);
  }

  @Test
  void pointsAtEveryCardinalAndDiagonalEdge() {
    assertPlacement(compute(1000f, 0f, 0f, 0f, 1f, 1f), RIGHT, CENTER_Y, 1f, 0f);
    assertPlacement(compute(-1000f, 0f, 0f, 0f, 1f, 1f), LEFT, CENTER_Y, -1f, 0f);
    assertPlacement(compute(0f, 1000f, 0f, 0f, 1f, 1f), CENTER_X, TOP, 0f, 1f);
    assertPlacement(compute(0f, -1000f, 0f, 0f, 1f, 1f), CENTER_X, BOTTOM, 0f, -1f);

    // Diagonals clip on the nearest edge; the direction still points straight at the enemy.
    assertPlacement(compute(1000f, 1000f, 0f, 0f, 1f, 1f),
        CENTER_X + 280f, TOP, DIAGONAL, DIAGONAL);
    assertPlacement(compute(-1000f, 1000f, 0f, 0f, 1f, 1f),
        CENTER_X - 280f, TOP, -DIAGONAL, DIAGONAL);
    assertPlacement(compute(1000f, -1000f, 0f, 0f, 1f, 1f),
        CENTER_X + 224f, BOTTOM, DIAGONAL, -DIAGONAL);
    assertPlacement(compute(-1000f, -1000f, 0f, 0f, 1f, 1f),
        CENTER_X - 224f, BOTTOM, -DIAGONAL, -DIAGONAL);
  }

  @Test
  void mapsWorldDirectionsThroughTheRotatedCameraFrame() {
    // 90-degree camera: world +Y maps to the HUD right direction, world +X maps to the top edge.
    assertPlacement(compute(0f, 1000f, 0f, 1f, 0f, 1f), LEFT, CENTER_Y, -1f, 0f);
    assertPlacement(compute(1000f, 0f, 0f, 1f, 0f, 1f), CENTER_X, TOP, 0f, 1f);

    // 180-degree camera flips the world +X into the HUD left edge.
    assertPlacement(compute(1000f, 0f, 0f, 0f, -1f, 1f), LEFT, CENTER_Y, -1f, 0f);

    // Wrapped 45-degree and 225-degree cameras keep the direction unit-length and on an edge.
    assertPlacement(compute(1000f, 0f, 0f, DIAGONAL, DIAGONAL, 1f),
        CENTER_X + 280f, TOP, DIAGONAL, DIAGONAL);
    assertPlacement(compute(1000f, 0f, 0f, -DIAGONAL, -DIAGONAL, 1f),
        CENTER_X - 224f, BOTTOM, -DIAGONAL, -DIAGONAL);
  }

  @Test
  void hidesWheneverTheEnemyIntersectsTheViewIncludingZoomAndHalfBox() {
    // Barely outside the upright view: the indicator is active on the right edge.
    assertPlacement(compute(450f, 0f, 0f, 0f, 1f, 1f), RIGHT, CENTER_Y, 1f, 0f);

    // Zoom widens the visible half extents, so the same enemy becomes visible and hidable.
    assertFalse(compute(450f, 0f, 0f, 0f, 1f, 1.25f).active());
    // A large half box adds the same drawing margin the visibility rule uses.
    assertFalse(compute(450f, 0f, 60f, 0f, 1f, 1f).active());
    assertTrue(compute(450f, 0f, 40f, 0f, 1f, 1f).active());
  }

  @Test
  void returnsNoneForInactiveDegenerateAndNonFiniteInputs() {
    assertFalse(OffscreenEnemyIndicator.compute(false, 1000f, 0f, 0f,
        0f, 0f, 0f, 1f, VIEW_WIDTH, VIEW_HEIGHT, 1f).active());

    // The enemy sits exactly on the camera position: a zero vector has no direction.
    assertFalse(compute(0f, 0f, 0f, 0f, 1f, 1f).active());

    assertFalse(compute(Float.NaN, 0f, 0f, 0f, 1f, 1f).active());
    assertFalse(compute(1000f, Float.POSITIVE_INFINITY, 0f, 0f, 1f, 1f).active());
    assertFalse(compute(1000f, 0f, 0f, 0f, 1f, 0f).active());
    assertFalse(compute(1000f, 0f, 0f, 0f, 1f, -1f).active());

    // A viewport too small for the interior rectangle (and the fixed bottom band) is unusable.
    assertFalse(OffscreenEnemyIndicator.compute(true, 1000f, 0f, 0f,
        0f, 0f, 0f, 1f, 2f * OffscreenEnemyIndicator.EDGE_MARGIN, VIEW_HEIGHT, 1f).active());
    assertFalse(OffscreenEnemyIndicator.compute(true, 1000f, 0f, 0f,
        0f, 0f, 0f, 1f, VIEW_WIDTH, MinimapProjection.BOTTOM_MARGIN
        + OffscreenEnemyIndicator.EDGE_MARGIN, 1f).active());
  }

  @Test
  void scalesTheInteriorRectangleWithTheVirtualViewport() {
    // 1600x1200 virtual viewport (letterbox scaled up): the tip tracks the wider interior frame.
    OffscreenEnemyIndicator.Placement placement = OffscreenEnemyIndicator.compute(true,
        2000f, 0f, 0f, 0f, 0f, 0f, 1f, 1600f, 1200f, 1f);
    assertPlacement(placement, 1600f - OffscreenEnemyIndicator.EDGE_MARGIN, 600f, 1f, 0f);
  }

  @Test
  void slidesOffTheMinimapPanelAlongTheSameEdgePreservingDirection() {
    OffscreenEnemyIndicator.Placement placement = compute(1000f, -1000f, 0f, 0f, 1f, 1f);
    MinimapProjection.Rectangle panel = MinimapProjection.fit(PhaseOneGameController.WORLD,
        VIEW_WIDTH, VIEW_HEIGHT).panel();

    OffscreenEnemyIndicator.Placement moved = OffscreenEnemyIndicator.avoidReservations(
        placement, VIEW_WIDTH, VIEW_HEIGHT, panel);

    // The panel's left free segment is nearer than its right one; the tip stays on the bottom edge.
    assertEquals(panel.x() - OffscreenEnemyIndicator.RESERVATION_GAP, moved.x(), EPS);
    assertEquals(BOTTOM, moved.y(), EPS);
    assertEquals(placement.directionX(), moved.directionX(), EPS);
    assertEquals(placement.directionY(), moved.directionY(), EPS);

    // A tip outside every reservation is returned untouched.
    OffscreenEnemyIndicator.Placement clear = compute(1000f, 0f, 0f, 0f, 1f, 1f);
    assertSame(clear, OffscreenEnemyIndicator.avoidReservations(
        clear, VIEW_WIDTH, VIEW_HEIGHT, panel));

    // Null placement and null reservations stay null-safe.
    assertNull(OffscreenEnemyIndicator.avoidReservations(null, VIEW_WIDTH, VIEW_HEIGHT, panel));
    assertSame(clear, OffscreenEnemyIndicator.avoidReservations(
        clear, VIEW_WIDTH, VIEW_HEIGHT, (MinimapProjection.Rectangle) null));
  }

  private static void assertPlacement(OffscreenEnemyIndicator.Placement placement,
      float x, float y, float directionX, float directionY) {
    assertTrue(placement.active(), "placement should be active");
    assertEquals(x, placement.x(), EPS);
    assertEquals(y, placement.y(), EPS);
    assertEquals(directionX, placement.directionX(), EPS);
    assertEquals(directionY, placement.directionY(), EPS);
    assertEquals(1f, (float) Math.hypot(placement.directionX(), placement.directionY()), EPS,
        "direction must be unit length");
  }
}

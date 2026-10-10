package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure geometry tests for the single visibility rule shared by PhaseOne's raider drawing and the
 * autonomous weapon's coincidence authorization. No native window is needed.
 */
class CameraVisibilityTest {

  private static final float VIEW_WIDTH = 1024f;
  private static final float VIEW_HEIGHT = 768f;

  private static boolean visible(float x, float y, float upX, float upY, float zoom,
      float halfBox) {
    return CameraVisibility.shipVisible(x, y, halfBox, 0f, 0f, upX, upY,
        VIEW_WIDTH, VIEW_HEIGHT, zoom);
  }

  @Test
  void cameraCenterIsVisible() {
    assertTrue(visible(0f, 0f, 0f, 1f, 1f, 0f));
  }

  @Test
  void boundariesAreInclusiveAndJustOutsideIsHidden() {
    assertTrue(visible(512f, 0f, 0f, 1f, 1f, 0f));
    assertFalse(visible(513f, 0f, 0f, 1f, 1f, 0f));
    assertTrue(visible(0f, 384f, 0f, 1f, 1f, 0f));
    assertFalse(visible(0f, 385f, 0f, 1f, 1f, 0f));
  }

  @Test
  void boxMarginExtendsTheVisibleArea() {
    assertFalse(visible(560f, 0f, 0f, 1f, 1f, 0f));
    assertTrue(visible(560f, 0f, 0f, 1f, 1f, 60f));
  }

  @Test
  void zoomScalesTheVisibleHalfExtents() {
    assertFalse(visible(560f, 0f, 0f, 1f, 1f, 0f));
    assertTrue(visible(560f, 0f, 0f, 1f, 1.15f, 0f));
  }

  @Test
  void cameraRotationChangesWhichWorldPointsAreVisible() {
    // The point lies outside the upright view vertically but inside the 90-degree rotated one.
    assertFalse(visible(0f, 450f, 0f, 1f, 1f, 0f));
    assertTrue(visible(0f, 450f, 1f, 0f, 1f, 0f));

    // A point below the upright view enters the 45-degree rotated view.
    float diagonal = (float) Math.sin(Math.toRadians(45));
    assertFalse(visible(0f, -450f, 0f, 1f, 1f, 0f));
    assertTrue(visible(0f, -450f, diagonal, diagonal, 1f, 0f));
  }

  @Test
  void coincidenceNeedsBothShipsInsideTheSameView() {
    boolean astraVisible = visible(0f, 0f, 0f, 1f, 1f, 0f);
    boolean raiderVisible = visible(2000f, 0f, 0f, 1f, 1f, 0f);
    assertTrue(astraVisible);
    assertFalse(raiderVisible);
    assertFalse(astraVisible && raiderVisible, "only one ship intersects the view");
  }
}

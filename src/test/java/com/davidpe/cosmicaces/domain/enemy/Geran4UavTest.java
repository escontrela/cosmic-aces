package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Geran4UavTest {

  private static final float EPSILON = 0.0001f;

  @Test
  void keepsAFixedHeadingWhileMovingStraight() {
    Geran4Uav north = new Geran4Uav(1, 90f, 100f, 100f, 0f, 1f);
    north.advance(1f);
    assertEquals(100f, north.x(), EPSILON);
    assertEquals(190f, north.y(), EPSILON);

    Geran4Uav east = new Geran4Uav(2, 90f, 100f, 100f, 1f, 0f);
    east.advance(1f);
    assertEquals(190f, east.x(), EPSILON);
    assertEquals(100f, east.y(), EPSILON);

    Geran4Uav south = new Geran4Uav(3, 90f, 100f, 100f, 0f, -1f);
    south.advance(1f);
    assertEquals(100f, south.x(), EPSILON);
    assertEquals(10f, south.y(), EPSILON);

    Geran4Uav west = new Geran4Uav(4, 90f, 100f, 100f, -1f, 0f);
    west.advance(1f);
    assertEquals(10f, west.x(), EPSILON);
    assertEquals(100f, west.y(), EPSILON);

    Geran4Uav diagonal = new Geran4Uav(5, 90f, 100f, 100f, 3f, 4f);
    diagonal.advance(1f);
    assertEquals(100f + 90f * 0.6f, diagonal.x(), EPSILON);
    assertEquals(100f + 90f * 0.8f, diagonal.y(), EPSILON);
  }

  @Test
  void normalizesTheHeadingOnceAndKeepsItForTheWholeFlight() {
    Geran4Uav uav = new Geran4Uav(1, 90f, 100f, 100f, 30f, 40f);
    assertEquals(0.6f, uav.directionX(), EPSILON);
    assertEquals(0.8f, uav.directionY(), EPSILON);
    uav.advance(2f);
    assertEquals(0.6f, uav.directionX(), EPSILON, "heading must not change while flying");
    assertEquals(0.8f, uav.directionY(), EPSILON, "heading must not change while flying");
  }

  @Test
  void zeroAndInvalidDeltasDoNotMoveTheUavNorAgeThePropeller() {
    Geran4Uav uav = new Geran4Uav(1, 90f, 100f, 100f, 1f, 0f);
    uav.advance(0f);
    uav.advance(-1f);
    uav.advance(Float.NaN);
    assertEquals(100f, uav.x(), EPSILON);
    assertEquals(0, uav.propellerFrame());
  }

  @Test
  void propellerFrameCyclesThroughTheThreeSheetFrames() {
    Geran4Uav uav = new Geran4Uav(1, 90f, 100f, 100f, 0f, 1f);
    assertEquals(0, uav.propellerFrame());
    uav.advance(Geran4Uav.PROPELLER_FRAME_SECONDS);
    assertEquals(1, uav.propellerFrame());
    uav.advance(Geran4Uav.PROPELLER_FRAME_SECONDS);
    assertEquals(2, uav.propellerFrame());
    uav.advance(Geran4Uav.PROPELLER_FRAME_SECONDS);
    assertEquals(0, uav.propellerFrame(), "the cycle wraps after the third frame");
    uav.advance(Geran4Uav.PROPELLER_FRAME_SECONDS);
    assertEquals(1, uav.propellerFrame(), "the cycle continues after the wrap");
  }

  @Test
  void headingRotationMatchesTheSheetBaseOrientationConvention() {
    assertRotationEquivalent(0f, new Geran4Uav(1, 90f, 0f, 0f, 0f, 1f).rotationDegrees());
    assertRotationEquivalent(-90f, new Geran4Uav(2, 90f, 0f, 0f, 1f, 0f).rotationDegrees());
    assertRotationEquivalent(180f, new Geran4Uav(3, 90f, 0f, 0f, 0f, -1f).rotationDegrees());
    assertRotationEquivalent(90f, new Geran4Uav(4, 90f, 0f, 0f, -1f, 0f).rotationDegrees());
    assertRotationEquivalent(-45f, new Geran4Uav(5, 90f, 0f, 0f, 1f, 1f).rotationDegrees());
  }

  private static void assertRotationEquivalent(float expectedDegrees, float actualDegrees) {
    float delta = ((expectedDegrees - actualDegrees) % 360f + 360f) % 360f;
    assertEquals(0f, Math.min(delta, 360f - delta), EPSILON,
        "rotation must match " + expectedDegrees + "° modulo 360°");
  }

  @Test
  void splittingTheMovementDeltaDoesNotChangeTheTrajectory() {
    Geran4Uav whole = new Geran4Uav(1, 90f, 100f, 100f, 3f, 4f);
    Geran4Uav split = new Geran4Uav(2, 90f, 100f, 100f, 3f, 4f);
    whole.advance(1.5f);
    for (int i = 0; i < 30; i++) {
      split.advance(0.05f);
    }
    assertEquals(whole.x(), split.x(), 0.0005f);
    assertEquals(whole.y(), split.y(), 0.0005f);
  }

  @Test
  void boxCenterDerivesFromTheStableBox() {
    Geran4Uav uav = new Geran4Uav(1, 90f, 100f, 120f, 0f, 1f);
    assertEquals(100f + Geran4Uav.BOX_WIDTH / 2f, uav.centerX(), EPSILON);
    assertEquals(120f + Geran4Uav.BOX_HEIGHT / 2f, uav.centerY(), EPSILON);
  }

  @Test
  void fighterScaleBoxStaysBelowAFighterBox() {
    assertEquals(72f, Geran4Uav.BOX_WIDTH, EPSILON);
    assertEquals(72f, Geran4Uav.BOX_HEIGHT, EPSILON);
    assertEquals(Geran4Uav.BOX_WIDTH, new Geran4Uav(1, 90f, 0f, 0f, 0f, 1f).drawWidth(), EPSILON);
    assertEquals(Geran4Uav.BOX_HEIGHT, new Geran4Uav(1, 90f, 0f, 0f, 0f, 1f).drawHeight(), EPSILON);
    org.junit.jupiter.api.Assertions.assertTrue(
        Geran4Uav.BOX_WIDTH <= RaiderEncounter.RAIDER_WIDTH
            && Geran4Uav.BOX_HEIGHT <= RaiderEncounter.RAIDER_HEIGHT,
        "the UAV must never exceed a fighter box (Vesper Raider is 80x80)");
  }

  @Test
  void rejectsInvalidHeadings() {
    assertThrows(IllegalArgumentException.class,
        () -> new Geran4Uav(1, 90f, 0f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> new Geran4Uav(1, 90f, 0f, 0f, Float.NaN, 1f));
    assertThrows(IllegalArgumentException.class,
        () -> new Geran4Uav(1, 90f, 0f, 0f, 1f, Float.POSITIVE_INFINITY));
  }

  @Test
  void drawingRequiresAttachedVisuals() {
    Geran4Uav uav = new Geran4Uav(1, 90f, 0f, 0f, 0f, 1f);
    assertThrows(IllegalStateException.class, () -> uav.draw(null));
  }
}

package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VesperRaiderTest {

  private static final float SPEED = 100f;
  private static final float EPSILON = 0.001f;

  @Test
  void descendsStraightDownWhenHeadingIsZero() {
    VesperRaider raider = raider(100f, 200f, 0f);
    raider.advance(1f);
    assertEquals(100f, raider.x(), EPSILON);
    assertEquals(100f, raider.y(), EPSILON);
  }

  @Test
  void positiveHeadingMovesRightAndDown() {
    VesperRaider raider = raider(0f, 100f, 20f);
    raider.advance(1f);
    assertEquals(34.202f, raider.x(), EPSILON);
    assertEquals(6.031f, raider.y(), EPSILON);
  }

  @Test
  void negativeHeadingMovesLeftAndDown() {
    VesperRaider raider = raider(0f, 100f, -20f);
    raider.advance(1f);
    assertEquals(-34.202f, raider.x(), EPSILON);
    assertEquals(6.031f, raider.y(), EPSILON);
  }

  @Test
  void nonPositiveOrNonFiniteDeltaDoesNotMoveTheRaider() {
    VesperRaider raider = raider(50f, 50f, 10f);
    raider.advance(0f);
    raider.advance(-1f);
    raider.advance(Float.NaN);
    raider.advance(Float.POSITIVE_INFINITY);
    assertEquals(50f, raider.x(), EPSILON);
    assertEquals(50f, raider.y(), EPSILON);
  }

  @Test
  void bankReflectsTheHeadingSign() {
    assertEquals(0, raider(0f, 0f, 0f).bank());
    assertEquals(1, raider(0f, 0f, 5f).bank());
    assertEquals(-1, raider(0f, 0f, -5f).bank());
  }

  @Test
  void headingCanBeReplaced() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.setHeadingDegrees(-12f);
    assertEquals(-12f, raider.headingDegrees(), EPSILON);
  }

  @Test
  void keepsBoxAndLaneMetadata() {
    VesperRaider raider = new VesperRaider(100f, 30f, 40f, 200f, 210f, 220f, 0f);
    assertEquals(30f, raider.width(), EPSILON);
    assertEquals(40f, raider.height(), EPSILON);
    assertEquals(200f, raider.spawnX(), EPSILON);
    assertEquals(100f, raider.speed(), EPSILON);
  }

  @Test
  void rejectsNonPositiveSpeedOrBox() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(0f, 10f, 10f, 0f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(Float.NaN, 10f, 10f, 0f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 0f, 10f, 0f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, -1f, 0f, 0f, 0f, 0f));
  }

  @Test
  void rejectsNonFinitePosition() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, Float.NaN, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, Float.NaN, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, 0f, Float.NaN, 0f));
  }

  private static VesperRaider raider(float x, float y, float headingDegrees) {
    return new VesperRaider(SPEED, 10f, 10f, x, x, y, headingDegrees);
  }
}
package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

class VesperRaiderTest {

  private static final float SPEED = 100f;
  private static final float EPSILON = 0.001f;
  private static final WorldBounds WORLD = new WorldBounds(4000f, 12000f);

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
  void headingCanBeReplacedAndClearsTheTurn() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(30f, 10f);
    assertEquals(1, raider.turnDirection());

    raider.setHeadingDegrees(-12f);
    assertEquals(-12f, raider.headingDegrees(), EPSILON);
    assertEquals(0, raider.turnDirection());
  }

  @Test
  void steeringIsBoundedAndRecordsTheTurnDirection() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(90f, 20f);
    assertEquals(20f, raider.headingDegrees(), EPSILON);
    assertEquals(1, raider.turnDirection());

    raider.steerTowards(-90f, 20f);
    assertEquals(0f, raider.headingDegrees(), EPSILON);
    assertEquals(-1, raider.turnDirection());
  }

  @Test
  void steeringNeverOvershootsATargetWithinTheLimit() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(10f, 90f);
    assertEquals(10f, raider.headingDegrees(), EPSILON);
    assertEquals(1, raider.turnDirection());
  }

  @Test
  void yawPoseAccompaniesTurnsAndBankPoseAccompaniesDrift() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(30f, 10f);
    assertEquals(VesperRaiderSheet.Pose.YAW_RIGHT, raider.pose());

    raider.setHeadingDegrees(0f);
    raider.setDriftDirection(1);
    assertEquals(VesperRaiderSheet.Pose.LEFT, raider.pose());

    raider.setDriftDirection(-1);
    assertEquals(VesperRaiderSheet.Pose.RIGHT, raider.pose());

    raider.setDriftDirection(0);
    assertEquals(VesperRaiderSheet.Pose.NEUTRAL, raider.pose());
  }

  @Test
  void clampToWorldKeepsTheBoxInside() {
    VesperRaider low = raider(-50f, -50f, 0f);
    low.clampToWorld(WORLD);
    assertEquals(0f, low.x(), EPSILON);
    assertEquals(0f, low.y(), EPSILON);

    VesperRaider high = raider(WORLD.width() + 10f, WORLD.height() + 10f, 0f);
    high.clampToWorld(WORLD);
    assertEquals(WORLD.maxX(high.drawWidth()), high.x(), EPSILON);
    assertEquals(WORLD.maxY(high.drawHeight()), high.y(), EPSILON);
  }

  @Test
  void rejectsNonPositiveSpeedOrBox() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(0f, 10f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(Float.NaN, 10f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 0f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, -1f, 0f, 0f, 0f));
  }

  @Test
  void rejectsNonFinitePosition() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, Float.NaN, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, 0f, Float.NaN));
  }

  private static VesperRaider raider(float x, float y, float headingDegrees) {
    return new VesperRaider(SPEED, 10f, 10f, x, y, headingDegrees);
  }
}

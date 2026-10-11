package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GhostwingDroneTest {

  private static final float EPSILON = 0.0001f;

  @Test
  void keepsAFixedCardinalDirectionWhileMoving() {
    GhostwingDrone north = new GhostwingDrone(1, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.NORTH);
    north.advance(1f);
    assertEquals(100f, north.x(), EPSILON);
    assertEquals(190f, north.y(), EPSILON);

    GhostwingDrone east = new GhostwingDrone(2, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.EAST);
    east.advance(1f);
    assertEquals(190f, east.x(), EPSILON);
    assertEquals(100f, east.y(), EPSILON);

    GhostwingDrone south = new GhostwingDrone(3, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.SOUTH);
    south.advance(1f);
    assertEquals(100f, south.x(), EPSILON);
    assertEquals(10f, south.y(), EPSILON);

    GhostwingDrone west = new GhostwingDrone(4, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.WEST);
    west.advance(1f);
    assertEquals(10f, west.x(), EPSILON);
    assertEquals(100f, west.y(), EPSILON);
  }

  @Test
  void zeroAndInvalidDeltasDoNotMoveTheDroneNorAgeThePropeller() {
    GhostwingDrone drone = new GhostwingDrone(1, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.EAST);
    drone.advance(0f);
    drone.advance(-1f);
    drone.advance(Float.NaN);
    assertEquals(100f, drone.x(), EPSILON);
    assertEquals(0, drone.propellerFrame());
  }

  @Test
  void propellerFrameCyclesThroughTheThreeSheetFrames() {
    GhostwingDrone drone = new GhostwingDrone(1, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.NORTH);
    assertEquals(0, drone.propellerFrame());
    drone.advance(GhostwingDrone.PROPELLER_FRAME_SECONDS);
    assertEquals(1, drone.propellerFrame());
    drone.advance(GhostwingDrone.PROPELLER_FRAME_SECONDS);
    assertEquals(2, drone.propellerFrame());
    drone.advance(GhostwingDrone.PROPELLER_FRAME_SECONDS);
    assertEquals(0, drone.propellerFrame(), "the cycle wraps after the third frame");
    drone.advance(GhostwingDrone.PROPELLER_FRAME_SECONDS);
    assertEquals(1, drone.propellerFrame(), "the cycle continues after the wrap");
  }

  @Test
  void directionRotationMatchesTheSheetBaseOrientation() {
    assertEquals(0f, GhostwingDrone.Direction.NORTH.rotationDegrees());
    assertEquals(-90f, GhostwingDrone.Direction.EAST.rotationDegrees());
    assertEquals(180f, GhostwingDrone.Direction.SOUTH.rotationDegrees());
    assertEquals(90f, GhostwingDrone.Direction.WEST.rotationDegrees());
  }

  @Test
  void splittingTheMovementDeltaDoesNotChangeTheTrajectory() {
    GhostwingDrone whole = new GhostwingDrone(1, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.EAST);
    GhostwingDrone split = new GhostwingDrone(2, 90f, 100f, 100f, 72f, 72f,
        GhostwingDrone.Direction.EAST);
    whole.advance(1.5f);
    for (int i = 0; i < 30; i++) {
      split.advance(0.05f);
    }
    assertEquals(whole.x(), split.x(), 0.0005f);
    assertEquals(whole.y(), split.y(), 0.0005f);
  }

  @Test
  void boxCenterDerivesFromTheStableBox() {
    GhostwingDrone drone = new GhostwingDrone(1, 90f, 100f, 120f, 72f, 72f,
        GhostwingDrone.Direction.NORTH);
    assertEquals(136f, drone.centerX(), EPSILON);
    assertEquals(156f, drone.centerY(), EPSILON);
  }

  @Test
  void rejectsMissingDirection() {
    assertThrows(IllegalArgumentException.class, () -> new GhostwingDrone(
        1, 90f, 100f, 100f, 72f, 72f, null));
  }
}
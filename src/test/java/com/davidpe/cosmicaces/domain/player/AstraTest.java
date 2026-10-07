package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import org.junit.jupiter.api.Test;

class AstraTest {

  private static final PlayArea AREA = new PlayArea(800f, 600f);
  private static final float EPSILON = 0.001f;

  @Test
  void movesInEveryDirectionAndOppositeKeysCancel() {
    Astra astra = new Astra(100f, 400f, 300f);
    astra.move(MovementIntent.fromDirections(false, true, false, false), 1f, AREA);
    astra.move(MovementIntent.fromDirections(true, false, false, false), 1f, AREA);
    astra.move(MovementIntent.fromDirections(false, false, true, false), 1f, AREA);
    astra.move(MovementIntent.fromDirections(false, false, false, true), 1f, AREA);
    astra.move(MovementIntent.fromDirections(true, true, true, true), 1f, AREA);
    assertEquals(400f, astra.x(), EPSILON);
    assertEquals(300f, astra.y(), EPSILON);
  }

  @Test
  void diagonalMovementHasTheSameSpeedAsStraightMovement() {
    Astra astra = new Astra(100f);
    astra.move(MovementIntent.fromDirections(false, true, true, false), 1f, AREA);
    assertEquals(70.71068f, astra.x(), EPSILON);
    assertEquals(70.71068f, astra.y(), EPSILON);
  }

  @Test
  void movementAndPlacementStayInsideThePlayArea() {
    Astra astra = new Astra(100f);
    astra.placeAt(-100f, 900f, AREA);
    assertEquals(0f, astra.x(), EPSILON);
    assertEquals(AREA.height(), astra.y(), EPSILON);
    astra.move(MovementIntent.fromDirections(false, true, true, false), 100f, AREA);
    assertEquals(AREA.width(), astra.x(), EPSILON);
    assertEquals(AREA.height(), astra.y(), EPSILON);
  }

  @Test
  void invalidDeltaAndEmptyIntentDoNotMove() {
    Astra astra = new Astra(100f, 50f, 50f);
    MovementIntent right = MovementIntent.fromDirections(false, true, false, false);
    astra.move(right, 0f, AREA);
    astra.move(right, -1f, AREA);
    astra.move(right, Float.NaN, AREA);
    astra.move(right, Float.POSITIVE_INFINITY, AREA);
    astra.move(MovementIntent.none(), 1f, AREA);
    assertEquals(50f, astra.x(), EPSILON);
    assertEquals(50f, astra.y(), EPSILON);
  }

  @Test
  void drawingBoxCoversEveryPoseOfBothSheets() {
    Astra astra = new Astra(Astra.DEFAULT_SPEED);
    assertEquals(579f * 0.11f, astra.drawWidth(), EPSILON);
    assertEquals(779f * 0.11f, astra.drawHeight(), EPSILON);
  }

  @Test
  void selectsBankAndAccelerationIndependentlyOfMovement() {
    Astra astra = new Astra(100f);
    astra.move(MovementIntent.fromDirections(true, false, true, false), 0f, AREA, true);
    assertEquals(HeroShipSheet.Pose.LEFT, astra.pose());
    assertEquals(true, astra.accelerating());
    astra.move(MovementIntent.fromDirections(false, true, false, false), 0f, AREA, false);
    assertEquals(HeroShipSheet.Pose.RIGHT, astra.pose());
    assertEquals(false, astra.accelerating());
  }

  @Test
  void rejectsInvalidSpeed() {
    assertThrows(IllegalArgumentException.class, () -> new Astra(0f));
    assertThrows(IllegalArgumentException.class, () -> new Astra(Float.NaN));
  }
}

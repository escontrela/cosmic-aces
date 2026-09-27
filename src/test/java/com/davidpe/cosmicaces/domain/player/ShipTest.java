package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import org.junit.jupiter.api.Test;

class ShipTest {

  private static final float SPEED = 100f;
  private static final PlayArea AREA = new PlayArea(800f, 600f);
  private static final float EPSILON = 0.001f;

  @Test
  void movesRightWithoutVerticalChange() {
    Ship ship = shipAt(0f, 0f);
    ship.move(new MovementIntent(false, true, false, false), 1f, AREA);
    assertEquals(SPEED, ship.x(), EPSILON);
    assertEquals(0f, ship.y(), EPSILON);
  }

  @Test
  void movesLeft() {
    Ship ship = shipAt(400f, 300f);
    ship.move(new MovementIntent(true, false, false, false), 1f, AREA);
    assertEquals(300f, ship.x(), EPSILON);
    assertEquals(300f, ship.y(), EPSILON);
  }

  @Test
  void movesUp() {
    Ship ship = shipAt(0f, 0f);
    ship.move(new MovementIntent(false, false, true, false), 1f, AREA);
    assertEquals(0f, ship.x(), EPSILON);
    assertEquals(SPEED, ship.y(), EPSILON);
  }

  @Test
  void movesDown() {
    Ship ship = shipAt(0f, 400f);
    ship.move(new MovementIntent(false, false, false, true), 1f, AREA);
    assertEquals(0f, ship.x(), EPSILON);
    assertEquals(300f, ship.y(), EPSILON);
  }

  @Test
  void oppositeKeysCancelOut() {
    Ship ship = shipAt(100f, 100f);
    ship.move(new MovementIntent(true, true, true, true), 1f, AREA);
    assertEquals(100f, ship.x(), EPSILON);
    assertEquals(100f, ship.y(), EPSILON);
  }

  @Test
  void diagonalMovementIsNormalized() {
    Ship ship = shipAt(0f, 0f);
    ship.move(new MovementIntent(false, true, true, false), 1f, AREA);
    float expected = SPEED * 0.70710678f;
    assertEquals(expected, ship.x(), EPSILON);
    assertEquals(expected, ship.y(), EPSILON);
  }

  @Test
  void emptyIntentDoesNotMoveTheShip() {
    Ship ship = shipAt(123f, 456f);
    ship.move(MovementIntent.none(), 1f, AREA);
    assertEquals(123f, ship.x(), EPSILON);
    assertEquals(456f, ship.y(), EPSILON);
  }

  @Test
  void positionIsClampedToPlayAreaBounds() {
    Ship ship = shipAt(0f, 0f);
    ship.move(new MovementIntent(true, false, false, false), 100f, AREA);
    assertEquals(0f, ship.x(), EPSILON);
    assertEquals(0f, ship.y(), EPSILON);

    ship.move(new MovementIntent(false, true, true, false), 100f, AREA);
    assertEquals(AREA.width(), ship.x(), EPSILON);
    assertEquals(AREA.height(), ship.y(), EPSILON);
  }

  @Test
  void nonPositiveOrNonFiniteDeltaDoesNotMoveTheShip() {
    Ship ship = shipAt(50f, 50f);
    ship.move(new MovementIntent(false, true, false, false), 0f, AREA);
    ship.move(new MovementIntent(false, true, false, false), -1f, AREA);
    ship.move(new MovementIntent(false, true, false, false), Float.NaN, AREA);
    assertEquals(50f, ship.x(), EPSILON);
    assertEquals(50f, ship.y(), EPSILON);
  }

  @Test
  void placeAtClampsToPlayArea() {
    Ship ship = shipAt(0f, 0f);
    ship.placeAt(-100f, 900f, AREA);
    assertEquals(0f, ship.x(), EPSILON);
    assertEquals(AREA.height(), ship.y(), EPSILON);
  }

  @Test
  void rejectsNonPositiveSpeed() {
    assertThrows(IllegalArgumentException.class, () -> new Ship(0f));
    assertThrows(IllegalArgumentException.class, () -> new Ship(Float.NaN));
  }

  private static Ship shipAt(float x, float y) {
    return new Ship(SPEED, x, y);
  }
}
package com.davidpe.cosmicaces.domain.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MovementIntentTest {

  @Test
  void oppositeKeysCancelAndDiagonalKeepsBothAxes() {
    assertEquals(MovementIntent.none(), MovementIntent.fromDirections(true, true, true, true));
    assertEquals(new MovementIntent(1f, 1f),
        MovementIntent.fromDirections(false, true, true, false));
  }

  @Test
  void downwardHeadingWorksForEnemyMovement() {
    MovementIntent straight = MovementIntent.fromDownwardHeading(0f);
    assertEquals(0f, straight.horizontal(), 0.0001f);
    assertEquals(-1f, straight.vertical(), 0.0001f);
    MovementIntent right = MovementIntent.fromDownwardHeading(20f);
    assertEquals(0.34202f, right.horizontal(), 0.0001f);
    assertEquals(-0.93969f, right.vertical(), 0.0001f);
  }

  @Test
  void rejectsNonFiniteDirections() {
    assertThrows(IllegalArgumentException.class, () -> new MovementIntent(Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> MovementIntent.fromDownwardHeading(Float.POSITIVE_INFINITY));
  }
}
